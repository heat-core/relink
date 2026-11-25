package app.tether.audio

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import app.tether.adb.Adb
import app.tether.helper.SyntheticPipeProtocol as Protocol
import app.tether.helper.SyntheticPipes
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Optional synthetic comparison. Calls stay on the normal TCP backend throughout the probe. */
internal object PipeProbe {
    fun available(context: Context): Boolean =
        context.packageManager.resolveContentProvider(context.packageName + Protocol.authoritySuffix, 0) != null

    private data class Pipes(val capture: ParcelFileDescriptor, val injection: ParcelFileDescriptor) {
        fun close() {
            runCatching { capture.close() }
            runCatching { injection.close() }
        }
    }

    private class Session(ticket: String, generation: Long) {
        val grant = PipeProbeGrant(ticket, generation, SystemClock.elapsedRealtime() + 18_000)
        val appAnchor = Binder()
        val offered = CompletableFuture<Pipes>()
        val result = CompletableFuture<Bundle>()
        var shell: IBinder? = null
        var death: IBinder.DeathRecipient? = null

        fun close() {
            val pipes = if (offered.isCompletedExceptionally) null else offered.getNow(null)
            pipes?.close()
            val recipient = death
            if (recipient != null) runCatching { shell?.unlinkToDeath(recipient, 0) }
        }
    }

    private class BootstrapFailed(val step: app.tether.helper.ShellStartup.Step?) : IllegalStateException()

    @Volatile private var active: Session? = null

    @Synchronized private fun begin(session: Session): Boolean {
        if (active != null) return false
        active = session
        return true
    }

    @Synchronized private fun end(session: Session) {
        if (active === session) active = null
        session.close()
    }

    @Synchronized
    fun receive(method: String, extras: Bundle?, uid: Int): Bundle? {
        val session = active ?: return null
        val args = extras ?: return null
        val ticket = args.getString("ticket")
        val generation = args.getLong("generation")
        val now = SystemClock.elapsedRealtime()
        if (!session.grant.matches(uid, ticket, generation, now)) return null
        val shell = args.getBinder("shell") ?: return null
        if (method == Protocol.resultMethod) {
            if (session.shell != shell) return null
            // Only bounded scalar counters are propagated to the UI.
            session.result.complete(Bundle().apply {
                putInt("received", args.getInt("received", -1).coerceIn(-1, Protocol.frameCount))
                putInt("rejected", args.getInt("rejected", -1).coerceIn(-1, Protocol.frameCount))
                putInt("dropped", args.getInt("dropped", -1).coerceIn(-1, Protocol.frameCount))
                putLong("p95", args.getLong("p95", -1).coerceIn(-1, 18_000_000))
            })
            return Bundle()
        }
        if (method != Protocol.openMethod || !session.grant.consume(uid, ticket, generation, now)) return null
        @Suppress("DEPRECATION")
        val capture = args.getParcelable<ParcelFileDescriptor>("capture") ?: return null
        @Suppress("DEPRECATION")
        val injection = args.getParcelable<ParcelFileDescriptor>("injection")
        if (injection == null) { capture.close(); return null }
        val pipes = Pipes(capture, injection)
        val death = IBinder.DeathRecipient {
            if (session.result.completeExceptionally(android.os.DeadObjectException())) pipes.close()
        }
        try {
            shell.linkToDeath(death, 0)
            session.shell = shell
            session.death = death
            session.offered.complete(pipes)
            return Bundle().apply { putBinder("app", session.appAnchor) }
        } catch (failure: Exception) {
            pipes.close()
            session.offered.completeExceptionally(failure)
            return null
        }
    }

    /** Execute on a worker, never from the UI or a provider thread. */
    fun run(context: Context): String {
        if (!available(context)) return "unavailable"
        if (app.tether.telecom.CallRegistry.active().isNotEmpty()) return "call-active"
        val random = SecureRandom()
        val ticket = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val generation = random.nextLong()
        val session = Session(ticket, generation)
        if (!begin(session)) return "busy"
        val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "audio-pipe-probe").apply { isDaemon = true } }
        var phase = "adb"
        try {
            val adb = Adb.get(context)
            val apk = context.applicationInfo.sourceDir
            require(apk.startsWith("/data/app/") && apk.all { it.isLetterOrDigit() || it in "/_=.+~-" })
            require(context.packageName.all { it.isLetterOrDigit() || it in "._" })
            val launch = pool.submit {
                try {
                    val output = adb.shell("CLASSPATH='$apk' app_process / app.tether.helper.HelperMain pipe-probe " +
                        "${context.packageName} $ticket $generation 2>&1")
                    if (!session.offered.isDone) {
                        val stage = app.tether.helper.ShellStartup.failureFrom(output)
                        session.offered.completeExceptionally(BootstrapFailed(stage))
                    }
                } catch (failure: Exception) {
                    session.offered.completeExceptionally(failure)
                    session.result.completeExceptionally(failure)
                }
            }
            phase = "bootstrap"
            val pair = session.offered.get(12, TimeUnit.SECONDS)
            phase = "duplex"
            val sent = pool.submit<Int> { pair.injection.use { SyntheticPipes.write(it, generation) } }
            val down = pair.capture.use { SyntheticPipes.read(it, generation) }
            val up = session.result.get(5, TimeUnit.SECONDS)
            val pushDrops = sent.get(5, TimeUnit.SECONDS)
            launch.get(2, TimeUnit.SECONDS)
            return "capture=${down.received}/${Protocol.frameCount}, missing=${down.missing}, " +
                "rejected=${down.rejected}, p95_us=${down.p95Micros ?: -1}, writer_drops=${up.getInt("dropped")}; " +
                "injection=${up.getInt("received")}/${Protocol.frameCount}, rejected=${up.getInt("rejected")}, " +
                "p95_us=${up.getLong("p95")}, writer_drops=$pushDrops"
        } catch (failure: Exception) {
            val cause = (failure as? java.util.concurrent.ExecutionException)?.cause ?: failure
            return "$phase: ${cause.javaClass.simpleName}" +
                ((cause as? BootstrapFailed)?.step?.name?.let { " ($it)" } ?: "")
        } finally {
            end(session)
            pool.shutdownNow()
        }
    }
}
