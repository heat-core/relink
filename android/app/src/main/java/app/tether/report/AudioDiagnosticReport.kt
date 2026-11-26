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
        val report = ProblemReport(if (all) "ReLink diagnostics" else "ReLink audio diagnostics", ProblemReport.created(System.currentTimeMillis()))
        report.section("Setup")
            .kv("Android API", Build.VERSION.SDK_INT)
            .kv("Permission checks passed", permissionsGranted)
            .kv("Phone state permission", app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
            .kv("Answer calls permission", app.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED)
            .kv("Bluetooth connect permission", if (Build.VERSION.SDK_INT >= 31)
                app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED else "not required")
            .kv("Default dialer", app.sims.isDialer())
            .kv("Battery unrestricted", app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName))
            .kv("Wireless debugging paired flag", app.prefs.getBoolean("adb_paired", false))
            .kv("Legacy TCP enabled flag", app.prefs.getBoolean(Adb.PREF_LEGACY, false))
        report.section("ADB connection")
            .kv("ADB transport observed", Adb.observedTransport() ?: "unverified")
            .kv("Wireless", Adb.connectionStatus.tls.name)
            .kv("Legacy TCP", Adb.connectionStatus.legacy.name)
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
            .kv("HELLO received", hello != null)
            .kv("HELLO protocol", hello?.optInt("version"))
            .kv("HELLO accepted", helper.ready)
            .kv("Shell initialization failure", helper.shellInitFailure?.name ?: "none observed")
            .kv("Shell initialization warnings", helper.shellInitWarnings().joinToString { it.name })
        report.section("Audio")
            .kv("Backend", "TCP")
            .kv("Capture", AudioDiagnosticValues.capture(info?.optString("capture")))
            .kv("Injection", AudioDiagnosticValues.injection(info?.optString("inject")))
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
