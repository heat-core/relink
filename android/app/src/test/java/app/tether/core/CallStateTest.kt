package app.tether.core

import app.tether.telecom.addressedCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallStateTest {
    @Test fun commandTargetsItsOwnCall() {
        assertEquals("A", addressedCall("a", mapOf("a" to "A", "b" to "B")))
    }

    @Test fun lateCommandNeverHitsTheNextCall() {
        assertNull(addressedCall("a", mapOf("b" to "B")))
    }

    @Test fun commandFromBeforeAHostRestartDoesNotFallBack() {
        assertNull(addressedCall("old", mapOf("new" to "active")))
        assertNull(addressedCall("", mapOf("new" to "ringing")))
    }
}
