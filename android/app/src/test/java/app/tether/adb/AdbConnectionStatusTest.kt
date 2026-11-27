package app.tether.adb

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
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

    @Test fun settingsChangesInvalidatePreviousVerification() {
        val live = AdbConnectionStatus(tls = AdbFailure.CONNECTED, legacy = AdbFailure.CONNECTED, tcpConfigured = true)
        val changed = live.withSettings(true, true, 5556, wirelessSupported = true, wirelessEnabled = false)
        assertEquals(AdbFailure.DEBUGGING_OFF, changed.tls)
        assertEquals(AdbFailure.NOT_ATTEMPTED, changed.legacy)
        val enabledAgain = changed.withSettings(true, false, 5556, wirelessSupported = true, wirelessEnabled = true)
        assertEquals(AdbFailure.NOT_ATTEMPTED, enabledAgain.tls)
        assertFalse(enabledAgain.anyConnected)
    }

    @Test fun bootstrapInputIsBoundToOneVerifiedAndAllowedRoute() {
        val settings = AdbConnectionStatus(wirelessEnabled = true, tcpConfigured = true, tcpPort = 5555)
        val wireless = VerifiedAdbRoute()
        val legacy = VerifiedAdbRoute(5555)
        assertTrue(wireless.acceptsInput(wireless, settings))
        assertFalse(wireless.acceptsInput(VerifiedAdbRoute(), settings))
        assertFalse(wireless.acceptsInput(legacy, settings))
        assertFalse(wireless.acceptsInput(null, settings))
        assertFalse(wireless.acceptsInput(wireless, settings.copy(wirelessEnabled = false)))
        assertTrue(legacy.acceptsInput(legacy, settings))
        assertFalse(legacy.acceptsInput(legacy, settings.copy(tcpPort = 5556)))
        assertFalse(legacy.acceptsInput(legacy, settings.copy(tcpConfigured = false)))
    }

    @Test fun localPolicyAllowsOnlyLoopbackAndOwnedInterfaceAddresses() {
        assertTrue(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("127.0.0.1"), emptySet()))
        assertTrue(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("::1"), emptySet()))
        val localV4 = InetAddress.getByName("192.168.1.42")
        val owned = setOf(localV4)
        assertTrue(AdbAddressPolicy.isAllowedLocalAddress(localV4, owned))
        assertFalse(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("192.168.1.43"), owned))
        val linkLocal = InetAddress.getByName("fe80::1").address
        val ownedV6 = java.net.Inet6Address.getByAddress(null, linkLocal, 2)
        assertTrue(AdbAddressPolicy.isAllowedLocalAddress(ownedV6, setOf(ownedV6)))
        assertFalse(AdbAddressPolicy.isAllowedLocalAddress(java.net.Inet6Address.getByAddress(null, linkLocal, 3), setOf(ownedV6)))
        assertFalse(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("fe80::1"), setOf(ownedV6)))
        val wildcard = InetAddress.getByName("0.0.0.0")
        val multicast = InetAddress.getByName("224.0.0.251")
        assertFalse(AdbAddressPolicy.isAllowedLocalAddress(wildcard, setOf(wildcard)))
        assertFalse(AdbAddressPolicy.isAllowedLocalAddress(multicast, setOf(multicast)))
    }
}
