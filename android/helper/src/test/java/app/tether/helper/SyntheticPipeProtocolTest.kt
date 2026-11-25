package app.tether.helper

import org.junit.Assert.*
import org.junit.Test

class SyntheticPipeProtocolTest {
    @Test fun countsGapsAndRejectsOldGenerationDuplicateAndBadClock() {
        val probe = SyntheticPipeProtocol.Measurement(8)
        fun add(generation: Long, seq: Int, sent: Long = 1000) =
            probe.add(SyntheticPipeProtocol.frame(generation, seq, sent), 6000)
        add(8, 0)
        add(7, 1)
        add(8, 0)
        add(8, 1, 6001)
        probe.add(ByteArray(4), 6000)
        add(8, 3)
        assertEquals(2, probe.received)
        assertEquals(38, probe.missing)
        assertEquals(4, probe.rejected)
        assertEquals(5L, probe.p95Micros)
    }

    @Test fun ignoresSequenceOutsideTheProbe() {
        val probe = SyntheticPipeProtocol.Measurement(8)
        probe.add(SyntheticPipeProtocol.frame(8, SyntheticPipeProtocol.frameCount, 1), 2)
        assertEquals(0, probe.received)
        assertEquals(1, probe.rejected)
        assertNull(probe.p95Micros)
    }

    @Test fun sanitizesBootstrapFailure() {
        val failure = runCatching {
            ShellStartup.required(ShellStartup.Step.SYSTEM_CONTEXT) {
                throw SecurityException("sensitive data")
            }
        }.exceptionOrNull() as ShellStartup.Failure
        assertEquals("shell-init:SYSTEM_CONTEXT:SecurityException", failure.message)
        assertNull(failure.cause)
        assertEquals(ShellStartup.Step.SYSTEM_CONTEXT,
            ShellStartup.failureFrom("process-exited\nshell-init-failed:SYSTEM_CONTEXT\n"))
        assertNull(ShellStartup.failureFrom("shell-init-failed:SYSTEM_CONTEXT secret"))
    }
}
