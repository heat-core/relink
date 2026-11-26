package app.tether.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperBridgeCommandTest {
    @Test fun launchCommandReadsTokenFromStdinWithoutExposingSecretInArgs() {
        val secret = "0123456789abcdef0123456789abcdef"
        val cmd = HelperBridge.buildLaunchCommand("app.relink.android", "/data/app/base.apk", 47123)
        assertFalse(cmd.contains(secret))
        assertTrue(cmd.contains("umask 077"))
        assertTrue(cmd.contains("read -r _t && [ -n \"\$_t\" ] && printf"))
        assertTrue(cmd.indexOf("echo shell-init-failed:TOKEN_INPUT; exit 1") in 0 until cmd.indexOf("setsid"))
        assertTrue(cmd.contains("serve-file 47123"))
        assertTrue(cmd.contains("--replace"))
    }
}
