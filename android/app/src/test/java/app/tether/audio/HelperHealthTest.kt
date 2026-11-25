package app.tether.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperHealthTest {
    @Test fun exportAllowsOnlyKnownBackendMethods() {
        assertEquals("intercept", AudioDiagnosticValues.capture("intercept"))
        assertEquals("telephony_tx", AudioDiagnosticValues.injection("telephony_tx"))
        for (value in listOf(null, "null", "intercept: user@example.org", "0800123456", "private message")) {
            assertEquals("unverified", AudioDiagnosticValues.capture(value))
            assertEquals("unverified", AudioDiagnosticValues.injection(value))
        }
    }

    @Test fun diagnosticsContainOnlyAStageAttemptAndTypedFault() {
        assertEquals("stage=FAILED attempt=2 fault=HELLO_TIMEOUT",
            HelperHealth(HelperStage.FAILED, 2, HelperFault.HELLO_TIMEOUT).line())
    }

    @Test fun exceptionMessagesAreNotExported() {
        val fault = HelperFault.classify(java.util.concurrent.ExecutionException(SecurityException("private@example.org")))
        assertEquals(HelperFault.PERMISSION_DENIED, fault)
        assertFalse(HelperHealth(HelperStage.FAILED, fault = fault).line().contains("private"))
    }

    @Test fun deadlineBeginsOnlyOnceTheCallIsActive() {
        assertFalse(AudioFallbackPolicy.shouldRelease(-1, 99_999, false))
        assertFalse(AudioFallbackPolicy.shouldRelease(1000, 8999, false))
        assertTrue(AudioFallbackPolicy.shouldRelease(1000, 9000, false))
        assertFalse(AudioFallbackPolicy.shouldRelease(1000, 9000, true))
    }

    @Test fun stalledStreamsDifferFromSilentSamples() {
        assertTrue(AudioFallbackPolicy.confirmedFailure("down-nothing"))
        assertTrue(AudioFallbackPolicy.confirmedFailure("up-stuck"))
        assertFalse(AudioFallbackPolicy.confirmedFailure("down-zeros"))
    }
}
