package app.tether.adb

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

internal enum class AdbFailure {
    NOT_ATTEMPTED, CONNECTING, CONNECTED, DEBUGGING_OFF, DISCOVERY_TIMEOUT, NETWORK_UNREACHABLE,
    CONNECTION_REFUSED, AUTHENTICATION_FAILED, TIMEOUT, IO_ERROR, NOT_CONNECTED
}

internal data class AdbConnectionStatus(
    val tls: AdbFailure = AdbFailure.NOT_ATTEMPTED,
    val legacy: AdbFailure = AdbFailure.NOT_ATTEMPTED,
) {
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
