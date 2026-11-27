package app.tether.data

import app.tether.data.SmsParts.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsPartsTest {
    @Test fun lastPartArrivingFirstDoesNotConfirmTheText() {
        val parts = SmsParts()
        assertTrue(parts.onSent(2, 3, null).isEmpty())
        assertTrue(parts.onSent(0, 3, 7).isEmpty())
        assertEquals(listOf(Result("failed", 7)), parts.onSent(1, 3, null))
    }

    @Test fun successfulTextWaitsForEveryDistinctPart() {
        val parts = SmsParts()
        assertTrue(parts.onSent(1, 2, null).isEmpty())
        assertTrue(parts.onSent(1, 2, null).isEmpty())
        assertEquals(listOf(Result("sent")), parts.onSent(0, 2, null))
        assertTrue(parts.onSent(0, 2, null).isEmpty())
    }

    @Test fun earlierFailureIsReportedWithItsActualError() {
        val parts = SmsParts()
        assertTrue(parts.onSent(0, 2, 4).isEmpty())
        assertEquals(listOf(Result("failed", 4)), parts.onSent(1, 2, null))
    }

    @Test fun onlyTheFinalPartNeedsToReportDelivery() {
        val parts = SmsParts()
        assertTrue(parts.onSent(0, 2, null).isEmpty())
        assertEquals(listOf(Result("sent")), parts.onSent(1, 2, null))
        assertEquals(listOf(Result("delivered")), parts.onDelivery(last = true, error = null))
        assertTrue(parts.onDelivery(last = true, error = null).isEmpty())
    }

    @Test fun earlyDeliveryIsReportedAfterSendSuccess() {
        val parts = SmsParts()
        assertTrue(parts.onSent(1, 2, null).isEmpty())
        assertTrue(parts.onDelivery(last = true, error = null).isEmpty())
        assertEquals(listOf(Result("sent"), Result("delivered")), parts.onSent(0, 2, null))
    }

    @Test fun earlyDeliveryCannotHideSendFailure() {
        val parts = SmsParts()
        assertTrue(parts.onDelivery(last = true, error = null).isEmpty())
        assertTrue(parts.onSent(1, 2, null).isEmpty())
        assertEquals(listOf(Result("failed", 7)), parts.onSent(0, 2, 7))
        assertTrue(parts.onDelivery(last = true, error = null).isEmpty())
    }

    @Test fun failedDeliveryReportsItsOwnError() {
        val parts = SmsParts()
        assertEquals(listOf(Result("sent")), parts.onSent(0, 1, null))
        assertEquals(listOf(Result("failed", 5)), parts.onDelivery(last = true, error = 5))
    }

    @Test fun singlePartAndInvalidMetadata() {
        val parts = SmsParts()
        assertTrue(parts.onSent(0, 0, null).isEmpty())
        assertTrue(parts.onSent(-1, 1, null).isEmpty())
        assertTrue(parts.onSent(1, 1, null).isEmpty())
        assertEquals(listOf(Result("failed", 2)), parts.onSent(0, 1, 2))
    }
}
