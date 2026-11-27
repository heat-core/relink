package app.tether.link

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandLedgerTest {
    private val operation = UUID.randomUUID().toString()

    @Test fun replacementGetsResultsAndNeverStartsTheOperationAgain() {
        val ledger = CommandLedger()
        val original = mutableListOf<String>()
        val replacement = mutableListOf<String>()
        val first = ledger.admit("peer", ledger.epoch, operation, "sms", original::add) as CommandLedger.Admission.Start
        first.entry.publish("sent")
        assertEquals(CommandLedger.Admission.Duplicate, ledger.admit("peer", ledger.epoch, operation, "sms", replacement::add))
        first.entry.publish("delivered")
        assertEquals(listOf("sent"), original)
        assertEquals(listOf("sent", "delivered"), replacement)
    }

    @Test fun aReplayBeforeTheFirstResultOnlyMovesTheReplyRoute() {
        val ledger = CommandLedger()
        val replacement = mutableListOf<String>()
        val first = ledger.admit("peer", ledger.epoch, operation, "dial") { error("retired transport") } as CommandLedger.Admission.Start
        assertEquals(CommandLedger.Admission.Duplicate, ledger.admit("peer", ledger.epoch, operation, "dial", replacement::add))
        first.entry.publish("accepted")
        assertEquals(listOf("accepted"), replacement)
    }

    @Test fun identityIsScopedToPeerAndCannotChangeCommandType() {
        val ledger = CommandLedger()
        assertTrue(ledger.admit("first", ledger.epoch, operation, "sms") {} is CommandLedger.Admission.Start)
        assertTrue(ledger.admit("second", ledger.epoch, operation, "dial") {} is CommandLedger.Admission.Start)
        assertEquals(CommandLedger.Admission.Invalid, ledger.admit("first", ledger.epoch, operation, "dial") {})
        assertEquals(CommandLedger.Admission.Invalid, ledger.admit("first", ledger.epoch, "a", "sms") {})
    }

    @Test fun capacityAndHostRestartNeverReopenPreviouslyAcceptedIds() {
        val ledger = CommandLedger(capacity = 1)
        val retired = ledger.epoch
        assertTrue(ledger.admit("peer", retired, operation, "dial") {} is CommandLedger.Admission.Start)
        assertEquals(CommandLedger.Admission.Retired, ledger.admit("peer", retired, UUID.randomUUID().toString(), "dial") {})
        assertEquals(CommandLedger.Admission.Retired, ledger.admit("peer", retired, operation, "dial") {})
        assertEquals(CommandLedger.Admission.Retired, CommandLedger().admit("peer", retired, operation, "dial") {})
    }
}
