package app.tether.audio

import app.tether.helper.AudioStrategies

/** Backends appear in preference order. An unavailable one cannot mask a ready fallback. */
internal object AudioBackendSelection {
    fun select(backends: List<AudioBackend>, sdk: Int): AudioBackend? = backends.firstOrNull {
        val capability = it.capabilities(sdk)
        it.ready && capability.capture && capability.injection
    }
}

/** Only confirmed stalls consume a call's single recovery attempt. Quiet capture alone is inconclusive. */
internal data class RouteRetry(val skipCapture: String? = null, val skipInjection: String? = null) {
    companion object {
        fun forFailure(event: String, sdk: Int, capture: String?, injection: String?): RouteRetry? = when {
            event == "down-nothing" && capture in AudioStrategies.capture(sdk) &&
                AudioStrategies.capture(sdk).size > 1 -> RouteRetry(skipCapture = capture)
            event == "up-stuck" && injection in AudioStrategies.injection(sdk) &&
                AudioStrategies.injection(sdk).size > 1 -> RouteRetry(skipInjection = injection)
            else -> null
        }
    }
}
