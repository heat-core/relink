package app.tether.helper

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Linux pipes are nonblocking. Each direction can time out and close independently. */
object SyntheticPipes {
    private fun configure(fd: java.io.FileDescriptor) {
        if (android.os.Build.VERSION.SDK_INT < 30) throw IOException("pipe probe requires Android 11")
        val flags = Os.fcntlInt(fd, OsConstants.F_GETFL, 0)
        Os.fcntlInt(fd, OsConstants.F_SETFL, flags or OsConstants.O_NONBLOCK)
    }

    fun write(descriptor: ParcelFileDescriptor, generation: Long): Int {
        val fd = descriptor.fileDescriptor
        configure(fd)
        var dropped = 0
        val cadence = System.nanoTime()
        repeat(SyntheticPipeProtocol.frameCount) { index ->
            val sendAt = cadence + TimeUnit.MILLISECONDS.toNanos(index * SyntheticPipeProtocol.intervalMs)
            val pause = sendAt - System.nanoTime()
            if (pause > 0) TimeUnit.NANOSECONDS.sleep(pause)
            val packet = SyntheticPipeProtocol.frame(generation, index, System.nanoTime())
            try {
                // A 640-byte write is atomic on an Android pipe. Drop a whole frame if full.
                if (Os.write(fd, packet, 0, packet.size) != packet.size) throw IOException("partial pipe frame")
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.EAGAIN) throw e
                dropped++
            }
        }
        return dropped
    }

    fun read(descriptor: ParcelFileDescriptor, generation: Long): SyntheticPipeProtocol.Measurement {
        val fd = descriptor.fileDescriptor
        configure(fd)
        val data = ByteArray(SyntheticPipeProtocol.frameBytes)
        var offset = 0
        val measurement = SyntheticPipeProtocol.Measurement(generation)
        val event = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
        val events = arrayOf(event)
        val end = android.os.SystemClock.elapsedRealtime() + 3500
        while (measurement.received < SyntheticPipeProtocol.frameCount) {
            val wait = (end - android.os.SystemClock.elapsedRealtime()).toInt()
            if (wait <= 0 || Os.poll(events, wait) == 0) throw TimeoutException("pipe read")
            val count = try {
                Os.read(fd, data, offset, data.size - offset)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN) continue else throw e
            }
            if (count == 0) {
                if (offset != 0) throw EOFException("truncated pipe frame")
                break
            }
            offset += count
            if (offset == data.size) {
                measurement.add(data, System.nanoTime())
                offset = 0
            }
        }
        return measurement
    }
}
