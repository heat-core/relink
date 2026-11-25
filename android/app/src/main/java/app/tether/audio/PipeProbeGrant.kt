package app.tether.audio

import app.tether.helper.SyntheticPipeProtocol
import java.security.MessageDigest

/** Ticket and generation are ephemeral, and expiry uses monotonic time. */
internal class PipeProbeGrant(private val ticket: String, val generation: Long, private val expiresAt: Long) {
    private var opened = false

    @Synchronized fun consume(uid: Int, candidate: String?, id: Long, now: Long): Boolean {
        if (opened || !matches(uid, candidate, id, now)) return false
        opened = true
        return true
    }

    fun matches(uid: Int, candidate: String?, id: Long, now: Long): Boolean =
        uid == SyntheticPipeProtocol.shellUid && id == generation && now < expiresAt &&
            candidate?.length == ticket.length && MessageDigest.isEqual(ticket.toByteArray(), candidate.toByteArray())
}
