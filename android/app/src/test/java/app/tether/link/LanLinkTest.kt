package app.tether.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LanLinkTest {
    private fun ip(value: String) = InetAddress.getByName(value) as Inet4Address

    @Test fun admitsOnlyPeersOnTheBoundPrivateSubnet() {
        val host = ip("192.168.20.1")
        assertTrue(LanLink.sameLan(host, ip("192.168.20.40"), 24))
        assertFalse(LanLink.sameLan(host, ip("192.168.21.40"), 24))
        assertFalse(LanLink.sameLan(host, ip("192.168.20.0"), 24))
        assertFalse(LanLink.sameLan(host, ip("192.168.20.255"), 24))
        assertFalse(LanLink.sameLan(host, host, 24))
        assertFalse(LanLink.sameLan(host, ip("192.168.20.40"), 0))
        assertFalse(LanLink.sameLan(ip("203.0.113.1"), ip("203.0.113.2"), 24))
        assertFalse(LanLink.sameLan(ip("127.0.0.1"), ip("127.0.0.2"), 8))
    }

    @Test fun prefixChangesInvalidatePreviousHotspotPeers() {
        val host = ip("172.20.10.1")
        val peer = ip("172.20.10.34")
        assertTrue(LanLink.sameLan(host, peer, 24))
        assertFalse(LanLink.sameLan(host, peer, 28))
    }

    @Test fun orderedStreamClosesAndReleasesItsSocketExactlyOnce() {
        val loopback = InetAddress.getLoopbackAddress()
        val input = byteArrayOf(10, 20, 30)
        val output = byteArrayOf(40, 50, 60)
        val done = CountDownLatch(1)
        val releases = AtomicInteger()
        ServerSocket(0, 1, loopback).use { server ->
            Socket(loopback, server.localPort).use { client ->
                client.soTimeout = 2000
                server.accept().use { socket ->
                    val link = LanTransport(socket) { releases.incrementAndGet() }
                    assertFalse(link.canPair)
                    try {
                        link.start({ bytes, count -> link.write(bytes.copyOf(count)) }, { done.countDown() })
                        client.getOutputStream().write(input)
                        assertArrayEquals(input, client.getInputStream().readNBytes(input.size))
                        link.write(output)
                        assertArrayEquals(output, client.getInputStream().readNBytes(output.size))
                        client.shutdownOutput()
                        assertTrue(done.await(2, TimeUnit.SECONDS))
                    } finally {
                        link.close()
                    }
                    assertEquals(1, releases.get())
                }
            }
        }
    }
}
