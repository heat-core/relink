package app.tether.helper

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Bounds complete socket lifetimes, including work that has timed out but is still unwinding. */
internal class HelperConnections(limit: Int = 4, private val authDeadlineMs: Long = 2000) : AutoCloseable {
    private val sessions = ThreadPoolExecutor(limit, limit, 0, TimeUnit.MILLISECONDS, SynchronousQueue(), { r ->
        Thread(r, "helper-session").apply { isDaemon = true }
    })
    private val deadlines = ScheduledThreadPoolExecutor(1) { r ->
        Thread(r, "helper-auth-deadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    val idle: Boolean get() = sessions.activeCount == 0

    fun start(closeSocket: () -> Unit, handle: (markAuthenticated: () -> Boolean) -> Unit) {
        try {
            sessions.execute {
                val pending = AtomicBoolean(true)
                try {
                    val deadline = deadlines.schedule({
                        if (pending.compareAndSet(true, false)) closeSocket()
                    }, authDeadlineMs, TimeUnit.MILLISECONDS)
                    try {
                        handle {
                            val accepted = pending.compareAndSet(true, false)
                            deadline.cancel(false)
                            accepted
                        }
                    } finally {
                        pending.set(false)
                        deadline.cancel(false)
                    }
                } finally {
                    closeSocket()
                }
            }
        } catch (_: RejectedExecutionException) {
            closeSocket()
        }
    }

    override fun close() {
        sessions.shutdown()
        deadlines.shutdown()
    }
}
