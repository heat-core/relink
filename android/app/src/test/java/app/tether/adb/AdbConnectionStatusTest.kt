package app.tether.adb

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbConnectionStatusTest {
    @Test fun shellProbeRequiresAReplyAndHandlesClosedStreams() {
        assertEquals(AdbFailure.CONNECTED, verifyAdbShell { service ->
            assertEquals("shell,raw:printf R", service)
            'R'.code
        })
        assertEquals(AdbFailure.SHELL_PROBE_FAILED, verifyAdbShell { -1 })
        assertEquals(AdbFailure.SHELL_PROBE_FAILED, verifyAdbShell { 'X'.code })
        assertEquals(AdbFailure.SHELL_PROBE_FAILED, verifyAdbShell { throw IOException("Stream closed") })
    }

    @Test fun stalledShellProbeTimesOut() {
        assertEquals(AdbFailure.TIMEOUT, verifyAdbShell(timeoutMs = 20) {
            Thread.sleep(10_000)
            'R'.code
        })
    }

    @Test fun distinctTransportFailuresNeverExposeExceptionText() {
        assertEquals(AdbFailure.CONNECTION_REFUSED, classifyAdbFailure(ConnectException("ECONNREFUSED host.example")))
        assertEquals(AdbFailure.NETWORK_UNREACHABLE, classifyAdbFailure(NoRouteToHostException()))
        assertEquals(AdbFailure.AUTHENTICATION_FAILED, classifyAdbFailure(ExecutionException(SSLHandshakeException("credential detail"))))
        assertEquals(AdbFailure.TIMEOUT, classifyAdbFailure(SocketTimeoutException()))
        assertEquals(AdbFailure.DISCOVERY_TIMEOUT,
            classifyAdbFailure(InterruptedException("Timed out while trying to find a valid host address and port"), discovery = true))
        assertEquals(AdbFailure.IO_ERROR, classifyAdbFailure(IOException("private value")))
    }

    @Test fun connectionStatusSeparatesConfigurationFromVerification() {
        val unverifiedTcp = AdbConnectionStatus(
            tls = AdbFailure.DEBUGGING_OFF,
            legacy = AdbFailure.CONNECTION_REFUSED,
            paired = true,
            tcpConfigured = true,
            tcpPort = 5555,
            checkedAtMs = 1234L,
        )
        assertFalse(unverifiedTcp.tlsConnected)
        assertFalse(unverifiedTcp.tcpVerified)
        assertFalse(unverifiedTcp.anyConnected)
        assertTrue(unverifiedTcp.isTcpConfiguredUnverified())

        val verifiedTcp = unverifiedTcp.copy(legacy = AdbFailure.CONNECTED)
        assertTrue(verifiedTcp.tcpVerified)
        assertTrue(verifiedTcp.anyConnected)
        assertFalse(verifiedTcp.isTcpConfiguredUnverified())
    }
}
