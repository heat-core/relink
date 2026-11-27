package app.tether.helper

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperConnectionsTest {
    @Test fun saturationRejectsNewSocketsWithoutEvictingAuthenticatedSession() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val activeClosed = AtomicBoolean(false)
        val extraClosed = AtomicBoolean(false)
        HelperConnections(limit = 1).use { connections ->
            try {
                connections.start({ activeClosed.set(true) }) { authenticated ->
                    if (authenticated()) entered.countDown()
                    finish.await(2, TimeUnit.SECONDS)
                }
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                connections.start({ extraClosed.set(true) }) { error("Unexpected queued connection") }
                assertTrue(extraClosed.get())
                assertFalse(activeClosed.get())
            } finally {
                finish.countDown()
            }
        }
    }

    @Test fun expiredHandshakeCannotAuthenticateAfterTheDeadline() {
        val closed = CountDownLatch(1)
        val result = java.util.concurrent.CompletableFuture<Boolean>()
        HelperConnections(limit = 1, authDeadlineMs = 50).use { connections ->
            connections.start({ closed.countDown() }) { authenticated ->
                if (closed.await(2, TimeUnit.SECONDS)) result.complete(authenticated())
            }
            assertFalse(result.get(3, TimeUnit.SECONDS))
        }
    }
}
