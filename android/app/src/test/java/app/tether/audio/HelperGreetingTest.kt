package app.tether.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class HelperGreetingTest {
    private val apk = "/data/app/current/base.apk"

    @Test fun helloPreservesTheFirstFollowingFrame() {
        val input = data(frame("""{"apk":"$apk","version":3}""") + byteArrayOf(6))
        val greeting = HelperGreeting.read(input)
        assertTrue(HelperGreeting.compatible(greeting, apk, 3))
        assertEquals(6, input.readUnsignedByte())
    }

    @Test fun malformedTruncatedAndOutOfOrderFramesFail() {
        for (bytes in listOf(frame("{"), frame("{}").dropLast(1).toByteArray(), frame("{}", type = 6))) {
            assertThrows(IOException::class.java) { HelperGreeting.read(data(bytes)) }
        }
    }

    @Test fun invalidLengthFailsWithoutWaitingForAPayload() {
        for (size in listOf(-1, 0, 4097)) {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).apply { writeByte(1); writeInt(size) }
            assertThrows(IOException::class.java) { HelperGreeting.read(data(bytes.toByteArray())) }
        }
    }

    @Test fun silentLocalPeerTimesOutThenANewConnectionCanGreet() {
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 2, loopback).use { server ->
            Socket(loopback, server.localPort).use { client ->
                server.accept().use {
                    client.soTimeout = 100
                    assertThrows(SocketTimeoutException::class.java) {
                        HelperGreeting.read(DataInputStream(client.getInputStream()))
                    }
                }
            }
            Socket(loopback, server.localPort).use { client ->
                server.accept().use { peer ->
                    client.soTimeout = 1000
                    peer.getOutputStream().write(frame("""{"apk":"$apk","version":3}"""))
                    assertTrue(HelperGreeting.compatible(HelperGreeting.read(DataInputStream(client.getInputStream())), apk, 3))
                }
            }
        }
    }

    @Test fun unchangedApkPathWithTheWrongOrMissingProtocolIsIncompatible() {
        for (json in listOf("""{"apk":"$apk","version":2}""", """{"apk":"$apk"}""", """{"apk":"$apk","version":"3"}""")) {
            assertFalse(HelperGreeting.compatible(JSONObject(json), apk, 3))
        }
    }

    @Test fun matchingProtocolWithAnOlderApkPathIsIncompatible() {
        assertFalse(HelperGreeting.compatible(JSONObject("""{"apk":"/data/app/previous/base.apk","version":3}"""), apk, 3))
    }

    private fun data(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))

    private fun frame(json: String, type: Int = 1): ByteArray {
        val bytes = ByteArrayOutputStream()
        val payload = json.toByteArray(Charsets.UTF_8)
        DataOutputStream(bytes).apply {
            writeByte(type)
            writeInt(payload.size)
            write(payload)
        }
        return bytes.toByteArray()
    }
}
