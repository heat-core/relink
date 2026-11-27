package app.tether.adb

import java.net.InetAddress
import java.net.Inet6Address
import java.net.NetworkInterface

/** The two ways to reach the phone's own adbd (see [connectInOrder] and [probeAdb]). */
interface AdbEndpoints {
    /** Wireless debugging (TLS, found by mDNS). */
    fun connectTls(): Boolean
    /** Legacy TCP mode (`adb tcpip`) on 127.0.0.1:[port], RSA key approval. */
    fun connectLegacy(port: Int): Boolean
}

internal interface AdbDetailedEndpoints {
    fun probeTls(): AdbFailure
    fun probeLegacy(port: Int): AdbFailure
}

internal enum class ProbeMode { ACTIVE_FIRST, VERIFY_TCP_ONLY, VERIFY_ALL }

internal data class ProbeOutcome(
    val activeTransport: String?,
    val tls: AdbFailure,
    val tcp: AdbFailure,
)

/** How a connection was made: "tls", "legacy", or null when neither worked. */
object ConnectOrder {
    const val DEFAULT_LEGACY_PORT = 5555

    /** Wireless debugging exists from Android 11. */
    fun tlsSupported(sdk: Int) = sdk >= 30
}

/** Only this handset's numeric interface addresses are candidates for ADB discovery. */
internal object AdbAddressPolicy {
    fun isAllowedLocalAddress(addr: InetAddress, owned: Set<InetAddress> = deviceAddresses()): Boolean {
        if (addr.isAnyLocalAddress || addr.isMulticastAddress) return false
        if (addr is Inet6Address && addr.isLinkLocalAddress) {
            return addr.scopeId != 0 && owned.any { it is Inet6Address && it == addr && it.scopeId == addr.scopeId }
        }
        return addr.isLoopbackAddress || addr in owned
    }

    private fun deviceAddresses(): Set<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp }.flatMap { it.inetAddresses.toList() }.toSet()
    }.getOrDefault(emptySet())

}

/**
 * Probes Wireless debugging and/or legacy TCP ADB according to [mode]:
 * - [ProbeMode.ACTIVE_FIRST]: returns the first usable transport (same order as [connectInOrder]).
 * - [ProbeMode.VERIFY_TCP_ONLY]: explicitly verifies `127.0.0.1:[legacyPort]` even when TLS is enabled,
 *   never leaving `tcp` stuck at `NOT_ATTEMPTED`.
 * - [ProbeMode.VERIFY_ALL]: verifies TLS (when supported and enabled) and TCP (when [legacyEnabled])
 *   independently so both status fields reflect real connectivity.
 */
internal fun probeAdb(
    endpoints: AdbDetailedEndpoints,
    tlsSupported: Boolean,
    wirelessSettingOn: Boolean,
    legacyEnabled: Boolean,
    legacyPort: Int,
    mode: ProbeMode,
    previousTls: AdbFailure = AdbFailure.NOT_ATTEMPTED,
    previousLegacy: AdbFailure = AdbFailure.NOT_ATTEMPTED,
): ProbeOutcome {
    var tls = previousTls
    var tcp = previousLegacy
    var active: String? = null
    if (mode == ProbeMode.VERIFY_TCP_ONLY) {
        tcp = endpoints.probeLegacy(legacyPort)
        active = if (tcp == AdbFailure.CONNECTED) "legacy" else null
        return ProbeOutcome(active, tls, tcp)
    }
    if (tlsSupported) {
        tls = if (!wirelessSettingOn) AdbFailure.DEBUGGING_OFF else endpoints.probeTls()
        if (tls == AdbFailure.CONNECTED) {
            active = "tls"
            if (mode == ProbeMode.ACTIVE_FIRST) return ProbeOutcome("tls", tls, tcp)
        }
    } else {
        tls = AdbFailure.NOT_ATTEMPTED
    }
    if (legacyEnabled || mode == ProbeMode.VERIFY_ALL) {
        if (legacyEnabled) {
            tcp = endpoints.probeLegacy(legacyPort)
            if (tcp == AdbFailure.CONNECTED && active == null) active = "legacy"
        } else {
            tcp = AdbFailure.NOT_ATTEMPTED
        }
    }
    return ProbeOutcome(active, tls, tcp)
}

/**
 * Wireless debugging first (when this Android has it), then, only if the user opted in, legacy TCP adb on the
 * loopback address. A failure or exception of one way never stops the other; [log] gets one line per failure.
 */
fun connectInOrder(e: AdbEndpoints, tlsSupported: Boolean, legacyEnabled: Boolean, legacyPort: Int, log: (String) -> Unit = {}): String? {
    if (tlsSupported) {
        try { if (e.connectTls()) return "tls" } catch (t: Exception) { log("wireless debugging connect failed: ${classifyAdbFailure(t, discovery = true).name}") }
    }
    if (legacyEnabled) {
        try { if (e.connectLegacy(legacyPort)) return "legacy" } catch (t: Exception) { log("TCP adb connect failed: ${classifyAdbFailure(t).name}") }
    }
    return null
}
