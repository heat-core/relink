package app.tether.helper

/** Device-side candidates. Eligibility never asserts that the vendor audio stack will open a route. */
object AudioStrategies {
    fun capture(sdk: Int): List<String> = when {
        sdk >= 33 -> listOf("intercept", "voice_downlink", "telephony_rx", "voice_call")
        sdk >= 30 -> listOf("voice_downlink", "voice_call", "telephony_rx")
        else -> emptyList()
    }

    fun injection(sdk: Int): List<String> = when {
        sdk >= 33 -> listOf("intercept", "telephony_tx", "policy_mix")
        sdk >= 30 -> listOf("telephony_tx")
        else -> emptyList()
    }

    /** `telephony_rx` uses AudioSource.DEFAULT + preferredDevice and must never silently fall back to BUILTIN_MIC. */
    fun isTelephonyInputRouteValid(method: String, routedType: Int, telephonyType: Int): Boolean =
        method != "telephony_rx" || routedType == telephonyType

    /** A verified preference is tried first; a failed candidate is excluded only for this recovery. */
    fun order(candidates: List<String>, preferred: String, excluded: String): List<String> {
        val available = candidates.filterNot { it == excluded }
        return if (preferred in available) listOf(preferred) + available.filterNot { it == preferred } else available
    }
}
