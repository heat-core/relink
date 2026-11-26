package app.tether.audio

internal enum class HelperStage {
    STOPPED, CHECKING_ADB, STARTING_PROCESS, CONNECTING, AUTHENTICATING, WAITING_HELLO, READY, RETRY_WAIT, FAILED
}

internal enum class HelperFault {
    NONE, ADB_UNREACHABLE, ADB_TIMEOUT, PROCESS_EXITED, PROCESS_TIMEOUT, SOCKET_UNREACHABLE,
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
)

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
}

internal object AudioFallbackPolicy {
    // Silent samples by themselves do not establish a broken device. No writes or reads with an active call do.
    fun confirmedFailure(event: String) = event == "down-nothing" || event == "up-stuck"
    fun shouldRelease(startedActiveAt: Long, now: Long, receivedInfo: Boolean) =
        startedActiveAt >= 0 && !receivedInfo && now - startedActiveAt >= 8000
}
