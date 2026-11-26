package app.tether.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HelperLockAndTokenTest {
    @Test fun readAndDeleteTokenRemovesTemporarySecretFile() {
        val dir = Files.createTempDirectory("relink-tok").toFile()
        try {
            val tok = File(dir, ".helper.tok")
            val token = "ab".repeat(16)
            tok.writeText("$token\n", Charsets.UTF_8)
            assertTrue(tok.exists())
            assertEquals(token, HelperLock.readAndDeleteToken(tok.absolutePath))
            assertFalse(tok.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun malformedTokenIsRejectedAndDeleted() {
        val dir = Files.createTempDirectory("relink-invalid-tok").toFile()
        try {
            val tok = File(dir, ".helper.tok")
            for (token in listOf("", "ab".repeat(15), "az".repeat(16), " a".repeat(16))) {
                tok.writeText("$token\n", Charsets.UTF_8)
                assertThrows(IllegalArgumentException::class.java) { HelperLock.readAndDeleteToken(tok.absolutePath) }
                assertFalse(tok.exists())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun lockAllowsSingleOwnerAndRefusesUnverifiedReplacement() {
        val dir = Files.createTempDirectory("relink-lock").toFile()
        try {
            val lockFile = File(dir, "helper.lock")
            val pidFile = File(dir, "helper.pid")
            val first = HelperLock.acquire(lockFile = lockFile, pidFile = pidFile, ownPid = 2222L, port = 19001)!!
            try {
                // Re-acquiring inside the same JVM on a fresh handle may trigger OverlappingFileLockException -> tryLock() returns null.
                val second = HelperLock.acquire(lockFile = lockFile, pidFile = pidFile, replace = false, ownPid = 3333L, port = 19002)
                assertNull(second)

                // Even with replace = true, an unrelated PID (isHelperProcess = false) must never be signaled.
                var killedPid: Long? = null
                val third = HelperLock.acquire(
                    lockFile = lockFile,
                    pidFile = pidFile,
                    replace = true,
                    ownPid = 3333L,
                    port = 19002,
                    waitMs = 120L,
                    isHelperProcess = { false },
                    terminatePid = { killedPid = it },
                )
                assertNull(third)
                assertNull(killedPid)

                // When isHelperProcess confirms the owner PID, replace terminates it and takes over the lock.
                var replacedPid: Long? = null
                val fourth = HelperLock.acquire(
                    lockFile = lockFile,
                    pidFile = pidFile,
                    replace = true,
                    ownPid = 4444L,
                    port = 19003,
                    waitMs = 500L,
                    isHelperProcess = { it == 2222L },
                    terminatePid = { pid ->
                        replacedPid = pid
                        first.release()
                    },
                )
                assertEquals(2222L, replacedPid)
                assertNotNull(fourth)
                fourth!!.release()
            } finally {
                first.release()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
