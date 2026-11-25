package app.tether.helper

import android.content.Context
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs as a short-lived shell process, separate from the helper carrying calls. */
internal object ShellPipeProbe {
    fun run(context: Context, packageName: String, ticket: String, generation: Long) {
        check(android.os.Build.VERSION.SDK_INT >= 30)
        check(Process.myUid() == SyntheticPipeProtocol.shellUid)
        val resolver = context.contentResolver
        val uri = Uri.parse("content://" + packageName + SyntheticPipeProtocol.authoritySuffix)
        val processAnchor = Binder()
        fun proof() = Bundle().apply {
            putString("ticket", ticket)
            putLong("generation", generation)
            putBinder("shell", processAnchor)
        }

        val capture = ParcelFileDescriptor.createPipe()
        try {
            val injection = ParcelFileDescriptor.createPipe()
            try {
                val offer = proof().apply {
                    putParcelable("capture", capture[0])
                    putParcelable("injection", injection[1])
                }
                val peer = resolver.call(uri, SyntheticPipeProtocol.openMethod, null, offer)
                    ?.getBinder("app") ?: error("no app binder")
                capture[0].close()
                injection[1].close()
                val pool = Executors.newSingleThreadExecutor()
                val death = IBinder.DeathRecipient {
                    runCatching { capture[1].close() }
                    runCatching { injection[0].close() }
                    pool.shutdownNow()
                }
                try {
                    peer.linkToDeath(death, 0)
                    val push = pool.submit<Int> { capture[1].use { SyntheticPipes.write(it, generation) } }
                    val incoming = SyntheticPipes.read(injection[0], generation)
                    val dropped = push.get(5, TimeUnit.SECONDS)
                    val completion = proof().apply {
                        putInt("received", incoming.received)
                        putInt("rejected", incoming.rejected)
                        putInt("dropped", dropped)
                        incoming.p95Micros?.let { putLong("p95", it) }
                    }
                    resolver.call(uri, SyntheticPipeProtocol.resultMethod, null, completion)
                } finally {
                    runCatching { peer.unlinkToDeath(death, 0) }
                    pool.shutdownNow()
                }
            } finally {
                injection.forEach { runCatching { it.close() } }
            }
        } finally {
            capture.forEach { runCatching { it.close() } }
        }
    }
}
