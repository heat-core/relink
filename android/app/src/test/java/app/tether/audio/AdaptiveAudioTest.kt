package app.tether.audio

import app.tether.helper.AudioStrategies
import org.junit.Assert.*
import org.junit.Test

class AdaptiveAudioTest {
    @Test fun usesReadyFallbackWhenPreferredBackendCannotInject() {
        val preferred = FakeBackend("preferred", ready = true, injection = false)
        val tcp = FakeBackend("tcp", ready = true)
        assertSame(tcp, AudioBackendSelection.select(listOf(preferred, tcp), 33))
        assertNull(AudioBackendSelection.select(listOf(FakeBackend("tcp", ready = false)), 33))
    }

    @Test fun selectsOnlyEligibleAndroidRoutes() {
        assertTrue(AudioStrategies.capture(29).isEmpty())
        assertEquals(listOf("telephony_tx"), AudioStrategies.injection(32))
        assertEquals("intercept", AudioStrategies.capture(33).first())
    }

    @Test fun verifiedPreferenceKeepsFallbacksAndSkipsFailedRoute() {
        val options = AudioStrategies.injection(33)
        assertEquals(listOf("telephony_tx", "intercept", "policy_mix"),
            AudioStrategies.order(options, "telephony_tx", ""))
        assertEquals(listOf("intercept", "policy_mix"), AudioStrategies.order(options, "telephony_tx", "telephony_tx"))
        assertEquals(options, AudioStrategies.order(options, "unrecognized", ""))
    }

    @Test fun confirmedFailureRetriesOnlyTheAffectedDirection() {
        assertEquals(RouteRetry(skipCapture = "intercept"), RouteRetry.forFailure("down-nothing", 33, "intercept", "telephony_tx"))
        assertEquals(RouteRetry(skipInjection = "intercept"), RouteRetry.forFailure("up-stuck", 33, "intercept", "intercept"))
        assertNull(RouteRetry.forFailure("up-stuck", 32, "voice_downlink", "telephony_tx"))
        assertNull(RouteRetry.forFailure("down-zeros", 33, "intercept", "intercept"))
    }

    private class FakeBackend(override val id: String, override val ready: Boolean, private val injection: Boolean = true) : AudioBackend {
        override fun capabilities(sdk: Int) = AudioBackendCapabilities(sdk >= 30, sdk >= 30 && injection)
        override fun start(request: AudioBackendRequest) = ready
        override fun stop() = true
        override fun writeUplink(pcm: ShortArray, forStart: Int) = true
    }
}
