package app.tether.audio

import org.json.JSONArray

internal enum class HelperStage {
    STOPPED, CHECKING_ADB, STARTING_PROCESS, CONNECTING, AUTHENTICATING, WAITING_HELLO, READY, RETRY_WAIT, FAILED
}

internal class HelperEndpoint(val port: Int, val token: String) {
    companion object {
        fun restore(port: Int, token: String?): HelperEndpoint? =
            if (port in 1..65535 && token != null && token.length == 32 && token.all { it in '0'..'9' || it in 'a'..'f' }) {
                HelperEndpoint(port, token)
            } else {
                null
            }
    }
}

internal enum class HelperFault {
    NONE, ADB_UNREACHABLE, ADB_TIMEOUT, ADB_IDENTITY_UNVERIFIED, PROCESS_EXITED, PROCESS_TIMEOUT, SOCKET_UNREACHABLE,
    AUTH_REJECTED, AUTH_TIMEOUT, HELLO_TIMEOUT, HELLO_INVALID, INCOMPATIBLE, DISCONNECTED, PERMISSION_DENIED, IO_ERROR, INVALID_STATE, EXCEPTION;

    companion object {
        fun classify(error: Throwable): HelperFault = when (val cause = error.cause.takeIf { error is java.util.concurrent.ExecutionException } ?: error) {
            is SecurityException -> PERMISSION_DENIED
            is java.io.IOException -> IO_ERROR
            is IllegalStateException -> if (cause.message == "adb not connected") ADB_UNREACHABLE else INVALID_STATE
            else -> EXCEPTION
        }
    }
}

internal enum class AudioPipelineState {
    TRANSPORT_DISCONNECTED,
    HELPER_UNAVAILABLE,
    RECOVERY_IN_PROGRESS,
    HELPER_AUTHENTICATED,
    AUDIO_BACKEND_INITIALIZED,
    MEDIA_ROUTE_ESTABLISHED,
    CALL_AUDIO_OPERATING,
}

internal data class HelperHealth(
    val stage: HelperStage = HelperStage.STOPPED,
    val attempt: Int = 0,
    val fault: HelperFault = HelperFault.NONE
) {
    // Only typed fields may reach the diagnostic log. Exception messages and process arguments never pass here.
    fun line() = "stage=${stage.name} attempt=$attempt fault=${fault.name}"
}

/** Explicitly separated runtime states across ADB setup, Helper process/auth, and call-audio readiness. */
internal data class HelperStateSnapshot(
    val adbPaired: Boolean,
    val adbConnected: Boolean,
    val tcpConfigured: Boolean,
    val tcpVerified: Boolean,
    val helperProcessRunning: Boolean,
    val helperAuthenticated: Boolean,
    val audioBackendReady: Boolean,
    val captureAvailable: Boolean,
    val injectionAvailable: Boolean,
    val captureBackend: String,
    val injectionBackend: String,
    val recoveryAttempts: Int,
    val lastDisconnectReason: String?,
    val adbBootstrapAllowed: Boolean = false,
    val mediaRouteEstablished: Boolean = false,
    val callAudioOperating: Boolean = false,
    val localFallback: Boolean = false,
    val transportConnected: Boolean = true,
    val stage: HelperStage = HelperStage.STOPPED,
    val fault: HelperFault = HelperFault.NONE,
) {
    fun pipelineState(): AudioPipelineState = when {
        !transportConnected -> AudioPipelineState.TRANSPORT_DISCONNECTED
        stage == HelperStage.CHECKING_ADB || stage == HelperStage.STARTING_PROCESS ||
            stage == HelperStage.CONNECTING || stage == HelperStage.AUTHENTICATING ||
            stage == HelperStage.WAITING_HELLO || stage == HelperStage.RETRY_WAIT -> AudioPipelineState.RECOVERY_IN_PROGRESS
        !helperAuthenticated -> AudioPipelineState.HELPER_UNAVAILABLE
        callAudioOperating && !localFallback -> AudioPipelineState.CALL_AUDIO_OPERATING
        mediaRouteEstablished -> AudioPipelineState.MEDIA_ROUTE_ESTABLISHED
        audioBackendReady -> AudioPipelineState.AUDIO_BACKEND_INITIALIZED
        else -> AudioPipelineState.HELPER_AUTHENTICATED
    }
}

internal sealed interface HelperCheckOutcome {
    data object AlreadyRunning : HelperCheckOutcome
    data object Reattached : HelperCheckOutcome
    data object Bootstrapped : HelperCheckOutcome
    data class Failed(val fault: HelperFault, val adbConnected: Boolean) : HelperCheckOutcome
}

internal object AudioDiagnosticValues {
    private val capture = setOf("intercept", "voice_downlink", "telephony_rx", "voice_call", "voice_communication")
    private val injection = setOf("intercept", "telephony_tx", "policy_mix")
    fun capture(method: String?) = method?.takeIf(capture::contains) ?: "unverified"
    fun injection(method: String?) = method?.takeIf(injection::contains) ?: "unverified"

    fun observedBackend(method: String?, results: JSONArray?, isCapture: Boolean): String {
        val known = if (isCapture) capture else injection
        if (method != null && method in known) return method
        for (i in 0 until (results?.length() ?: 0)) {
            val result = results?.optJSONObject(i) ?: continue
            if (!result.optBoolean("ok") || (isCapture && result.optInt("samples") <= 0)) continue
            val found = result.optString("method")
            if (found in known) return found
        }
        return "unverified"
    }
}

internal object AudioFallbackPolicy {
    // Silent samples by themselves do not establish a broken device. No writes or reads with an active call do.
    fun confirmedFailure(event: String) = event == "down-nothing" || event == "up-stuck"
    fun shouldRelease(startedActiveAt: Long, now: Long, receivedInfo: Boolean) =
        startedActiveAt >= 0 && !receivedInfo && now - startedActiveAt >= 8000
}
