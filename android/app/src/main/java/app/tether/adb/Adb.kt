package app.tether.adb

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import app.tether.R
import app.tether.TetherApp
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ADB client to the phone's own adbd over Wireless debugging (TLS, loopback/Wi-Fi), via libadb-android.
 * Our RSA key + self-signed certificate are what adbd remembers after the one-time pairing.
 */
class Adb private constructor(private val ctx: Context) : AbsAdbConnectionManager() {
    private val keyFile = File(ctx.filesDir, "adb_key.pk8")
    private val certFile = File(ctx.filesDir, "adb_cert.der")
    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(10, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
        if (!keyFile.exists() || !certFile.exists()) generate()
        key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
    }

    /** Secondary ephemeral connection manager using the same identity to verify an alternate endpoint without dropping an active ADB stream. */
    private inner class ProbeManager : AbsAdbConnectionManager() {
        init {
            setApi(Build.VERSION.SDK_INT)
            setTimeout(5, TimeUnit.SECONDS)
            setThrowOnUnauthorised(true)
        }
        override fun getPrivateKey() = key
        override fun getCertificate() = cert
        override fun getDeviceName() = "ReLink"
    }

    private fun probeStream(mgr: AbsAdbConnectionManager) = verifyAdbShell { service ->
        mgr.openStream(service).use { it.openInputStream().read() }
    }

    private fun generate() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=ReLink")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 86_400_000),
            Date(now + 20L * 365 * 86_400_000), name, kp.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val c = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(builder.build(signer))
        keyFile.writeBytes(kp.private.encoded)
        certFile.writeBytes(c.encoded)
    }

    override fun getPrivateKey() = key
    override fun getCertificate() = cert
    override fun getDeviceName() = "ReLink"

    /** Pairs with the 6-digit code shown in Settings › Wireless debugging › Pair device with pairing code. */
    fun pairWithCode(code: String): Boolean {
        val (host, port) = findPairingService()
            ?: throw IllegalStateException("pairing service not found; keep the pairing dialog open")
        if (!pairAt(host, port, code)) throw IllegalStateException("wrong or expired pairing code")
        return true
    }

    /** The pairing service Settings announces while "Pair device with pairing code" is open (mDNS, up to 15 s). */
    internal fun findPairingService(): Pair<InetAddress, Int>? = discover(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 15)

    /** False when adbd refused the code (wrong, or the dialog was closed and it expired). */
    internal fun pairAt(address: InetAddress, port: Int, code: String): Boolean {
        if (port !in 1..65535 || !AdbAddressPolicy.isAllowedLocalAddress(address)) return false
        val numericHost = address.hostAddress ?: return false
        return pair(numericHost, port, code.trim())
    }

    @Volatile private var activeRoute: VerifiedAdbRoute? = null
    val transport: String? get() = activeRoute?.label

    private fun publishStatus(status: AdbConnectionStatus) = synchronized(Companion) {
        if (instance === this) connectionStatus = status
    }

    private fun probeMgr(mgr: AbsAdbConnectionManager, wireless: Boolean, port: Int): AdbFailure {
        val result = try {
            val endpoint = if (wireless) {
                discover(AdbMdns.SERVICE_TYPE_TLS_CONNECT, 10) ?: return AdbFailure.DISCOVERY_TIMEOUT
            } else null
            val numericHost = if (endpoint != null) {
                if (!AdbAddressPolicy.isAllowedLocalAddress(endpoint.first)) return AdbFailure.NON_LOOPBACK_REJECTED
                endpoint.first.hostAddress ?: return AdbFailure.NON_LOOPBACK_REJECTED
            } else "127.0.0.1"
            val connectedOk = mgr.connect(numericHost, endpoint?.second ?: port)
            if (connectedOk && (endpoint == null || AdbAddressPolicy.isAllowedLocalAddress(endpoint.first))) {
                probeStream(mgr)
            } else {
                AdbFailure.NOT_CONNECTED
            }
        } catch (failure: Exception) {
            classifyAdbFailure(failure, discovery = wireless)
        }
        if (result != AdbFailure.CONNECTED) runCatching { mgr.disconnect() }
        return result
    }

    private fun probeAlternate(wireless: Boolean, port: Int): AdbFailure {
        val manager = ProbeManager()
        // LibADB close() also destroys the shared private key. Stop only this connection.
        return try { probeMgr(manager, wireless, port) } finally { runCatching { manager.disconnect() } }
    }

    private fun canReuseTransport(settings: AdbConnectionStatus) =
        isConnected && activeRoute?.isAllowed(settings) == true

    @Synchronized fun ensureConnected(): Boolean {
        val meta = snapshotStatus(ctx)
        if (isConnected) {
            if (canReuseTransport(meta) && probeStream(this) == AdbFailure.CONNECTED) {
                publishStatus(meta.copy(
                    tls = if (transport == "tls") AdbFailure.CONNECTED else meta.tls,
                    legacy = if (transport == "legacy") AdbFailure.CONNECTED else meta.legacy,
                    checkedAtMs = System.currentTimeMillis(),
                ))
                return true
            }
            runCatching { disconnect() }
        }
        activeRoute = null
        val legacy = meta.tcpConfigured
        val port = meta.tcpPort
        publishStatus(meta.copy(
            tls = when {
                !ConnectOrder.tlsSupported(Build.VERSION.SDK_INT) -> AdbFailure.NOT_ATTEMPTED
                !meta.wirelessEnabled -> AdbFailure.DEBUGGING_OFF
                else -> AdbFailure.CONNECTING
            },
            legacy = if (legacy) AdbFailure.CONNECTING else AdbFailure.NOT_ATTEMPTED,
        ))
        val outcome = probeAdb(object : AdbDetailedEndpoints {
            override fun probeTls() = probeMgr(this@Adb, wireless = true, port = port)
            override fun probeLegacy(port: Int) = probeMgr(this@Adb, wireless = false, port = port)
        }, ConnectOrder.tlsSupported(Build.VERSION.SDK_INT), meta.wirelessEnabled, legacy, port, ProbeMode.ACTIVE_FIRST)
        activeRoute = when (outcome.activeTransport) {
            "tls" -> VerifiedAdbRoute()
            "legacy" -> VerifiedAdbRoute(port)
            else -> null
        }
        publishStatus(meta.copy(
            tls = outcome.tls,
            legacy = outcome.tcp,
            checkedAtMs = System.currentTimeMillis(),
        ))
        return outcome.activeTransport != null
    }

    /**
     * Performs a live ADB probe according to [mode], testing Wireless TLS and/or loopback TCP
     * independently without leaving `TCP` at `NOT_ATTEMPTED` when configured or explicitly checked.
     */
    @Synchronized internal fun checkLiveConnection(mode: ProbeMode = ProbeMode.VERIFY_ALL): AdbConnectionStatus {
        val meta = snapshotStatus(ctx)
        val legacyEnabled = meta.tcpConfigured || mode == ProbeMode.VERIFY_TCP_ONLY
        val port = meta.tcpPort
        if (!canReuseTransport(meta) || probeStream(this) != AdbFailure.CONNECTED) {
            if (isConnected) runCatching { disconnect() }
            activeRoute = null
        }
        val endpoints = object : AdbDetailedEndpoints {
            override fun probeTls(): AdbFailure {
                if (transport == "tls") return AdbFailure.CONNECTED
                return if (transport == null) {
                    val r = probeMgr(this@Adb, wireless = true, port = port)
                    if (r == AdbFailure.CONNECTED) activeRoute = VerifiedAdbRoute()
                    r
                } else {
                    probeAlternate(wireless = true, port = port)
                }
            }
            override fun probeLegacy(port: Int): AdbFailure {
                if (transport == "legacy") return AdbFailure.CONNECTED
                return if (transport == null && meta.tcpConfigured) {
                    val r = probeMgr(this@Adb, wireless = false, port = port)
                    if (r == AdbFailure.CONNECTED) activeRoute = VerifiedAdbRoute(port)
                    r
                } else {
                    probeAlternate(wireless = false, port = port)
                }
            }
        }
        val outcome = probeAdb(
            endpoints = endpoints,
            tlsSupported = ConnectOrder.tlsSupported(Build.VERSION.SDK_INT),
            wirelessSettingOn = meta.wirelessEnabled,
            legacyEnabled = legacyEnabled,
            legacyPort = port,
            mode = mode,
            previousTls = if (transport == "tls") AdbFailure.CONNECTED else meta.tls.withoutStaleConnection(),
            previousLegacy = if (transport == "legacy") AdbFailure.CONNECTED else meta.legacy.withoutStaleConnection(),
        )
        val updated = meta.copy(
            tls = outcome.tls,
            legacy = outcome.tcp,
            checkedAtMs = System.currentTimeMillis(),
        )
        publishStatus(updated)
        Log.i(TAG, "ADB probe ${mode.name}: ${updated.summary()}")
        return updated
    }

    /** Runs a shell command and returns its output (stdout + stderr). */
    @Synchronized fun shell(cmd: String): String {
        if (!ensureConnected()) throw IllegalStateException("adb not connected")
        return runShell(cmd, null)
    }

    @Synchronized internal fun verifiedRoute(): VerifiedAdbRoute? = if (ensureConnected()) activeRoute else null

    @Synchronized internal fun shellOnRoute(route: VerifiedAdbRoute, cmd: String, stdinLine: String): String {
        check(ADB_HELPER_BOOTSTRAP_ALLOWED)
        // Do not reconnect or switch transports when sending the bootstrap secret.
        if (instance !== this || !isConnected || !route.acceptsInput(activeRoute, snapshotStatus(ctx))) {
            throw IllegalStateException("adb not connected")
        }
        return runShell(cmd, stdinLine)
    }

    private fun runShell(cmd: String, stdinLine: String?): String {
        // "shell,raw:" runs without a PTY, so a backgrounded helper is not hung up when the stream closes.
        openStream("shell,raw:$cmd").use { s ->
            if (stdinLine != null) {
                val data = (stdinLine + "\n").toByteArray(Charsets.UTF_8)
                s.openOutputStream().apply { write(data); flush() }
            }
            val input = s.openInputStream()
            val out = StringBuilder()
            val buf = ByteArray(4096)
            while (true) {
                val n = try { input.read(buf) } catch (e: java.io.IOException) {
                    if (e.message != "Stream closed") throw e
                    -1
                }
                if (n < 0) break
                out.append(String(buf, 0, n))
            }
            return out.toString()
        }
    }

    private fun discover(type: String, timeoutSec: Long): Pair<InetAddress, Int>? {
        var found: Pair<InetAddress, Int>? = null
        var rejectedNonLocal = false
        val latch = CountDownLatch(1)
        val mdns = AdbMdns(ctx, type) { addr: InetAddress?, port: Int ->
            if (addr != null && port in 1..65535) {
                if (AdbAddressPolicy.isAllowedLocalAddress(addr)) {
                    found = addr to port
                    latch.countDown()
                } else {
                    rejectedNonLocal = true
                    Log.w(TAG, "ignored non-local ADB mDNS address")
                }
            }
        }
        mdns.start()
        try {
            latch.await(timeoutSec, TimeUnit.SECONDS)
            if (found == null && rejectedNonLocal) throw SecurityException("non-local ADB mDNS host rejected")
            return found
        } finally {
            mdns.stop()
        }
    }

    companion object {
        const val TAG = "Tether"
        @Volatile internal var connectionStatus = AdbConnectionStatus()
            private set
        internal fun snapshotStatus(ctx: Context): AdbConnectionStatus {
            val prefs = (ctx.applicationContext as? TetherApp)?.prefs
            val supported = ConnectOrder.tlsSupported(Build.VERSION.SDK_INT)
            val enabled = supported && runCatching {
                Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", -1)
            }.getOrDefault(-1) != 0
            return synchronized(this) {
                connectionStatus.withSettings(
                    paired = prefs?.getBoolean("adb_paired", false) == true,
                    tcpConfigured = prefs?.getBoolean(PREF_LEGACY, false) == true,
                    tcpPort = prefs?.getInt(PREF_LEGACY_PORT, ConnectOrder.DEFAULT_LEGACY_PORT) ?: ConnectOrder.DEFAULT_LEGACY_PORT,
                    wirelessSupported = supported,
                    wirelessEnabled = enabled,
                ).also { connectionStatus = it }
            }
        }
        internal fun observedTransport(): String? = instance?.let { if (it.isConnected) it.transport else null }
        /** Opt-in legacy TCP adb (`adb tcpip`): the setting and its port (default 5555). */
        const val PREF_LEGACY = "adb_legacy"
        const val PREF_LEGACY_PORT = "adb_legacy_port"
        @Volatile private var instance: Adb? = null
        fun get(ctx: Context): Adb = instance ?: synchronized(this) {
            instance ?: Adb(ctx.applicationContext).also { instance = it }
        }

        /** Drops the current connection (it may be hung on a restarted adbd); the next get() starts fresh. */
        fun reset() {
            val old = synchronized(this) {
                instance?.also {
                    instance = null
                    val status = connectionStatus
                    connectionStatus = status.copy(tls = status.tls.withoutStaleConnection(), legacy = status.legacy.withoutStaleConnection())
                }
            } ?: return
            Thread({ runCatching { old.close() } }, "adb-close").apply { isDaemon = true }.start()
        }

        // ------------------------------------------------------------------ notification flow

        const val NOTIF_ADB = 30
        const val KEY_CODE = "code"

        fun showPairingNotification(ctx: Context, text: String = ctx.getString(R.string.adb_notif_text)) {
            val input = RemoteInput.Builder(KEY_CODE).setLabel(ctx.getString(R.string.adb_notif_hint)).build()
            val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, AdbPairingReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val action = Notification.Action.Builder(null, ctx.getString(R.string.adb_notif_action), pi)
                .addRemoteInput(input).build()
            val n = Notification.Builder(ctx, TetherApp.CH_SETUP)
                .setSmallIcon(R.drawable.ic_tether)
                .setContentTitle(ctx.getString(R.string.adb_notif_title))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .addAction(action)
                .setOngoing(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ADB, n)
        }

        fun updateNotification(ctx: Context, text: String, done: Boolean) {
            if (!done) { showPairingNotification(ctx, text); return }
            val n = Notification.Builder(ctx, TetherApp.CH_SETUP).setSmallIcon(R.drawable.ic_tether)
                .setContentTitle(ctx.getString(R.string.adb_notif_title)).setContentText(text).setAutoCancel(true).build()
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ADB, n)
        }
    }
}

/** The notification's code field. Only checks the code and hands the work to [AdbPairing] (never blocks the broadcast). */
class AdbPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Adb.KEY_CODE)?.toString()
        val code = AdbPairing.normalizeCode(typed)
        // Always answer the reply: a notification that is not posted again keeps its spinner forever.
        if (code == null) { AdbPairing.show(context, AdbPairing.Result.BAD_CODE, null); return }
        AdbPairing.start(context, code)
    }
}
