package app.tether.helper

/** Allowlisted stage records shared by helper, UI and diagnostics. */
object ShellStartup {
    enum class Step { UID, MAIN_LOOPER, ACTIVITY_THREAD, APPLICATION_INFO, CONFIGURATION, SYSTEM_CONTEXT, ATTRIBUTION, TOKEN_INPUT }

    class Failure(val step: Step, val kind: String) : IllegalStateException("shell-init:${step.name}:$kind")

    fun <T> required(step: Step, block: () -> T): T = try {
        block()
    } catch (failure: Exception) {
        throw Failure(step, failure.javaClass.simpleName)
    } catch (failure: LinkageError) {
        throw Failure(step, failure.javaClass.simpleName)
    }

    fun failureFrom(output: String): Step? = output.lineSequence()
        .mapNotNull { line -> Step.entries.firstOrNull { line == "shell-init-failed:${it.name}" } }
        .firstOrNull()
}
