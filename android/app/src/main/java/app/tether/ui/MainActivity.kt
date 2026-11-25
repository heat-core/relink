package app.tether.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.tether.R
import app.tether.TetherApp
import app.tether.adb.Adb
import app.tether.adb.ConnectOrder
import app.tether.core.PairingCode
import app.tether.helper.CallAudio
import app.tether.helper.LocalMutes
import app.tether.helper.MicPath
import app.tether.link.LinkLog
import app.tether.link.LinkService
import app.tether.report.AudioDiagnosticReport
import app.tether.audio.HelperStage
import app.tether.audio.PipeProbe
import app.tether.audio.HelperFault
import app.tether.report.ReportUi
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

class MainActivity : Activity() {
    private lateinit var app: TetherApp
    private lateinit var root: LinearLayout
    private lateinit var scroller: ScrollView
    private enum class Page(val label: Int) {
        HOME(R.string.beta_home), LINK(R.string.section_link), AUDIO(R.string.section_audio), SETUP(R.string.section_setup)
    }
    private var page = Page.HOME
    private val main = Handler(Looper.getMainLooper())
    private var qrUntil = 0L
    private var qrBitmap: Bitmap? = null
    private var qrLegacy = false
    private val redraw = Runnable { render() }
    private val listener: () -> Unit = {
        main.removeCallbacks(redraw)
        main.postDelayed(redraw, 80)
    }

    /** Runtime permissions this Android version knows: Bluetooth ones from 12, notifications from 13. */
    private val perms = (
        (if (android.os.Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE) else emptyList()) +
        (if (android.os.Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()) + listOf(
        Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS,
    )).toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as TetherApp
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        page = Page.entries.firstOrNull { it.name == savedInstanceState?.getString("page") } ?: Page.HOME
        scroller = ScrollView(this).apply { addView(root); setBackgroundColor(color(R.color.background)); isFillViewport = true }
        setContentView(scroller)
        fitHostWindow(scroller)
        if (missingPerms().isEmpty()) LinkService.start(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        app.hub.addListener(listener)
        render()
        tick()
    }

    override fun onPause() {
        app.hub.removeListener(listener)
        main.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (missingPerms().isEmpty()) LinkService.start(this)
        render()
    }

    private fun tick() {
        main.postDelayed({ if (page == Page.LINK && qrUntil > 0) render(); tick() }, 1000)
    }

    private fun missingPerms() = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    private fun navigate(destination: Page) {
        if (page == destination) return
        page = destination
        render()
        scroller.scrollTo(0, 0)
    }

    private fun render() {
        root.removeAllViews()
        root.addView(label(getString(R.string.app_name), R.color.muted, 14f))
        root.addView(label(getString(page.label), R.color.text, 30f).apply {
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            setTypeface(typeface, Typeface.BOLD)
        })
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        for (group in Page.entries.chunked(2)) {
            val row = LinearLayout(this)
            for (item in group) {
                row.addView(button(getString(item.label), secondary = item != page) { navigate(item) }.apply {
                    isSelected = item == page
                    isEnabled = item != page
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            }
            navigation.addView(row)
        }
        navigation.setPadding(0, 0, 0, dp(12))
        root.addView(navigation)
        when (page) {
            Page.HOME -> renderHome()
            Page.LINK -> renderLink()
            Page.SETUP -> renderSetup()
            Page.AUDIO -> renderAudio()
        }
    }

    private fun renderHome() {
        val connected = app.hub.conn?.isUp == true
        val link = card(getString(R.string.beta_connection))
        val linkText = when {
            LinkService.running?.peripheral?.enabled == false -> R.string.state_bt_off
            connected -> R.string.state_connected
            !app.keys.isPaired -> R.string.state_unpaired
            else -> R.string.state_waiting
        }
        link.addView(label(getString(linkText), if (connected) R.color.ok else R.color.warn, 19f))
        link.addView(button(getString(if (app.keys.isPaired) R.string.beta_manage_devices else R.string.pair_iphone), secondary = true) {
            navigate(Page.LINK)
        })

        val setup = card(getString(R.string.section_setup))
        val needsSetup = missingPerms().isNotEmpty() || !app.sims.isDialer() || !Adb.hasSecureSettings(this)
        setup.addView(label(getString(if (needsSetup) R.string.beta_setup_pending else R.string.beta_setup_complete), R.color.text, 16f))
        if (needsSetup) setup.addView(button(getString(R.string.beta_continue_setup)) { navigate(Page.SETUP) })

        val audio = card(getString(R.string.section_audio))
        if (PipeProbe.available(this)) {
            audio.addView(button(getString(R.string.beta3_transport), secondary = true) { chooseTransportProbe() })
        }
        val audioText = when {
            app.hub.audio.forcedLocal -> R.string.audio_on_android
            app.hub.audio.helperStatus().optString("state") == "running" -> R.string.helper_phase_ready
            else -> R.string.helper_phase_stopped
        }
        audio.addView(label(getString(audioText), R.color.text, 16f))
        audio.addView(button(getString(R.string.beta_open_diagnostics), secondary = true) { navigate(Page.AUDIO) })
    }

    private fun renderLink() {
        // Link
        val link = card(getString(R.string.section_link))
        val svc = LinkService.running
        val conn = app.hub.conn
        val (state, stateColor) = when {
            svc?.peripheral?.enabled == false -> getString(R.string.state_bt_off) to R.color.bad
            conn?.isUp == true -> getString(R.string.state_connected) + " · " + conn.transport.label to R.color.ok
            !app.keys.isPaired -> getString(R.string.state_unpaired) to R.color.warn
            else -> getString(R.string.state_waiting) to R.color.muted
        }
        link.addView(label(state, stateColor, 18f))
        // Paired devices: connected ones are marked; each can be removed on its own.
        val live = app.hub.targets()
        for (cl in app.keys.centrals()) {
            val on = live.any { it.peerKey.contentEquals(cl.key) }
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(label((if (on) "● " else "○ ") + cl.name + (if (on) " · " + getString(R.string.state_connected) else ""),
                if (on) R.color.ok else R.color.muted).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            r.addView(button(getString(R.string.unpair), secondary = true) {
                AlertDialog.Builder(this).setMessage(getString(R.string.unpair_confirm_named, cl.name))
                    .setPositiveButton(R.string.unpair) { _, _ ->
                        app.keys.removeCentral(cl.key)
                        app.hub.conns.filter { it.peerKey.contentEquals(cl.key) }.forEach { it.close("unpaired") }
                        render()
                    }
                    .setNegativeButton(android.R.string.cancel, null).show()
            })
            link.addView(r)
        }
        val remaining = (qrUntil - System.currentTimeMillis()) / 1000
        if (qrUntil > 0 && remaining > 0 && app.keys.currentToken() != null) {
            link.addView(label(getString(if (qrLegacy) R.string.pair_hint_old else R.string.pair_hint), R.color.text))
            link.addView(ImageView(this).apply {
                setImageBitmap(qrBitmap); adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(dp(260), dp(260)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            })
            link.addView(label(getString(R.string.pair_expires, remaining), R.color.muted).apply { gravity = Gravity.CENTER })
            // Cellular 1.1 scans only the tether1: form; the same code (same token) in that form.
            link.addView(button(getString(if (qrLegacy) R.string.pair_new_code else R.string.pair_old_app), secondary = true) {
                qrLegacy = !qrLegacy; drawQr(app.keys.openPairingCurrent() ?: return@button); render()
            })
        } else if (qrUntil > 0) {
            qrUntil = 0
            qrBitmap = null
            qrLegacy = false
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(getString(R.string.pair_iphone)) { showQr() })
        link.addView(row)

    }

    private fun renderSetup() {
        val setup = card(getString(R.string.section_setup))
        setup.addView(label(getString(R.string.setup_intro), R.color.muted, 13f))
        setup.addView(step(getString(R.string.step_n, 1, getString(R.string.step_permissions)), missingPerms().isEmpty(), getString(R.string.action_grant),
            help = getString(R.string.help_permissions)) {
            requestPermissions(missingPerms(), 1)
        })
        setup.addView(step(getString(R.string.step_n, 2, getString(R.string.step_dialer)), app.sims.isDialer(), getString(R.string.action_set),
            help = getString(R.string.help_dialer)) {
            startActivityForResult(if (android.os.Build.VERSION.SDK_INT >= 29) getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
                else Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName), 2)
        })
        val pm = getSystemService(PowerManager::class.java)
        setup.addView(step(getString(R.string.step_n, 3, getString(R.string.step_battery)), pm.isIgnoringBatteryOptimizations(packageName), getString(R.string.action_set),
            help = getString(R.string.help_battery)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        val adbDone = Adb.hasSecureSettings(this) && app.prefs.getBoolean("adb_paired", false)
        // Wireless debugging exists from Android 11; before that only TCP adb (below) can start the helper.
        if (android.os.Build.VERSION.SDK_INT >= 30) setup.addView(step(getString(R.string.step_n, 4, getString(R.string.step_adb)), adbDone, getString(R.string.action_pair),
            help = getString(R.string.help_adb)) {
            Adb.showPairingNotification(this)
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        })

        // Legacy TCP adb: opt-in, for Android 10 (no Wireless debugging) or a phone without a trusted Wi-Fi network.
        val tcpOn = app.prefs.getBoolean(Adb.PREF_LEGACY, false)
        val tcpPort = app.prefs.getInt(Adb.PREF_LEGACY_PORT, ConnectOrder.DEFAULT_LEGACY_PORT)
        setup.addView(step(getString(R.string.step_adb_tcp), tcpOn && Adb.hasSecureSettings(this), getString(R.string.action_enable),
            if (tcpOn) getString(R.string.adb_tcp_on, tcpPort) else getString(R.string.adb_tcp_off)) { enableTcpAdb(tcpPort) })
        if (tcpOn) setup.addView(button(getString(R.string.action_turn_off), secondary = true) {
            app.prefs.edit().putBoolean(Adb.PREF_LEGACY, false).apply(); Adb.reset(); render()
        })
        if (!app.keys.isPaired && qrUntil == 0L) setup.addView(label(getString(R.string.help_pair_next), R.color.accent, 14f))

        val settings = card(getString(R.string.beta_settings))
        settings.addView(button(getString(R.string.beta_system_app_settings), secondary = true) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
    }

    private fun chooseTransportProbe() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.beta3_transport)
            .setMessage(R.string.beta3_transport_detail)
            .setNegativeButton(R.string.beta3_keep_tcp, null)
            .setPositiveButton(R.string.beta3_run_binder) { _, _ ->
                android.widget.Toast.makeText(this, R.string.beta3_probe_running, android.widget.Toast.LENGTH_SHORT).show()
                Thread({
                    val result = PipeProbe.run(applicationContext)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) android.app.AlertDialog.Builder(this)
                            .setTitle(R.string.beta3_transport)
                            .setMessage(result)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }, "binder-probe-ui").start()
            }
            .show()
    }

    private fun renderAudio() {
        val audio = card(getString(R.string.section_audio))
        // Call audio: interception API on Android 13+, voice-call capture on 11-12, nothing on 10.
        when (app.hub.helper.audioLevel()) {
            "unavailable" -> audio.addView(label(getString(R.string.audio_needs_android11), R.color.warn, 13f))
            "fallback" -> audio.addView(label(getString(R.string.audio_android11_12), R.color.muted, 13f))
            "blocked" -> audio.addView(label(getString(R.string.audio_blocked), R.color.warn, 13f))
        }
        if (app.hub.audio.forcedLocal) {
            audio.addView(label(getString(R.string.audio_on_android), R.color.warn, 16f))
        }
        val h = app.hub.audio.helperStatus()
        val running = h.optString("state") == "running"
        audio.addView(step(getString(R.string.step_helper), running, getString(R.string.action_start),
            if (running) getString(R.string.helper_running, h.optString("method", "auto")) +
                (h.optString("capture").takeIf { it.isNotEmpty() }?.let { " · " + getString(R.string.helper_capture, it) } ?: "") else getString(R.string.helper_stopped)) {
            app.hub.helper.kick(manual = true)
        })
        val health = app.hub.helper.health
        val shellFailure = app.hub.helper.shellInitFailure
        if (shellFailure != null) audio.addView(label(getString(R.string.beta3_shell_failed, shellFailure.name), R.color.warn, 14f))
        val shellWarnings = app.hub.helper.shellInitWarnings()
        if (shellWarnings.isNotEmpty()) audio.addView(label(getString(R.string.beta3_shell_warning, shellWarnings.joinToString { it.name }), R.color.muted, 13f))
        val stageLabel = getString(when (health.stage) {
            HelperStage.STOPPED -> R.string.helper_phase_stopped
            HelperStage.CHECKING_ADB -> R.string.helper_phase_adb
            HelperStage.STARTING_PROCESS -> R.string.helper_phase_process
            HelperStage.CONNECTING -> R.string.helper_phase_connecting
            HelperStage.AUTHENTICATING -> R.string.helper_phase_auth
            HelperStage.WAITING_HELLO -> R.string.helper_phase_hello
            HelperStage.READY -> R.string.helper_phase_ready
            HelperStage.RETRY_WAIT -> R.string.helper_phase_retry
            HelperStage.FAILED -> R.string.helper_phase_failed
        })
        audio.addView(label(stageLabel, R.color.text, 14f))
        if (health.fault != HelperFault.NONE) {
            audio.addView(label(getString(when (health.fault) {
                HelperFault.ADB_UNREACHABLE -> R.string.helper_fault_adb
                HelperFault.ADB_TIMEOUT -> R.string.helper_fault_adb_timeout
                HelperFault.PROCESS_EXITED -> R.string.helper_fault_process
                HelperFault.PROCESS_TIMEOUT -> R.string.helper_fault_process_timeout
                HelperFault.SOCKET_UNREACHABLE -> R.string.helper_fault_socket
                HelperFault.AUTH_REJECTED, HelperFault.AUTH_TIMEOUT -> R.string.helper_fault_auth
                HelperFault.HELLO_TIMEOUT, HelperFault.HELLO_INVALID -> R.string.helper_fault_hello
                HelperFault.INCOMPATIBLE -> R.string.helper_fault_version
                HelperFault.DISCONNECTED -> R.string.helper_fault_disconnected
                else -> R.string.helper_fault_exception
            }, health.fault.name), R.color.warn, 13f))
        }
        audio.addView(button(getString(R.string.audio_diagnostics_export), secondary = true) {
            val granted = missingPerms().isEmpty()
            ReportUi.send(this) { AudioDiagnosticReport.build(app, granted) }
        })
        // The phone's own microphone during bridged calls (issue #2): by default a redirect audio mode takes it out of the
        // call where the phone allows it (MicPath); muting it is opt-in, since on some phones that also mutes the iPhone.
        if (app.hub.helper.audioLevel() != "unavailable") {
            val mic = app.hub.audio.mic
            audio.addView(label(getString(R.string.mic_title), R.color.text, 16f))
            val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            for ((choice, text) in listOf(MicPath.Setting.AUTO to R.string.mic_choice_auto, MicPath.Setting.MUTE to R.string.mic_choice_mute,
                    MicPath.Setting.OPEN to R.string.mic_choice_open)) {
                group.addView(RadioButton(this).apply {
                    id = View.generateViewId(); this.text = getString(text); textSize = 15f; setTextColor(color(R.color.text))
                    isChecked = choice == mic
                    // A click (also on the chosen one) sets it anew, which forgets what this phone remembered.
                    setOnClickListener { Thread { app.hub.audio.setMic(choice); app.hub.pushStatus() }.start() }
                })
            }
            audio.addView(group)
            val autoFailed = mic == MicPath.Setting.AUTO && (android.os.Build.VERSION.SDK_INT < 33 ||
                app.hub.audio.redirectMemory().let { m -> m[MicPath.Redirect.CALL_REDIRECT]?.usable == false &&
                    (m[MicPath.Redirect.CALL_SCREENING]?.usable ?: false) == false })
            val blocked = mic == MicPath.Setting.MUTE && app.hub.audio.memory() == LocalMutes.Memory.BREAKS_UPLINK
            // Without a redirect mode the default setting's next step: the maker's own mic switch, where this phone has one that holds.
            val vendor = autoFailed && LocalMutes.autoPlan(CallAudio.silence, app.hub.audio.memory()).vendorMic
            audio.addView(label(getString(when {
                vendor -> R.string.mic_auto_vendor
                autoFailed -> R.string.mic_auto_unavailable
                mic == MicPath.Setting.AUTO -> R.string.mic_auto_note
                blocked -> R.string.mic_mute_blocked
                mic == MicPath.Setting.MUTE -> R.string.mic_mute_on
                else -> R.string.mic_open_note
            }), if (blocked || (autoFailed && !vendor)) R.color.warn else R.color.muted, 13f))
            h.optString("micPath").takeIf { it.isNotEmpty() }?.let { p ->
                audio.addView(label(when (p) {
                    "off" -> getString(R.string.mic_now_off, app.hub.helper.lastMic?.optString("mode") ?: "")
                    "muted" -> getString(R.string.mic_now_muted)
                    else -> getString(R.string.mic_now_open)
                }, R.color.muted, 13f))
            }
            h.optString("uplink").takeIf { it.isNotEmpty() }?.let { u ->
                audio.addView(label(getString(R.string.uplink_title) + ": " + getString(when (u) {
                    "ok" -> R.string.uplink_ok
                    "checking" -> R.string.uplink_checking
                    "muted-locally-fixed" -> if (mic == MicPath.Setting.MUTE) R.string.uplink_fixed else R.string.uplink_fixed_redirect
                    "off" -> R.string.uplink_off
                    else -> R.string.uplink_unverified
                }), if (u == "muted-locally-fixed") R.color.warn else R.color.muted, 13f))
            }
        }
        app.hub.lastSelftest?.let { st ->
            val methods = st.optJSONArray("methods")
            val txt = (0 until (methods?.length() ?: 0)).joinToString("\n") {
                val m = methods!!.getJSONObject(it); "${if (m.optBoolean("ok")) "✓" else "✗"} ${m.optString("name")}  ${m.optString("note")}"
            }
            audio.addView(label(getString(R.string.selftest_result, st.optString("chosen", "–")) + "\n" + txt, R.color.muted, 13f))
        }
        if (app.hub.selftestRunning) {
            audio.addView(label(getString(R.string.audio_selftest_running), R.color.accent, 14f))
        } else {
            val tests = listOf(
                R.string.action_selftest to "both",
                R.string.audio_test_capture to "capture",
                R.string.audio_test_injection to "injection"
            )
            for ((title, direction) in tests) audio.addView(button(getString(title), secondary = true) {
                Thread({ app.hub.selftest(direction) }, "audio-selftest").start()
            })
        }
        audio.addView(button(getString(R.string.link_log), secondary = true) { showLinkLog() })

    }

    /**
     * The link log (the same lines as `tools/dbg.sh linklog`: link, call-audio and mic events, no message text, no
     * numbers), with Share and Copy, so a bug report needs no computer. A header says which app, phone and Android.
     */
    private fun showLinkLog() {
        LinkLog.init(this)
        val body = LinkLog.tail(600)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        val header = "Tether $version · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · " +
            "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
        val text = header + "\n\n" + body.ifEmpty { getString(R.string.link_log_empty) }
        val view = TextView(this).apply {
            this.text = text; textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
            textDirection = View.TEXT_DIRECTION_LTR; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        AlertDialog.Builder(this).setTitle(R.string.link_log).setView(ScrollView(this).apply { addView(view) })
            .setPositiveButton(R.string.action_share) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Tether link log").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { startActivity(Intent.createChooser(send, getString(R.string.link_log))) }
            }
            .setNeutralButton(R.string.action_copy) { _, _ ->
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Tether link log", text))
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Explains the steps, then (on Enable) connects to adbd's TCP mode, grants what the app needs and starts the helper. */
    private fun enableTcpAdb(port: Int) {
        AlertDialog.Builder(this).setMessage(getString(R.string.adb_tcp_howto))
            .setPositiveButton(R.string.action_enable) { _, _ ->
                app.prefs.edit().putBoolean(Adb.PREF_LEGACY, true).apply()
                Toast.makeText(this, R.string.adb_tcp_connecting, Toast.LENGTH_SHORT).show()
                Thread {
                    Adb.reset()
                    val ok = try { Adb.get(this).ensureConnected() } catch (_: Throwable) { false }
                    if (ok) {
                        runCatching { Adb.bootstrap(this) }
                        app.prefs.edit().putBoolean("adb_paired", true).apply()
                        app.hub.helper.ensureRunning(); app.hub.refresh()
                    }
                    main.post {
                        Toast.makeText(this, if (ok) getString(R.string.adb_tcp_connected) else getString(R.string.adb_tcp_failed, port), Toast.LENGTH_LONG).show()
                        render()
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showQr() {
        qrLegacy = false
        drawQr(app.keys.openPairing())
        qrUntil = app.keys.pairingExpiry
        render()
    }

    /** The QR code holds the `tether://pair?c=…` link (the iPhone's Camera opens it in Cellular), or for Cellular 1.1
     *  the `tether1:` form of the same code. */
    private fun drawQr(code: PairingCode) {
        val size = 720
        val m = QRCodeWriter().encode(if (qrLegacy) code.encode() else code.link(), BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { if (m[it % size, it / size]) Color.BLACK else Color.WHITE }   // theme: ok, a QR code must be black on white
        qrBitmap = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ tiny view helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = getColor(id)

    private fun card(title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(color(R.color.surface)); cornerRadius = dp(16).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        }
        c.addView(label(title, R.color.text, 18f).apply {
            setTypeface(typeface, Typeface.BOLD)
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        root.addView(c)
        return c
    }

    private fun label(t: String, colorId: Int, size: Float = 15f) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color(colorId)); setPadding(0, dp(4), 0, dp(4))
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    private fun button(t: String, secondary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false
        setTextColor(if (secondary) color(R.color.accent) else color(R.color.background))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            if (secondary) setStroke(dp(1), color(R.color.accent)) else setColor(color(R.color.accent))
        }
        minHeight = dp(48)
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); marginEnd = dp(8) }
        setPadding(dp(18), 0, dp(18), 0)
        setOnClickListener { onClick() }
    }

    private fun step(title: String, done: Boolean, action: String, detail: String? = null, help: String? = null,
                     onClick: () -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        texts.addView(label(title, R.color.text, 16f))
        texts.addView(label(detail ?: getString(if (done) R.string.done else R.string.missing), if (done) R.color.ok else R.color.warn, 13f))
        // What to do, in plain words, until the step is done.
        if (!done && help != null) texts.addView(label(help, R.color.muted, 13f))
        row.addView(texts)
        if (!done) row.addView(button(action) { onClick() })
        return row
    }
}
