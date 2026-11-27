package app.tether.report

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import app.tether.TetherApp
import app.tether.adb.Adb
import app.tether.audio.AudioDiagnosticValues
import app.tether.core.ProblemReport
import app.tether.link.LinkService

/** Read-only, allowlisted report. No Telecom/SMS/link data and no raw logs, exception messages or PCM. */
internal object AudioDiagnosticReport {
    private val tcpCounters = listOf(
        "openWaitMs", "captureBacklogMs", "captureBacklogMaxMs", "captureDroppedMs",
        "injectQueuedMs", "injectQueuedMaxMs", "injectUnderrunsFirstSecond",
        "uplinkFrames", "uplinkDroppedFrames", "uplinkLateCutMs"
    )
    fun build(app: TetherApp, permissionsGranted: Boolean, all: Boolean = false): ProblemReport {
        val helper = app.hub.helper
        val info = helper.lastInfo
        val hello = helper.hello
        val snapshot = helper.health
        val state = helper.snapshot(
            transportConnected = app.hub.conn?.isUp == true,
            localFallback = app.hub.audio.forcedLocal,
            mediaActive = app.hub.audio.isMediaAcknowledged,
        )
        val adbStatus = Adb.snapshotStatus(app)
        val report = ProblemReport(if (all) "ReLink diagnostics" else "ReLink audio diagnostics", ProblemReport.created(System.currentTimeMillis()))
        report.section("Pipeline")
            .kv("Pipeline state", state.pipelineState().name)
            .kv("Transport connected", state.transportConnected)
            .kv("Automated ADB bootstrap allowed", state.adbBootstrapAllowed)
            .kv("Helper authenticated", state.helperAuthenticated)
            .kv("Audio backend initialized", state.audioBackendReady)
            .kv("Media route established", state.mediaRouteEstablished)
            .kv("Call audio paths initialized", state.callAudioOperating)
            .kv("Call returned to Android", state.localFallback)
        report.section("Setup")
            .kv("Android API", Build.VERSION.SDK_INT)
            .kv("Permission checks passed", permissionsGranted)
            .kv("Phone state permission", app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
            .kv("Answer calls permission", app.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED)
            .kv("Bluetooth connect permission", if (Build.VERSION.SDK_INT >= 31)
                app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED else "not required")
            .kv("Default dialer", app.sims.isDialer())
            .kv("Battery unrestricted", app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName))
            .kv("Wireless debugging paired flag", adbStatus.paired)
            .kv("Legacy TCP enabled flag", adbStatus.tcpConfigured)
        report.section("ADB connection")
            .kv("ADB paired", adbStatus.paired)
            .kv("ADB connected", adbStatus.anyConnected)
            .kv("ADB transport observed", Adb.observedTransport() ?: "unverified")
            .kv("Wireless", adbStatus.tls.name)
            .kv("Legacy TCP", adbStatus.legacy.name)
            .kv("TCP configured", adbStatus.tcpConfigured)
            .kv("TCP verified", adbStatus.tcpVerified)
            .kv("TCP port", adbStatus.tcpPort)
            .kv("Wireless enabled setting", runCatching {
                android.provider.Settings.Global.getInt(app.contentResolver, "adb_wifi_enabled", -1)
            }.getOrDefault(-1))
        if (all) {
            val peripheral = LinkService.running?.peripheral
            report.section("Link diagnostics")
                .kv("Bluetooth enabled", runCatching { peripheral?.enabled }.getOrNull())
                .kv("Advertising", peripheral?.advertising)
                .kv("Authenticated link", app.hub.conn?.isUp == true)
            report.section("Pairing diagnostics")
                .kv("Host pairing window", if (app.keys.currentToken() != null) "open" else "closed")
                .kv("Last pairing event", app.keys.pairingOutcome)
        }
        report.section("Helper")
            .kv("Stage", snapshot.stage.name)
            .kv("Attempt", snapshot.attempt)
            .kv("Failure category", snapshot.fault.name)
            .kv("Process observed", helper.processConfirmed)
            .kv("Helper process running", state.helperProcessRunning)
            .kv("Helper authenticated", state.helperAuthenticated)
            .kv("Recovery attempts", state.recoveryAttempts)
            .kv("Last disconnect reason", state.lastDisconnectReason ?: "none")
            .kv("HELLO received", hello != null)
            .kv("HELLO protocol", hello?.optInt("version"))
            .kv("HELLO accepted", helper.ready)
            .kv("Shell initialization failure", helper.shellInitFailure?.name ?: "none observed")
            .kv("Shell initialization warnings", helper.shellInitWarnings().joinToString { it.name })
        report.section("Audio")
            .kv("Backend", "TCP")
            .kv("Audio backend ready", state.audioBackendReady)
            .kv("Capture available", state.captureAvailable)
            .kv("Injection available", state.injectionAvailable)
            .kv("Capture", state.captureBackend)
            .kv("Injection", state.injectionBackend)
            .kv("Call returned to Android", app.hub.audio.forcedLocal)
            .kv("Self-test", app.hub.selftestStatus)
        helper.lastStats?.let { stats ->
            val transport = report.section("TCP path measurements")
            for (key in tcpCounters) {
                if (stats.has(key)) transport.kv(key, stats.optLong(key, -1).coerceIn(-1, Int.MAX_VALUE.toLong()))
            }
        }
        val methods = app.hub.lastSelftest?.optJSONArray("methods")
        if (methods != null) {
            val results = report.section("Self-test methods")
            for (i in 0 until methods.length()) {
                val row = methods.optJSONObject(i) ?: continue
                val name = row.optString("name")
                val direction = name.substringBefore(':')
                val method = when (direction) {
                    "capture" -> AudioDiagnosticValues.capture(name.substringAfter(':'))
                    "inject" -> AudioDiagnosticValues.injection(name.substringAfter(':'))
                    else -> continue
                }
                if (method != "unverified") {
                    results.kv("$direction $method", if (row.optBoolean("ok")) "measured signal" else "not verified")
                    if (row.has("routedToCall")) results.kv("$direction $method telephony route", row.optBoolean("routedToCall"))
                }
            }
        }
        return report
    }
}
