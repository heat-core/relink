package app.tether.ui

import android.Manifest
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
import android.widget.Switch
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.tether.BuildConfig
import app.tether.R
import app.tether.TetherApp
import app.tether.adb.Adb
import app.tether.adb.AdbFailure
import app.tether.adb.ConnectOrder
import app.tether.adb.ProbeMode
import app.tether.audio.HelperCheckOutcome
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

class MainActivity : ComponentActivity() {
    private lateinit var app: TetherApp
    private lateinit var root: LinearLayout
    private enum class Page(val label: Int, val icon: Int) {
        HOME(R.string.beta_home, android.R.drawable.ic_menu_view),
        LINK(R.string.nav_link, android.R.drawable.ic_menu_share),
        SETUP(R.string.section_setup, android.R.drawable.ic_menu_manage),
        AUDIO(R.string.nav_diagnostics, android.R.drawable.ic_menu_info_details),
        SETTINGS(R.string.section_settings, 0)
    }
    private var page by mutableStateOf(Page.HOME)
    private var pageRevision by mutableIntStateOf(0)
    private var renderedPage: Page? = null
    private var renderedRevision = -1
    private var qrCountdown: TextView? = null
    private var adbCheckInProgress: Int? = null
    private var adbCheckFeedback: String? = null
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
        page = Page.entries.firstOrNull { it.name == savedInstanceState?.getString("page") } ?: Page.HOME
        enableEdgeToEdge()
        setContent { HostContent() }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (missingPerms().isEmpty()) LinkService.start(this)
        render()
    }

    private fun tick() {
        main.postDelayed({
            if (page == Page.LINK && qrUntil > 0) {
                val seconds = qrRemaining()
                if (seconds <= 0 || app.keys.currentToken() == null) render()
                else qrCountdown?.text = getString(R.string.pair_expires, seconds)
            }
            tick()
        }, 1000)
    }

    private fun qrRemaining() = (qrUntil - android.os.SystemClock.elapsedRealtime() + 999) / 1000

    private fun missingPerms() = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    private fun navigate(destination: Page) {
        if (page == destination) return
        page = destination
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun HostContent() {
        val primary = colorResource(R.color.accent)
        val surface = colorResource(R.color.surface)
        val background = colorResource(R.color.background)
        val text = colorResource(R.color.text)
        val scheme = if (isSystemInDarkTheme()) darkColorScheme(
            primary = primary, onPrimary = background, surface = surface, onSurface = text, background = background,
            onBackground = text, secondaryContainer = background, onSecondaryContainer = primary
        ) else lightColorScheme(
            primary = primary, onPrimary = background, surface = surface, onSurface = text, background = background,
            onBackground = text, secondaryContainer = surface, onSecondaryContainer = primary
        )
        MaterialTheme(colorScheme = scheme) {
            BackHandler(enabled = page != Page.HOME) { navigate(Page.HOME) }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(page.label), style = MaterialTheme.typography.titleLarge)
                        } },
                        actions = {
                            if (page != Page.SETTINGS) TextButton(onClick = { navigate(Page.SETTINGS) }) {
                                Text(stringResource(R.string.section_settings))
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = surface) {
                        for (item in Page.entries.filter { it.icon != 0 }) {
                            NavigationBarItem(
                                selected = page == item,
                                onClick = { navigate(item) },
                                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                                label = { Text(stringResource(item.label)) }
                            )
                        }
                    }
                }
            ) { insets ->
                if (page == Page.HOME) {
                    Dashboard(Modifier.padding(insets))
                } else {
                    AndroidView(
                        modifier = Modifier.padding(insets).fillMaxSize(),
                        factory = { context ->
                            root = LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dp(16), dp(12), dp(16), dp(24))
                            }
                            renderedPage = null
                            ScrollView(context).apply { addView(root); isFillViewport = true }
                        },
                        update = { view ->
                            val currentPage = page
                            val revision = pageRevision
                            if (currentPage != renderedPage || revision != renderedRevision) {
                                val scrollY = if (currentPage == renderedPage) view.scrollY else 0
                                renderPage(currentPage)
                                renderedPage = currentPage
                                renderedRevision = revision
                                view.post { if (page == currentPage) view.scrollTo(0, scrollY) }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun render() { pageRevision++ }

    private fun renderPage(destination: Page) {
        qrCountdown = null
        root.removeAllViews()
        when (destination) {
            Page.HOME -> Unit
            Page.LINK -> renderLink()
            Page.SETUP -> renderSetup()
            Page.AUDIO -> renderAudio()
            Page.SETTINGS -> renderSettings()
        }
    }

    private fun renderSettings() {
        card(getString(R.string.beta_connection)).addView(Switch(this).apply {
            text = getString(R.string.lan_enabled)
            setTextColor(color(R.color.text))
            isChecked = app.prefs.getBoolean(LinkService.PREF_LAN_ENABLED, true)
            minHeight = dp(48)
            setOnCheckedChangeListener { _: CompoundButton, enabled: Boolean ->
                app.prefs.edit().putBoolean(LinkService.PREF_LAN_ENABLED, enabled).apply()
            }
        })
        card(getString(R.string.section_about)).addView(label(
            getString(R.string.about_release, BuildConfig.TETHER_RELEASE, BuildConfig.VERSION_CODE), R.color.text
        ))
    }

    @Composable private fun Dashboard(modifier: Modifier) {
        val revision = pageRevision
        val conn = app.hub.conn?.takeIf { it.isUp }
        val paired = app.keys.isPaired
        val linkText = when {
            conn != null -> R.string.state_connected
            LinkService.running?.peripheral?.enabled == false -> R.string.state_bt_off
            !paired -> R.string.state_unpaired
            else -> R.string.state_waiting
        }
        val needsSetup = remember(revision) {
            missingPerms().isNotEmpty() || !app.sims.isDialer() ||
                !(app.hub.helper.ready || Adb.snapshotStatus(this).anyConnected)
        }
        val audioText = when {
            app.hub.audio.forcedLocal -> R.string.audio_on_android
            app.hub.audio.helperStatus().optString("state") == "running" -> R.string.helper_phase_ready
            else -> R.string.helper_phase_stopped
        }
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardCard(R.string.beta_connection) {
                Text(
                    stringResource(linkText),
                    color = colorResource(if (conn == null) R.color.warn else R.color.ok),
                    style = MaterialTheme.typography.headlineSmall
                )
                conn?.let { Text(transportName(it.transport.label)) }
                ComposeButton(onClick = { navigate(Page.LINK) }) {
                    Text(stringResource(if (paired) R.string.beta_manage_devices else R.string.pair_iphone))
                }
            }
            DashboardCard(R.string.section_setup) {
                Text(stringResource(if (needsSetup) R.string.beta_setup_pending else R.string.beta_setup_complete))
                if (needsSetup) ComposeButton(onClick = { navigate(Page.SETUP) }) {
                    Text(stringResource(R.string.beta_continue_setup))
                }
            }
            DashboardCard(R.string.section_audio) {
                Text(stringResource(audioText))
                ComposeButton(onClick = { navigate(Page.AUDIO) }) {
                    Text(stringResource(R.string.beta_open_diagnostics))
                }
                if (PipeProbe.available(this@MainActivity)) {
                    TextButton(onClick = { chooseTransportProbe() }) { Text(stringResource(R.string.beta3_transport)) }
                }
            }
        }
    }

    @Composable private fun DashboardCard(title: Int, content: @Composable () -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                content()
            }
        }
    }

    private fun renderLink() {
        // Link
        val link = card(getString(R.string.section_link))
        val svc = LinkService.running
        val conn = app.hub.conn
        val (state, stateColor) = when {
            conn?.isUp == true -> getString(R.string.state_connected) + " · " + transportName(conn.transport.label) to R.color.ok
            svc?.peripheral?.enabled == false -> getString(R.string.state_bt_off) to R.color.bad
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
        val remaining = qrRemaining()
        if (qrUntil > 0 && remaining > 0 && app.keys.currentToken() != null) {
            link.addView(label(getString(if (qrLegacy) R.string.pair_hint_old else R.string.pair_hint), R.color.text))
            link.addView(ImageView(this).apply {
                setImageBitmap(qrBitmap); adjustViewBounds = true
                (drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = false
                layoutParams = LinearLayout.LayoutParams(dp(260), dp(260)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            })
            qrCountdown = label(getString(R.string.pair_expires, remaining), R.color.muted).apply { gravity = Gravity.CENTER }
            link.addView(qrCountdown)
            link.addView(button(getString(R.string.beta4_copy_manual), secondary = true) {
                val code = app.keys.openPairingCurrent()?.encode() ?: return@button
                val clip = android.content.ClipData.newPlainText("ReLink", code)
                clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            })
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
        link.addView(label(app.keys.pairingOutcome, R.color.muted, 14f))
        link.addView(label(getString(R.string.beta4_pair_tip), R.color.muted, 13f))
        row.addView(button(getString(R.string.pair_iphone)) { showQr() })
        link.addView(row)

    }

    private fun transportName(transport: String) = when (transport) {
        "lan" -> getString(R.string.transport_wifi)
        "l2cap" -> "Bluetooth L2CAP"
        "gatt" -> "Bluetooth GATT"
        else -> transport
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
        val adbStatus = Adb.snapshotStatus(this)
        val helperReady = app.hub.helper.ready
        // Wireless debugging exists from Android 11; before that only TCP adb (below) can start the helper.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val wirelessHelp = when {
                adbStatus.tlsConnected -> getString(R.string.help_adb)
                adbStatus.paired && helperReady -> getString(R.string.adb_paired_helper_alive)
                else -> getString(R.string.help_adb)
            }
            setup.addView(step(getString(R.string.step_n, 4, getString(R.string.step_adb)), adbStatus.tlsConnected, getString(R.string.action_pair),
                help = wirelessHelp) {
                Adb.showPairingNotification(this)
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            })
            if (adbStatus.paired) setup.addView(label(getString(R.string.adb_pairing_saved), R.color.muted, 14f))
        }

        // Legacy TCP adb: opt-in, for Android 10 (no Wireless debugging) or a phone without a trusted Wi-Fi network.
        val tcpOn = adbStatus.tcpConfigured
        val tcpPort = adbStatus.tcpPort
        val tcpDetail = when {
            !tcpOn -> getString(R.string.adb_tcp_off)
            adbStatus.tcpVerified -> getString(R.string.adb_tcp_verified, tcpPort)
            adbStatus.legacy == AdbFailure.CONNECTION_REFUSED -> getString(R.string.adb_tcp_listener_refused, tcpPort)
            else -> getString(R.string.beta4_tcp_unverified, tcpPort)
        }
        setup.addView(step(getString(R.string.step_adb_tcp), adbStatus.tcpVerified, getString(R.string.adb_tcp_configure), tcpDetail) {
            enableTcpAdb(tcpPort)
        })
        if (tcpOn) {
            setup.addView(button(getString(R.string.action_test_tcp), secondary = true) { runAdbCheck(ProbeMode.VERIFY_TCP_ONLY) }
                .apply { isEnabled = adbCheckInProgress == null })
            setup.addView(button(getString(R.string.action_turn_off), secondary = true) {
                app.prefs.edit().putBoolean(Adb.PREF_LEGACY, false).apply()
                if (Adb.observedTransport() == "legacy") Adb.reset()
                adbCheckFeedback = null
                render()
            }.apply { isEnabled = adbCheckInProgress == null })
        }
        setup.addView(label(adbStatus.summary(), R.color.muted, 14f))
        val feedback = adbCheckInProgress?.let { getString(it) } ?: adbCheckFeedback
        if (feedback != null) setup.addView(label(feedback, R.color.accent, 14f))
        setup.addView(button(getString(R.string.beta4_check_adb), secondary = true) {
            runAdbCheck(ProbeMode.VERIFY_ALL)
        }.apply { isEnabled = adbCheckInProgress == null })
        setup.addView(button(getString(R.string.beta4_export_all), secondary = true) {
            ReportUi.send(this) { AudioDiagnosticReport.build(app, missingPerms().isEmpty(), all = true) }
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
            checkHelperState()
        })
        audio.addView(button(getString(R.string.action_check_helper), secondary = true) {
            checkHelperState()
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
        if (running) {
            val runtime = app.hub.helper.snapshot()
            val capture = if (runtime.captureAvailable) R.string.audio_path_verified else R.string.audio_path_unverified
            val injection = if (runtime.injectionAvailable) R.string.audio_path_verified else R.string.audio_path_unverified
            audio.addView(label(getString(R.string.audio_path_status, getString(capture), getString(injection)), R.color.muted, 14f))
        }
        if (app.hub.helper.health.fault != HelperFault.NONE) {
            audio.addView(label(Adb.connectionStatus.summary(), R.color.muted, 14f))
            audio.addView(label(getString(R.string.beta4_adb_tip), R.color.muted, 13f))
        }
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
        val header = "ReLink $version · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · " +
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
                    .putExtra(Intent.EXTRA_SUBJECT, "ReLink link log").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { startActivity(Intent.createChooser(send, getString(R.string.link_log))) }
            }
            .setNeutralButton(R.string.action_copy) { _, _ ->
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("ReLink link log", text))
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runAdbCheck(mode: ProbeMode) {
        if (adbCheckInProgress != null) return
        val tcpOnly = mode == ProbeMode.VERIFY_TCP_ONLY
        adbCheckInProgress = if (tcpOnly) R.string.adb_tcp_connecting else R.string.adb_checking
        render()
        Thread({
            val result = runCatching { Adb.get(this).checkLiveConnection(mode) }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    adbCheckInProgress = null
                    adbCheckFeedback = result.fold(
                        onSuccess = { getString(R.string.adb_check_result, it.summary()) },
                        onFailure = { getString(R.string.adb_check_error) },
                    )
                    render()
                }
            }
            val status = result.getOrNull()
            val canStartHelper = status != null &&
                (if (tcpOnly) status.tcpConfigured && status.tcpVerified else status.anyConnected)
            if (canStartHelper && !app.hub.helper.ready) {
                app.hub.helper.kick(manual = true)
            }
        }, "adb-live-check").start()
    }

    private fun checkHelperState() {
        Toast.makeText(this, R.string.helper_checking, Toast.LENGTH_SHORT).show()
        Thread({
            val outcome = app.hub.helper.checkOrStart(allowBootstrap = true)
            main.post {
                if (!isFinishing && !isDestroyed) {
                    val msg = when (outcome) {
                        HelperCheckOutcome.AlreadyRunning -> getString(R.string.helper_already_running)
                        HelperCheckOutcome.Reattached -> getString(R.string.helper_reattached)
                        HelperCheckOutcome.Bootstrapped -> getString(R.string.helper_bootstrapped)
                        is HelperCheckOutcome.Failed -> getString(R.string.helper_check_failed, outcome.fault.name)
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    render()
                }
            }
        }, "helper-check").start()
    }

    private fun enableTcpAdb(port: Int) {
        if (adbCheckInProgress != null) return
        AlertDialog.Builder(this).setMessage(getString(R.string.adb_tcp_howto, port))
            .setPositiveButton(R.string.adb_tcp_configure) { _, _ ->
                app.prefs.edit().putBoolean(Adb.PREF_LEGACY, true).apply()
                runAdbCheck(ProbeMode.VERIFY_TCP_ONLY)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showQr() {
        qrLegacy = true
        drawQr(app.keys.openPairing())
        qrUntil = app.keys.pairingExpiry
        render()
    }

    /** The QR code holds the `tether://pair?c=…` link (the iPhone's Camera opens it in Cellular), or for Cellular 1.1
     *  the `tether1:` form of the same code. */
    private fun drawQr(code: PairingCode) {
        val size = 720
        val m = PairingQr.encode(code, qrLegacy, size)
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
