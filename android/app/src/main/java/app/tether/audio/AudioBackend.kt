package app.tether.audio

import app.tether.helper.LocalMutes
import app.tether.helper.AudioStrategies
import app.tether.helper.MicPath
import org.json.JSONObject

internal data class AudioBackendRequest(
    val rate: Int,
    val inject: String,
    val bufferMs: Int,
    val testLine: Boolean,
    val mic: MicPath.Setting,
    val mute: LocalMutes.Memory?,
    val redirect: Map<MicPath.Redirect, MicPath.Verdict>,
    val active: Boolean,
    val skipCapture: String? = null,
    val skipInjection: String? = null,
    val capture: String = "auto"
)

internal data class AudioBackendCapabilities(val capture: Boolean, val injection: Boolean)

/** Local audio control and PCM hand-off. Capability here means eligible to attempt, never audio verified. */
internal interface AudioBackend {
    val id: String
    val ready: Boolean
    fun capabilities(sdk: Int): AudioBackendCapabilities
    fun start(request: AudioBackendRequest): Boolean
    fun stop(): Boolean
    fun writeUplink(pcm: ShortArray, forStart: Int): Boolean
}

internal class TcpAudioBackend(private val helper: HelperBridge) : AudioBackend {
    override val id = "tcp"
    override val ready get() = helper.ready
    override fun capabilities(sdk: Int) = AudioBackendCapabilities(AudioStrategies.capture(sdk).isNotEmpty(), AudioStrategies.injection(sdk).isNotEmpty())
    override fun start(request: AudioBackendRequest): Boolean {
        val mute = request.mute
        val onTestLine = request.testLine
        return helper.start(request.rate, request.capture, request.inject, request.bufferMs, onTestLine, mic = request.mic.wire,
            micMute = LocalMutes.wantMicMute(request.mic == MicPath.Setting.MUTE, mute), micCheck = LocalMutes.wantCheck(mute),
            redirect = if (onTestLine) JSONObject() else MicPath.memoryJson(request.redirect),
            muteMemory = if (onTestLine) null else mute?.wire, active = request.active,
            skipCapture = request.skipCapture, skipInjection = request.skipInjection)
    }

    override fun stop() = helper.stop()
    override fun writeUplink(pcm: ShortArray, forStart: Int) = helper.up(pcm, forStart)
}
