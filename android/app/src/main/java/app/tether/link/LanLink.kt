package app.tether.link

import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** A paired-client TCP listener on OS-reported local networks. Offers go in sealed STATUS only. */
internal class LanLink(private val context: Context, private val onTransport: (Transport) -> Unit) {
    private data class Scope(val ip: Inet4Address, val prefix: Int) {
        fun accepts(peer: Inet4Address) = sameLan(ip, peer, prefix)
    }

    private val listeners = mutableMapOf<Scope, ServerSocket>()
    private val clients = mutableMapOf<Socket, Scope>()
    private val slots = Semaphore(8)
    private var stopped = false

    @Synchronized fun refresh(enabled: Boolean): Boolean {
        if (stopped) return false
        val before = listeners.keys.toSet()
        val wanted = if (enabled) localScopes() else emptySet()
        for (scope in listeners.keys.toList()) {
            val server = listeners.getValue(scope)
            if (scope !in wanted || server.isClosed) {
                listeners.remove(scope)
                runCatching { server.close() }
                for (client in clients.filterValues { it == scope }.keys) runCatching { client.close() }
            }
        }
        for (scope in wanted) {
            if (scope in listeners) continue
            val server = ServerSocket()
            try {
                server.bind(InetSocketAddress(scope.ip, PORT), 8)
                listeners[scope] = server
                Thread({ accept(scope, server) }, "relink-lan-listen").apply { isDaemon = true }.start()
            } catch (_: IOException) {
                runCatching { server.close() }
            }
        }
        return before != listeners.keys
    }

    @Synchronized fun offer(): JSONArray = JSONArray(listeners.filterValues { !it.isClosed }.keys.sortedBy { it.ip.hostAddress }.map {
        JSONObject().put("ip", it.ip.hostAddress).put("port", PORT)
    })

    @Synchronized fun stop() {
        refresh(false)
        stopped = true
    }

    private fun accept(scope: Scope, server: ServerSocket) {
        try {
            while (!server.isClosed) {
                val socket = server.accept()
                val peer = socket.inetAddress as? Inet4Address
                val admitted = synchronized(this) {
                    peer != null && listeners[scope] === server && scope.accepts(peer) && slots.tryAcquire()
                        && run { clients[socket] = scope; true }
                }
                if (!admitted) { runCatching { socket.close() }; continue }
                val transport = LanTransport(socket) {
                    synchronized(this) { clients.remove(socket) }
                    slots.release()
                }
                try {
                    socket.tcpNoDelay = true
                    onTransport(transport)
                } catch (_: Exception) {
                    transport.close()
                }
            }
        } catch (_: IOException) {
            runCatching { server.close() }
        }
    }

    private fun localScopes(): Set<Scope> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        // Android 13's protected tether state broadcast identifies interfaces without assuming vendor names.
        val tethered = runCatching {
            context.registerReceiver(null, IntentFilter("android.net.conn.TETHER_STATE_CHANGED"))
                ?.getStringArrayListExtra("tetherArray").orEmpty()
        }.getOrDefault(emptyList())
        return buildSet {
            for (name in tethered) runCatching {
                val net = NetworkInterface.getByName(name)
                if (net != null && net.isUp && !net.isLoopback) {
                    for (link in net.interfaceAddresses) scope(link.address as? Inet4Address, link.networkPrefixLength.toInt())?.let(::add)
                }
            }
            @Suppress("DEPRECATION")
            for (network in manager.allNetworks) {
                val caps = manager.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED) ||
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
                for (link in manager.getLinkProperties(network)?.linkAddresses.orEmpty()) {
                    scope(link.address as? Inet4Address, link.prefixLength)?.let(::add)
                }
            }
        }.take(4).toSet()
    }

    private fun scope(ip: Inet4Address?, prefix: Int) =
        ip?.takeIf { it.isSiteLocalAddress && prefix in 8..30 }?.let { Scope(it, prefix) }

    companion object {
        private const val PORT = 24783

        internal fun sameLan(local: Inet4Address, peer: Inet4Address, prefix: Int): Boolean {
            if (!local.isSiteLocalAddress || !peer.isSiteLocalAddress || local == peer || prefix !in 8..30) return false
            val mask = -1 shl (32 - prefix)
            fun number(ip: Inet4Address) = ip.address.fold(0) { bits, byte -> (bits shl 8) or (byte.toInt() and 255) }
            val host = number(peer) and mask.inv()
            return host != 0 && host != mask.inv() && (number(local) and mask) == (number(peer) and mask)
        }
    }
}

internal class LanTransport(private val socket: Socket, private val released: () -> Unit) : Transport {
    override val label = "lan"
    override val address = "local network"
    override val canPair = false
    private val closed = AtomicBoolean(false)

    override fun start(onBytes: (ByteArray, Int) -> Unit, onClosed: () -> Unit) {
        Thread({
            val buffer = ByteArray(4096)
            try {
                val input = socket.getInputStream()
                while (!closed.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) onBytes(buffer, count)
                }
            } catch (_: IOException) {
            } finally {
                close()
                onClosed()
            }
        }, "relink-lan-rx").apply { isDaemon = true }.start()
    }

    override fun write(bytes: ByteArray) = socket.getOutputStream().write(bytes)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        released()
    }
}
