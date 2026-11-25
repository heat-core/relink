package app.tether.helper

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.os.Looper
import android.os.Process
import app.tether.helper.ShellStartup.Step

/** Android context for the existing ADB-launched helper, running as the real shell UID. */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
object ShellEnv {
    const val SHELL_PACKAGE = "com.android.shell"

    @Volatile private var ctx: Context? = null
    private val warnings = linkedSetOf<Step>()

    @Synchronized
    fun init() {
        if (ctx != null) return
        warnings.clear()
        ShellStartup.required(Step.UID) { check(Process.myUid() == SyntheticPipeProtocol.shellUid) }
        ShellStartup.required(Step.MAIN_LOOPER) {
            if (Looper.getMainLooper() == null) {
                @Suppress("DEPRECATION")
                Looper.prepareMainLooper()
            }
        }
        val (atClass, thread) = ShellStartup.required(Step.ACTIVITY_THREAD) {
            val type = Class.forName("android.app.ActivityThread")
            val current = type.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }
            val existing = current.get(null)
            val instance = existing ?: type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                .also { current.set(null, it) }
            type to instance
        }
        // Preserve vendor state when Android has already initialized these fields.
        optional(Step.APPLICATION_INFO) {
            val field = atClass.getDeclaredField("mBoundApplication").apply { isAccessible = true }
            if (field.get(thread) == null) {
                val type = Class.forName("android.app.ActivityThread\$AppBindData")
                val bind = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                val ai = android.content.pm.ApplicationInfo().apply { packageName = SHELL_PACKAGE; uid = Process.myUid() }
                type.getDeclaredField("appInfo").apply { isAccessible = true }.set(bind, ai)
                field.set(thread, bind)
            }
        }
        if (Build.VERSION.SDK_INT >= 31) optional(Step.CONFIGURATION) {
            // Samsung reads the configuration while creating the system context. This API is non-SDK.
            val field = atClass.getDeclaredField("mConfigurationController").apply { isAccessible = true }
            if (field.get(thread) == null) {
                val cc = Class.forName("android.app.ConfigurationController")
                val internal = Class.forName("android.app.ActivityThreadInternal")
                field.set(thread, cc.getDeclaredConstructor(internal).apply { isAccessible = true }.newInstance(thread))
            }
        }
        val system = ShellStartup.required(Step.SYSTEM_CONTEXT) {
            atClass.getDeclaredMethod("getSystemContext").apply { isAccessible = true }.invoke(thread) as Context
        }
        ctx = ShellStartup.required(Step.ATTRIBUTION) {
            val wrapped = ShellContext(system)
            if (Build.VERSION.SDK_INT >= 31) check(wrapped.attributionSource.uid == Process.myUid())
            wrapped
        }
    }

    @Synchronized
    fun startupWarnings(): List<Step> = warnings.toList()

    private fun optional(step: Step, block: () -> Unit) {
        try {
            ShellStartup.required(step, block)
        } catch (failure: ShellStartup.Failure) {
            warnings.add(step)
            log("shell-init-warning:${step.name}:${failure.kind}")
        }
    }

    val context: Context get() = ctx ?: run { init(); checkNotNull(ctx) }

    /** A package context gives ContentResolver framework attribution for the actual shell package. */
    fun binderContext(): Context = ShellStartup.required(Step.ATTRIBUTION) {
        context.createPackageContext(SHELL_PACKAGE, 0).also { check(it.packageName == SHELL_PACKAGE) }
    }

    /** Prefer the shell-attributed AudioManager when the constructor is available. */
    val audioManager: AudioManager by lazy {
        try {
            AudioManager::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
                .newInstance(context)
        } catch (_: ReflectiveOperationException) {
            context.getSystemService(AudioManager::class.java)
        } catch (_: SecurityException) {
            context.getSystemService(AudioManager::class.java)
        }
    }

    fun log(msg: String) {
        System.err.println("[tether-helper] $msg")
        LogRing.add(msg)
    }

    private class ShellContext(base: Context) : ContextWrapper(base) {
        override fun getPackageName() = SHELL_PACKAGE
        override fun getOpPackageName() = SHELL_PACKAGE
        @SuppressLint("NewApi") // Available on Android 12+.
        override fun getAttributionSource(): AttributionSource =
            AttributionSource.Builder(Process.myUid()).setPackageName(SHELL_PACKAGE).build()
        override fun getApplicationContext(): Context = this
        override fun getApplicationInfo(): android.content.pm.ApplicationInfo =
            android.content.pm.ApplicationInfo(super.getApplicationInfo()).apply { targetSdkVersion = Build.VERSION.SDK_INT }
    }
}
