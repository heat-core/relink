package app.tether.helper

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * Coordinates single-instance ownership of the helper daemon so overlapping launches
 * cannot leave duplicate HelperMain processes competing for audio hardware or sockets.
 */
object HelperLock {
    data class Handle(val channel: FileChannel, val lock: FileLock, val pidFile: File) {
        fun release() {
            runCatching { lock.release() }
            runCatching { channel.close() }
        }
    }

    /**
     * Reads and immediately deletes a one-shot token file written by shell bootstrap.
     * Trims trailing newlines added by shell heredocs/printf.
     */
    fun readAndDeleteToken(path: String): String {
        val file = File(path)
        return try {
            file.readText(Charsets.UTF_8).trimEnd('\r', '\n').also { token ->
                require(token.length == 32 && token.all { it in '0'..'9' || it in 'a'..'f' })
            }
        } finally {
            runCatching { file.delete() }
        }
    }

    /**
     * Acquires an exclusive lock on [lockFile]. When [replace] is true and another instance
     * owns the lock, sends SIGTERM only if [pidFile] names a live process whose cmdline contains
     * `HelperMain` (when readable), then retries acquisition up to [waitMs].
     */
    fun acquire(
        lockFile: File = File("/data/local/tmp/.relink-helper.lock"),
        pidFile: File = File("/data/local/tmp/.relink-helper.pid"),
        replace: Boolean = false,
        ownPid: Long = currentPid(),
        port: Int = 0,
        waitMs: Long = 1800L,
        isHelperProcess: (Long) -> Boolean = ::defaultIsHelperPid,
        terminatePid: (Long) -> Unit = ::defaultTerminatePid
    ): Handle? {
        lockFile.parentFile?.mkdirs()
        val ch = RandomAccessFile(lockFile, "rw").channel
        var lock = runCatching { ch.tryLock() }.getOrNull()
        if (lock == null && replace) {
            val oldPid = runCatching { pidFile.takeIf { it.exists() }?.readText()?.trim()?.lines()?.firstOrNull()?.toLongOrNull() }.getOrNull()
            if (oldPid != null && oldPid > 1L && oldPid != ownPid && isHelperProcess(oldPid)) {
                terminatePid(oldPid)
            }
            val deadline = System.currentTimeMillis() + waitMs
            while (lock == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(60)
                lock = runCatching { ch.tryLock() }.getOrNull()
            }
        }
        if (lock == null) {
            runCatching { ch.close() }
            return null
        }
        runCatching {
            pidFile.writeText("$ownPid\n$port\n${HelperMain.VERSION}\n", Charsets.UTF_8)
        }
        return Handle(ch, lock, pidFile)
    }

    private fun currentPid(): Long = runCatching { android.os.Process.myPid().toLong() }
        .getOrElse {
            runCatching {
                val mx = Class.forName("java.lang.management.ManagementFactory")
                    .getMethod("getRuntimeMXBean").invoke(null)
                (mx.javaClass.getMethod("getName").invoke(mx) as String).substringBefore('@').toLong()
            }.getOrDefault(1L)
        }

    private fun defaultIsHelperPid(pid: Long): Boolean {
        val cmd = File("/proc/$pid/cmdline")
        if (!cmd.canRead()) return false
        val text = runCatching { cmd.readBytes().toString(Charsets.UTF_8) }.getOrDefault("")
        return "HelperMain" in text || "app.tether.helper" in text
    }

    private fun defaultTerminatePid(pid: Long) {
        runCatching { ProcessBuilder("kill", "-TERM", pid.toString()).start().waitFor() }
    }
}
