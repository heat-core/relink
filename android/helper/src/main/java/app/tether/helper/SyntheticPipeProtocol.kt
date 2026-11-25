package app.tether.helper

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/** Synthetic 16 kHz mono PCM-sized frames. No microphone, modem, speaker or BLE is used. */
object SyntheticPipeProtocol {
    const val version = 1
    const val frameCount = 40
    const val frameBytes = 640
    const val intervalMs = 20L
    const val shellUid = 2000
    const val authoritySuffix = ".audio-probe"
    const val openMethod = "open-duplex-v1"
    const val resultMethod = "complete-duplex-v1"

    fun frame(generation: Long, sequence: Int, now: Long): ByteArray = ByteBuffer.allocate(frameBytes)
        .putLong(generation).putInt(sequence).putLong(now).array()

    class Measurement(private val generation: Long) {
        private val latencies = mutableListOf<Long>()
        private var lastSequence = -1
        var rejected = 0
            private set
        val received get() = latencies.size
        val missing get() = frameCount - received
        val p95Micros: Long?
            get() = latencies.sorted().let { if (it.isEmpty()) null else it[(it.size * 95 + 99) / 100 - 1] }

        fun add(packet: ByteArray, now: Long) {
            if (packet.size != frameBytes) {
                rejected++
                return
            }
            val reader = ByteBuffer.wrap(packet)
            val id = reader.long
            val sequence = reader.int
            val sent = reader.long
            if (id != generation || sequence !in 0 until frameCount || sequence <= lastSequence || sent !in 1..now) {
                rejected++
                return
            }
            lastSequence = sequence
            latencies.add(TimeUnit.NANOSECONDS.toMicros(now - sent))
        }
    }
}
