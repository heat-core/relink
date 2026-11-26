package app.tether.adb

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

internal enum class AdbFailure {
    NOT_ATTEMPTED, CONNECTING, CONNECTED, DEBUGGING_OFF, DISCOVERY_TIMEOUT, NETWORK_UNREACHABLE,
    CONNECTION_REFUSED, AUTHENTICATION_FAILED, TIMEOUT, IO_ERROR, NOT_CONNECTED, NON_LOOPBACK_REJECTED, SHELL_PROBE_FAILED;

    fun withoutStaleConnection() = if (this == CONNECTED) NOT_ATTEMPTED else this
}

internal data class AdbConnectionStatus(
    val tls: AdbFailure = AdbFailure.NOT_ATTEMPTED,
    val legacy: AdbFailure = AdbFailure.NOT_ATTEMPTED,
    val paired: Boolean = false,
    val tcpConfigured: Boolean = false,
    val tcpPort: Int = ConnectOrder.DEFAULT_LEGACY_PORT,
    val checkedAtMs: Long = -1L,
) {
    val tlsConnected: Boolean get() = tls == AdbFailure.CONNECTED
    val tcpVerified: Boolean get() = legacy == AdbFailure.CONNECTED
    val anyConnected: Boolean get() = tlsConnected || tcpVerified
    fun isTcpConfiguredUnverified(): Boolean = tcpConfigured && !tcpVerified
    fun summary(): String = "Wireless: ${tls.name} · TCP: ${legacy.name}"
}

/** Classification keeps original endpoint information and exception messages out of exported diagnostics. */
internal fun classifyAdbFailure(error: Throwable, discovery: Boolean = false): AdbFailure {
    for (e in generateSequence(error) { it.cause?.takeUnless { cause -> cause === it } }.take(8)) {
        val kind = e.javaClass.simpleName
        val message = e.message.orEmpty()
        when {
            kind in setOf("AdbPairingRequiredException", "AdbAuthenticationFailedException") ||
                e is SSLException || e is SecurityException -> return AdbFailure.AUTHENTICATION_FAILED
            e is NoRouteToHostException || message.contains("ENETUNREACH") ||
                message.contains("EHOSTUNREACH") -> return AdbFailure.NETWORK_UNREACHABLE
            message.contains("ECONNREFUSED") ||
                (e is ConnectException && message.contains("refused", ignoreCase = true)) -> return AdbFailure.CONNECTION_REFUSED
            discovery && (message.contains("Timed out while trying to find", ignoreCase = true) ||
                message.contains("Could not find any valid host address", ignoreCase = true)) -> return AdbFailure.DISCOVERY_TIMEOUT
            e is SocketTimeoutException || e is TimeoutException || e is InterruptedException ||
                message.contains("ETIMEDOUT") -> return AdbFailure.TIMEOUT
        }
    }
    return if (error is IOException) AdbFailure.IO_ERROR else AdbFailure.NOT_CONNECTED
}

/** Require an actual shell response. libadb may return EOF or throw when an empty shell exits. */
internal fun verifyAdbShell(timeoutMs: Long = 2_000, readResponse: (String) -> Int): AdbFailure {
    val probe = FutureTask { readResponse("shell,raw:printf R") }
    Thread(probe, "adb-shell-probe").apply { isDaemon = true }.start()
    return try {
        if (probe.get(timeoutMs, TimeUnit.MILLISECONDS) == 'R'.code) AdbFailure.CONNECTED else AdbFailure.SHELL_PROBE_FAILED
    } catch (_: TimeoutException) {
        AdbFailure.TIMEOUT
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        AdbFailure.TIMEOUT
    } catch (failure: ExecutionException) {
        classifyAdbFailure(failure.cause ?: failure).takeUnless { it == AdbFailure.IO_ERROR }
            ?: AdbFailure.SHELL_PROBE_FAILED
    } finally {
        probe.cancel(true)
    }
}
