package app.tether

import app.tether.adb.AdbAddressPolicy
import app.tether.adb.AdbDetailedEndpoints
import app.tether.adb.AdbEndpoints
import app.tether.adb.AdbFailure
import app.tether.adb.ConnectOrder
import app.tether.adb.ProbeMode
import app.tether.adb.connectInOrder
import app.tether.adb.probeAdb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class AdbConnectOrderTest {
    private class Fake(val tls: Result<Boolean>, val legacy: Result<Boolean>) : AdbEndpoints {
        val calls = mutableListOf<String>()
        override fun connectTls(): Boolean { calls += "tls"; return tls.getOrThrow() }
        override fun connectLegacy(port: Int): Boolean { calls += "legacy:$port"; return legacy.getOrThrow() }
    }
    private fun ok() = Result.success(true)
    private fun no() = Result.success(false)
    private fun boom() = Result.failure<Boolean>(java.io.IOException("refused"))

    @Test fun tlsWinsAndLegacyIsNotTried() {
        val f = Fake(ok(), ok())
        assertEquals("tls", connectInOrder(f, true, true, 5555))
        assertEquals(listOf("tls"), f.calls)
    }

    @Test fun legacyIsTriedSecondAfterTlsFailsOrThrows() {
        for (tls in listOf(no(), boom())) {
            val f = Fake(tls, ok())
            val log = mutableListOf<String>()
            assertEquals("legacy", connectInOrder(f, true, true, 5556) { log += it })
            assertEquals(listOf("tls", "legacy:5556"), f.calls)
            assertEquals(if (tls.isFailure) 1 else 0, log.size)
        }
    }

    @Test fun legacyIsOptIn() {
        val f = Fake(no(), ok())
        assertNull(connectInOrder(f, true, false, 5555))
        assertEquals(listOf("tls"), f.calls)
    }

    @Test fun androidWithoutWirelessDebuggingGoesStraightToLegacy() {
        assertEquals(false, ConnectOrder.tlsSupported(29))
        assertEquals(true, ConnectOrder.tlsSupported(30))
        val f = Fake(ok(), ok())
        assertEquals("legacy", connectInOrder(f, false, true, 5555))
        assertEquals(listOf("legacy:5555"), f.calls)
    }

    @Test fun bothFailing() {
        val f = Fake(no(), boom())
        assertNull(connectInOrder(f, true, true, 5555))
        assertEquals(listOf("tls", "legacy:5555"), f.calls)
    }

    private class DetailedFake(val tls: AdbFailure, val tcp: AdbFailure) : AdbDetailedEndpoints {
        val calls = mutableListOf<String>()
        override fun probeTls(): AdbFailure { calls += "tls"; return tls }
        override fun probeLegacy(port: Int): AdbFailure { calls += "legacy:$port"; return tcp }
    }

    @Test fun verifyTcpOnlyAlwaysTestsTcpEvenWhenTlsIsConnected() {
        val f = DetailedFake(AdbFailure.CONNECTED, AdbFailure.CONNECTED)
        val out = probeAdb(
            endpoints = f,
            tlsSupported = true,
            wirelessSettingOn = true,
            legacyEnabled = true,
            legacyPort = 5555,
            mode = ProbeMode.VERIFY_TCP_ONLY,
            previousTls = AdbFailure.CONNECTED,
            previousLegacy = AdbFailure.NOT_ATTEMPTED,
        )
        assertEquals(listOf("legacy:5555"), f.calls)
        assertEquals("legacy", out.activeTransport)
        assertEquals(AdbFailure.CONNECTED, out.tls)
        assertEquals(AdbFailure.CONNECTED, out.tcp)

        val refused = DetailedFake(AdbFailure.CONNECTED, AdbFailure.CONNECTION_REFUSED)
        val outFail = probeAdb(
            endpoints = refused,
            tlsSupported = true,
            wirelessSettingOn = true,
            legacyEnabled = true,
            legacyPort = 5555,
            mode = ProbeMode.VERIFY_TCP_ONLY,
            previousTls = AdbFailure.CONNECTED,
            previousLegacy = AdbFailure.NOT_ATTEMPTED,
        )
        assertEquals(listOf("legacy:5555"), refused.calls)
        assertNull(outFail.activeTransport)
        assertEquals(AdbFailure.CONNECTED, outFail.tls)
        assertEquals(AdbFailure.CONNECTION_REFUSED, outFail.tcp)
    }

    @Test fun tcpOnlyDoesNotReportLostWirelessConnection() {
        val f = DetailedFake(AdbFailure.CONNECTED, AdbFailure.CONNECTION_REFUSED)
        val out = probeAdb(
            endpoints = f,
            tlsSupported = true,
            wirelessSettingOn = true,
            legacyEnabled = false,
            legacyPort = 5555,
            mode = ProbeMode.VERIFY_TCP_ONLY,
            previousTls = AdbFailure.CONNECTED.withoutStaleConnection(),
        )
        assertEquals(listOf("legacy:5555"), f.calls)
        assertNull(out.activeTransport)
        assertEquals(AdbFailure.NOT_ATTEMPTED, out.tls)
        assertEquals(AdbFailure.CONNECTION_REFUSED, out.tcp)
    }

    @Test fun verifyAllProbesBothIndependentlyWhenWifiOff() {
        val f = DetailedFake(AdbFailure.CONNECTED, AdbFailure.CONNECTED)
        val out = probeAdb(
            endpoints = f,
            tlsSupported = true,
            wirelessSettingOn = false,
            legacyEnabled = true,
            legacyPort = 5556,
            mode = ProbeMode.VERIFY_ALL,
        )
        assertEquals(listOf("legacy:5556"), f.calls)
        assertEquals(AdbFailure.DEBUGGING_OFF, out.tls)
        assertEquals(AdbFailure.CONNECTED, out.tcp)
        assertEquals("legacy", out.activeTransport)
    }

    @Test fun adbDiscoveryPolicyAllowsOnlyLocalAddresses() {
        assertTrue(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("192.168.43.1")))
        assertTrue(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("10.0.0.5")))
        assertFalse(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(AdbAddressPolicy.isAllowedLocalAddress(InetAddress.getByName("2001:4860:4860::8888")))
    }
}
