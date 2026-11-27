package app.tether.link

import java.util.UUID

/** Tracks device effects across replacement links. A retired epoch can never execute again. */
internal class CommandLedger(private val capacity: Int = 1024) {
    private data class Key(val peer: String, val operation: UUID)
    private val entries = HashMap<Key, Entry>()

    @Volatile var epoch = UUID.randomUUID().toString()
        private set

    internal class Entry(val type: String, private var reply: (String) -> Unit) {
        private var latest: String? = null

        @Synchronized fun attach(reply: (String) -> Unit) {
            this.reply = reply
            latest?.let(reply)
        }

        @Synchronized fun publish(result: String) {
            latest = result
            reply(result)
        }
    }

    sealed interface Admission {
        data class Start(val entry: Entry) : Admission
        data object Duplicate : Admission
        data object Retired : Admission
        data object Invalid : Admission
    }

    @Synchronized fun admit(
        peer: String, epoch: String, operation: String, type: String, reply: (String) -> Unit,
    ): Admission {
        if (epoch != this.epoch) return Admission.Retired
        val id = runCatching { UUID.fromString(operation) }.getOrNull()
        if (id == null || id.toString() != operation.lowercase()) return Admission.Invalid
        val key = Key(peer, id)
        entries[key]?.let {
            if (it.type != type) return Admission.Invalid
            it.attach(reply)
            return Admission.Duplicate
        }
        if (entries.size >= capacity) {
            entries.clear()
            this.epoch = UUID.randomUUID().toString()
            return Admission.Retired
        }
        return Admission.Start(Entry(type, reply).also { entries[key] = it })
    }
}
