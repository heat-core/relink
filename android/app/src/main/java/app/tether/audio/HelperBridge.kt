package app.tether.audio

import android.content.Context
import android.util.Log
import android.os.SystemClock
import java.net.SocketTimeoutException
import app.tether.adb.Adb
import app.tether.helper.HelperMain
import app.tether.helper.ShellStartup
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * App side of the shell-helper link (protocol §6). SELinux does not let the shell domain connect to an
 * app's Unix socket, so the helper (uid 2000) listens on 127.0.0.1:<port> and we connect to it. The
 * helper and we prove to each other that we know the random token passed on its command line (HMAC challenge-
 * response, protocol §6); the token itself never crosses the socket, and a new one is made for every launch.
 */
class HelperBridge(private val ctx: Context) {
    companion object {
        const val TAG = "Tether"
        const val HELLO = HelperGreeting.TYPE; const val START = 2; const val STOP = 3; const val DOWN = 4; const val UP = 5
        const val INFO = 6; const val PROBE = 7; const val QUIT = 8; const val SELFTEST = 9; const val AUTH = 10
        const val NET = 11; const val CHALLENGE = 12; const val PROOF = 14; const val LOCAL = 15; const val CALL = 16; const val DIAG = 17
        private const val PREF_BLOCKED = "audio_blocked_fingerprint"
        /** Largest frame we accept from the helper (a 20 ms PCM frame is 640 bytes; JSON infos are small). */
        const val MAX_FRAME = 1 shl 20

        /** Call-audio level by Android version, see [audioLevel]. */
        fun audioLevelFor(sdk: Int) = when {
            sdk >= 33 -> "full"
            sdk >= 30 -> "fallback"
            else -> "unavailable"
        }
    }

    private val prefs = ctx.getSharedPreferences("helper", Context.MODE_PRIVATE)
    /** The running helper's port: a new one each launch, so an app squatting on it cannot keep call audio off. */
    @Volatile var port: Int = prefs.getInt("port", 0).takeIf { it > 0 } ?: newPort()
        private set

    private fun newPort(): Int = (47000 + SecureRandom().nextInt(2000)).also { prefs.edit().putInt("port", it).apply() }
    /** The running helper's token (set when we launch it; a new one each launch). */
    @Volatile private var token: String = prefs.getString("token", null) ?: newToken()

    private fun newToken(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        .also { prefs.edit().putString("token", it).apply() }

    private class Connection(val socket: Socket, val output: DataOutputStream, val hello: JSONObject)
    @Volatile private var connection: Connection? = null
    val hello: JSONObject? get() = connection?.hello
    @Volatile var lastInfo: JSONObject? = null; private set
    /** Standing-delay figures of the last bridged call (capture backlog, inject queue, drops). */
    @Volatile var lastStats: JSONObject? = null; private set
    /** The helper's uplink report (mic mute, uplink check), from the start INFO or a later `{uplink}` INFO; null between calls. */
    @Volatile var lastUplink: JSONObject? = null; private set
    /** The helper's report on the phone's own mic in this call (INFO `mic`: setting, path, redirect mode); null between calls. */
    @Volatile var lastMic: JSONObject? = null; private set
    /** Every mic report (also the one inside the start INFO). */
    @Volatile var onMic: ((JSONObject) -> Unit)? = null
    @Volatile var onDown: ((ShortArray) -> Unit)? = null
    /** A call's start INFO (methods, mutes, errors). */
    @Volatile var onInfo: ((JSONObject) -> Unit)? = null
    /** A path-watch finding during a call (INFO `path`: `event`, `note`). */
    @Volatile var onPath: ((JSONObject) -> Unit)? = null
    /** A diagnostics snapshot the helper took by itself (INFO `{autodiag, trigger, testLine}`: a dead path, a call's end). */
    @Volatile var onDiag: ((JSONObject) -> Unit)? = null
    /**
     * Id of the last START sent; its start INFO echoes it. [lastInfo] is cleared with every START, so until this call's
     * paths are open (the helper waits for the call's audio mode) nothing of the previous call is taken for this one's.
     */
    @Volatile var startId = 0; private set
    /** Every uplink report (also the one inside the start INFO). */
    @Volatile var onUplink: ((JSONObject) -> Unit)? = null
    @Volatile var onChange: (() -> Unit)? = null
    @Volatile private var pending: Pair<String, CompletableFuture<JSONObject>>? = null

    @Volatile internal var health = HelperHealth()
        private set
    @Volatile internal var adbTransport = "unknown"
        private set
    @Volatile internal var shellInitFailure: ShellStartup.Step? = null
        private set
    internal fun shellInitWarnings(): List<ShellStartup.Step> {
        val warnings = hello?.optJSONArray("startupWarnings") ?: return emptyList()
        return ShellStartup.Step.entries.filter { step -> (0 until minOf(warnings.length(), 16)).any { warnings.optString(it) == step.name } }
    }

    @Volatile internal var processConfirmed = false
        private set

    private fun stage(stage: HelperStage, attempt: Int = health.attempt, fault: HelperFault = HelperFault.NONE) {
        val updated = HelperHealth(stage, attempt, fault)
        if (updated == health) return
        health = updated
        Log.i("TetherAudioDiag", updated.line())
        onChange?.invoke()
    }

    val connected get() = connection != null
    val ready get() = connection?.let { compatible(it.hello) } == true

    /** Kept for symmetry with the service start-up; tries to attach to an already running helper. */
    fun listen() {
        Thread({ tryConnect() }, "helper-connect").start()
    }

    @Synchronized fun tryConnect(): Boolean {
        if (connected) return true
        val socket = Socket()
        var handshake = HelperStage.CONNECTING
        try {
            socket.connect(InetSocketAddress("127.0.0.1", port), 500)
            socket.tcpNoDelay = true
            socket.soTimeout = 1500
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            handshake = HelperStage.AUTHENTICATING
            stage(handshake)
            val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            if (!app.tether.helper.HelperAuth.appAuthenticate(input, output, token, nonce)) {
                stage(handshake, fault = HelperFault.AUTH_REJECTED)
                return false
            }
            handshake = HelperStage.WAITING_HELLO
            stage(handshake)
            socket.soTimeout = 2500
            val greeting = HelperGreeting.read(input)
            socket.soTimeout = 0
            val ready = Connection(socket, output, greeting)
            connection = ready
            processConfirmed = true
            if (compatible(greeting)) stage(HelperStage.READY)
            else stage(HelperStage.FAILED, fault = HelperFault.INCOMPATIBLE)
            Thread({
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) }
                readLoop(ready, input)
            }, "helper-rx").start()
            return true
        } catch (e: Exception) {
            val fault = when (handshake) {
                HelperStage.CONNECTING -> HelperFault.SOCKET_UNREACHABLE
                HelperStage.AUTHENTICATING -> if (e is SocketTimeoutException) HelperFault.AUTH_TIMEOUT else HelperFault.AUTH_REJECTED
                else -> if (e is SocketTimeoutException) HelperFault.HELLO_TIMEOUT else HelperFault.HELLO_INVALID
            }
            if (launching.get() || handshake != HelperStage.CONNECTING) stage(handshake, fault = fault)
            return false
        } finally {
            if (connection?.socket !== socket) runCatching { socket.close() }
        }
    }

    private fun readLoop(c: Connection, input: DataInputStream) {
        try {
            Log.i(TAG, "helper connected: ${c.hello}")
            onChange?.invoke()
            while (true) {
                val type = input.readUnsignedByte()
                val len = input.readInt()
                if (len !in 0..MAX_FRAME) throw java.io.IOException("helper frame of $len bytes")
                val p = ByteArray(len).also { input.readFully(it) }
                if (connection !== c) return
                when (type) {
                    DOWN -> {
                        val s = ShortArray(len / 2) { (p[2 * it].toInt() and 0xff or (p[2 * it + 1].toInt() shl 8)).toShort() }
                        onDown?.invoke(s)
                    }
                    INFO -> {
                        val o = JSONObject(String(p))
                        val pend = pending
                        if (o.has("autodiag")) {
                            Log.i(TAG, "helper diagnostics snapshot: ${o.optString("trigger")}")
                            onDiag?.invoke(o)
                        } else if (pend != null && o.has(pend.first)) {
                            pending = null
                            pend.second.complete(o.getJSONObject(pend.first))
                        } else if (o.has("stats")) {
                            lastStats = o.getJSONObject("stats")
                            lastUplink = null; lastMic = null
                            Log.i(TAG, "helper call-audio stats: $lastStats")
                        } else if (o.has("capture") || o.has("inject")) {
                            if (o.has("id") && o.optInt("id", -1) != startId) { Log.i(TAG, "start info of an older start dropped"); continue }
                            lastInfo = o
                            lastUplink = o.optJSONObject("uplink")
                            lastMic = o.optJSONObject("mic")
                            Log.i(TAG, "helper info: $o")
                            onInfo?.invoke(o)
                            lastUplink?.let { u -> onUplink?.invoke(u) }
                            lastMic?.let { m -> onMic?.invoke(m) }
                            onChange?.invoke()
                        } else if (o.has("path")) {
                            o.optJSONObject("path")?.let { pth -> Log.i(TAG, "helper path: $pth"); onPath?.invoke(pth) }
                        } else if (o.has("uplink") || o.has("mic")) {
                            o.optJSONObject("uplink")?.let { u -> lastUplink = u; Log.i(TAG, "helper uplink: $u"); onUplink?.invoke(u) }
                            o.optJSONObject("mic")?.let { m -> lastMic = m; Log.i(TAG, "helper mic: $m"); onMic?.invoke(m) }
                            onChange?.invoke()
                        } else {
                            // A reply that came after its request timed out: never mistaken for the audio methods.
                            Log.i(TAG, "late helper reply dropped: ${o.keys().asSequence().toList()}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "helper disconnected: ${e.message}")
        } finally {
            detach(c)
            onChange?.invoke()
        }
    }

    @Synchronized private fun detach(c: Connection) {
        if (connection === c) {
            connection = null
            lastInfo = null
            lastUplink = null
            lastMic = null
            pending?.second?.completeExceptionally(java.io.IOException("helper gone"))
            pending = null
            processConfirmed = false
            if (health.stage == HelperStage.READY) stage(HelperStage.STOPPED, fault = HelperFault.DISCONNECTED)
        }
        runCatching { c.socket.close() }
    }

    private fun send(type: Int, payload: ByteArray = ByteArray(0), forStart: Int? = null): Boolean {
        val c = connection ?: return false
        if (type != QUIT && !compatible(c.hello)) return false
        return try {
            synchronized(c.output) {
                if (c !== connection || (forStart != null && forStart != startId)) return false
                c.output.writeByte(type)
                c.output.writeInt(payload.size)
                c.output.write(payload)
                c.output.flush()
            }
            true
        } catch (e: Exception) {
            detach(c)
            false
        }
    }

    /**
     * [testLine]: the call is Tether's own test line (no network behind it): the helper plays the far end's listener.
     * [mic]: the phone-mic setting (MicPath.Setting: auto, mute, open); [micMute]: with "mute", whether to mute this
     * call (see LocalMutes); [micCheck]: then watch the uplink; [redirect]: what this phone remembers of the redirect modes.
     */
    fun start(rate: Int, capture: String = "auto", inject: String = "auto", injectMs: Int = 40, testLine: Boolean = false,
              mic: String = "auto", micMute: Boolean = false, micCheck: Boolean = true, redirect: JSONObject = JSONObject(),
              muteMemory: String? = null, active: Boolean = true, skipCapture: String? = null,
              skipInjection: String? = null): Boolean {
        val id = ++startId
        lastInfo = null; lastStats = null; lastUplink = null; lastMic = null
        return send(START, JSONObject().put("id", id).put("rate", rate).put("capture", capture).put("inject", inject).put("injectMs", injectMs)
            .put("testLine", testLine).put("mic", mic).put("micMute", micMute).put("micCheck", micCheck).put("redirect", redirect)
            .put("muteMemory", muteMemory ?: JSONObject.NULL).put("active", active)
            .put("skipCapture", skipCapture).put("skipInjection", skipInjection)
            .toString().toByteArray())
    }

    /** The phone-mic setting changed during a bridged call. */
    fun local(mic: String, micMute: Boolean, micCheck: Boolean, redirect: JSONObject = JSONObject(), muteMemory: String? = null) =
        send(LOCAL, JSONObject().put("mic", mic).put("micMute", micMute).put("micCheck", micCheck).put("redirect", redirect)
            .put("muteMemory", muteMemory ?: JSONObject.NULL).toString().toByteArray())

    /** The bridged call is now active (connected) or no longer: the helper passes the uplink only while it is. */
    fun call(active: Boolean) = send(CALL, JSONObject().put("active", active).toString().toByteArray())

    fun stop() = send(STOP)

    fun up(pcm: ShortArray, forStart: Int): Boolean {
        val b = ByteArray(pcm.size * 2)
        for (i in pcm.indices) { b[2 * i] = pcm[i].toInt().toByte(); b[2 * i + 1] = (pcm[i].toInt() shr 8).toByte() }
        return send(UP, b, forStart)
    }

    fun quit() = send(QUIT)

    /**
     * Network switches (mobile data, hotspot, Bluetooth tethering). [what] null only reads the state.
     * Returns {"data","hotspot","bt"[,"err"]} or null when the helper is not reachable.
     */
    private val requestLock = Any()

    fun net(what: String? = null, on: Boolean = false, sub: Int? = null, extra: JSONObject? = null): JSONObject? {
        // Its own lock, not the bridge's monitor: a slow switch must never hold up tryConnect / audio control.
        synchronized(requestLock) {
            val f = CompletableFuture<JSONObject>()
            pending = "net" to f
            val req = (extra ?: JSONObject()).apply { if (what != null) put("what", what).put("on", on); sub?.let { put("sub", it) } }
            if (!send(NET, req.toString().toByteArray())) { pending = null; return null }
            return try { f.get(25, TimeUnit.SECONDS) } catch (e: Exception) { pending = null; null }
        }
    }

    /** Sends PROBE / SELFTEST / DIAG (with [body], e.g. `{methods, micMute}`) and waits for the matching INFO. */
    fun request(kind: String, timeoutSec: Long = 20, body: JSONObject? = null): JSONObject? {
        synchronized(requestLock) {
            val f = CompletableFuture<JSONObject>()
            pending = kind to f
            val type = when (kind) { "probe" -> PROBE; "diag" -> DIAG; else -> SELFTEST }
            if (!send(type, body?.toString()?.toByteArray() ?: ByteArray(0))) { pending = null; return null }
            return try { f.get(timeoutSec, TimeUnit.SECONDS) } catch (e: Exception) { pending = null; null }
        }
    }

    /**
     * Shell command that starts the helper detached from the ADB stream. The subshell + setsid detach it
     * into its own session; the trailing sleep keeps the ADB shell (and its process group) alive until
     * that has happened, otherwise adbd can kill the child while the stream closes.
     */
    fun launchCommand(): String {
        val apk = ctx.applicationInfo.sourceDir
        val logFile = "/data/local/tmp/${ctx.packageName}-helper.log"
        // Processes are scoped by their authenticated port and idle timeout, never by a global pkill.
        return "(umask 077; M=HelperMain; setsid sh -c \"CLASSPATH=$apk exec app_process / app.tether.helper.\$M serve $port $token\" " +
            "</dev/null >$logFile 2>&1 & sleep 1; if kill -0 \$! 2>/dev/null; then echo process-alive; " +
            "else echo process-exited; grep -E '^shell-init-failed:[A-Z_]+' $logFile | tail -1; fi)"
    }

    /** A helper from an older install or with a different protocol must be replaced. */
    val isStale: Boolean get() = hello?.let { !compatible(it) } ?: false

    private fun compatible(greeting: JSONObject) =
        HelperGreeting.compatible(greeting, ctx.applicationInfo.sourceDir, HelperMain.VERSION)

    private val launching = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pool = java.util.concurrent.Executors.newCachedThreadPool { r -> Thread(r, "helper-launch").apply { isDaemon = true } }
    @Volatile private var nextAttemptAt = 0L
    private var failures = 0

    fun kick(manual: Boolean = false) {
        if (ready || (!manual && SystemClock.elapsedRealtime() < nextAttemptAt)) return
        if (!launching.compareAndSet(false, true)) return
        pool.execute {
            try {
                val fault = launch()
                if (fault == HelperFault.NONE) {
                    failures = 0
                    nextAttemptAt = 0
                } else {
                    failures++
                    nextAttemptAt = SystemClock.elapsedRealtime() + minOf(30_000L, 3000L * failures)
                    stage(HelperStage.FAILED, fault = fault)
                }
            } catch (failure: Exception) {
                nextAttemptAt = SystemClock.elapsedRealtime() + 5000
                stage(HelperStage.FAILED, fault = HelperFault.classify(failure))
            } finally {
                launching.set(false)
                onChange?.invoke()
            }
        }
    }

    /** Wait for the shared launch. The background launch can finish after the caller's deadline. */
    fun ensureRunning(waitMs: Long = 25_000): String? {
        if (ready) return null
        kick(manual = true)
        val end = SystemClock.elapsedRealtime() + waitMs
        while (launching.get() && !ready && SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        return when {
            ready -> null
            launching.get() -> "Helper is still starting (${health.stage.name})"
            else -> "Helper failed (${health.fault.name})"
        }
    }

    private fun launch(): HelperFault {
        if (connected || tryConnect()) {
            val c = connection
            if (c != null && compatible(c.hello)) return HelperFault.NONE
            if (c != null) { quit(); detach(c) }
        }
        synchronized(this) {
            if (ready) return HelperFault.NONE
            token = newToken()
            port = newPort()
        }
        var fault = HelperFault.ADB_UNREACHABLE
        for (attempt in 1..2) {
            processConfirmed = false
            shellInitFailure = null
            adbTransport = "unknown"
            stage(HelperStage.CHECKING_ADB, attempt)
            fault = HelperFault.ADB_UNREACHABLE
            val adbCheck = pool.submit<String?> {
                val adb = Adb.get(ctx)
                if (adb.ensureConnected()) adb.transport else null
            }
            val via = try {
                adbCheck.get(23, TimeUnit.SECONDS)
            } catch (_: java.util.concurrent.TimeoutException) {
                adbCheck.cancel(true)
                fault = HelperFault.ADB_TIMEOUT
                null
            } catch (failure: Exception) {
                fault = HelperFault.classify(failure)
                null
            }
            if (via != null) {
                adbTransport = via
                Log.i("TetherAudioDiag", if (via == "tls") "adb=tls" else "adb=legacy")
                stage(HelperStage.STARTING_PROCESS, attempt)
                val command = launchCommand()
                val process = pool.submit<String> { Adb.get(ctx).shell(command) }
                try {
                    val output = process.get(9, TimeUnit.SECONDS)
                    shellInitFailure = ShellStartup.failureFrom(output)
                    processConfirmed = output.lineSequence().any { it.trim() == "process-alive" }
                    Log.i("TetherAudioDiag", if (processConfirmed) "process=observed" else "process=unverified")
                    stage(HelperStage.CONNECTING, attempt)
                    val deadline = SystemClock.elapsedRealtime() + 4500
                    var observed = if (processConfirmed) HelperFault.SOCKET_UNREACHABLE else HelperFault.PROCESS_EXITED
                    do {
                        if (tryConnect() && ready) return HelperFault.NONE
                        val currentFault = health.fault
                        if (currentFault != HelperFault.NONE && currentFault != HelperFault.SOCKET_UNREACHABLE) observed = currentFault
                        connection?.let { c -> quit(); detach(c) }
                        Thread.sleep(200)
                    } while (SystemClock.elapsedRealtime() < deadline)
                    fault = observed
                } catch (_: java.util.concurrent.TimeoutException) {
                    process.cancel(true)
                    fault = HelperFault.PROCESS_TIMEOUT
                } catch (failure: Exception) {
                    fault = HelperFault.classify(failure)
                }
            }
            Adb.reset()
            if (attempt == 1) {
                stage(HelperStage.RETRY_WAIT, attempt, fault)
                Thread.sleep(1500)
            }
        }
        return fault
    }

    fun state(): JSONObject {
        val info = lastInfo
        val u = lastUplink
        return JSONObject()
            .put("state", if (ready) "running" else "stopped")
            .apply { info?.optString("inject")?.takeIf { it.isNotEmpty() && it != "null" }?.let { put("method", it) } }
            .apply { info?.optString("capture")?.takeIf { it.isNotEmpty() && it != "null" }?.let { put("capture", it) } }
            .put("up", info?.optString("inject")?.let { it.isNotEmpty() && it != "null" } ?: false)
            .put("down", info?.optString("capture")?.let { it.isNotEmpty() && it != "null" } ?: false)
            .put("audio", audioLevel())
            // A redirect mode took the call off the phone: fully silent. After the uplink check undid the mic mute (or a
            // redirect mode was undone), the phone is no longer fully silent.
            .apply { info?.optString("silent")?.takeIf { it.isNotEmpty() }?.let { put("silent", silentNow(it, u, lastMic)) } }
    }

    /** Status `silent` from the start INFO's [atStart] and the latest uplink / mic reports. */
    private fun silentNow(atStart: String, u: JSONObject?, mic: JSONObject?): String = when {
        atStart == "none" && mic?.optString("path") != "off" -> "none"
        mic?.optString("path") == "off" -> "full"
        u?.optBoolean("micMuted", false) == true -> "full"
        else -> if (atStart == "none") "none" else "earpiece"
    }

    /**
     * "full" on Android 13+ (interception API), "fallback" on 11-12 (voice-call capture, incall-music inject),
     * "unavailable" on 8-10, "blocked" when this phone's software gave Tether no call audio in a real call
     * ([audioBlocked]).
     */
    fun audioLevel(): String = if (audioBlocked()) "blocked" else audioLevelFor(android.os.Build.VERSION.SDK_INT)

    /**
     * This firmware gives Tether no call audio (the downlink capture read only zeros in a real call, issue #13):
     * remembered by build fingerprint, so a system update or a self-test that hears call audio clears it.
     */
    fun audioBlocked(): Boolean = app.tether.link.AudioOwner.blocked(prefs.getString(PREF_BLOCKED, null), blockedKey())
    fun rememberAudioBlocked() = prefs.edit().putString(PREF_BLOCKED, blockedKey()).apply()

    /** The firmware and this Tether build: a system update or a new Tether version tries call audio again (a fault
     *  of Tether's own could look like a phone that gives none, as on the A73 before 1.2.4, issue #13). */
    private fun blockedKey(): String {
        val code = runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        }.getOrDefault(0L)
        return "${android.os.Build.FINGERPRINT}|$code"
    }
    fun forgetAudioBlocked() { if (prefs.contains(PREF_BLOCKED)) prefs.edit().remove(PREF_BLOCKED).apply() }
}
