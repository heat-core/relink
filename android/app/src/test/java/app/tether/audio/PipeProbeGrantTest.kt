package app.tether.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipeProbeGrantTest {
    @Test fun enforcesCallerTicketGenerationAndExpiryWithoutConsumingFailures() {
        val grant = PipeProbeGrant("valid-token", 14L, 150)
        assertFalse(grant.consume(10001, "valid-token", 14L, 140))
        assertFalse(grant.consume(2000, "invalid-key", 14L, 140))
        assertFalse(grant.consume(2000, "valid-token", 13L, 140))
        assertFalse(grant.consume(2000, "valid-token", 14L, 150))
        assertTrue(grant.consume(2000, "valid-token", 14L, 149))
        assertFalse(grant.consume(2000, "valid-token", 14L, 149))
    }
}
