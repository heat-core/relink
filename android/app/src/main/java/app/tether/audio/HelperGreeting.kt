package app.tether.audio

import java.io.DataInputStream
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

/** The first frame after mutual authentication, before a helper becomes available to audio control. */
internal object HelperGreeting {
    const val TYPE = 1
    private const val MAX_BYTES = 4096

    fun read(input: DataInputStream): JSONObject {
        if (input.readUnsignedByte() != TYPE) throw IOException("expected helper HELLO")
        val size = input.readInt()
        if (size !in 1..MAX_BYTES) throw IOException("invalid helper HELLO size")
        val payload = ByteArray(size)
        input.readFully(payload)
        return try {
            JSONObject(String(payload, Charsets.UTF_8))
        } catch (failure: JSONException) {
            throw IOException("malformed helper HELLO", failure)
        }
    }

    fun compatible(greeting: JSONObject, apk: String, version: Int): Boolean =
        greeting.optString("apk") == apk && greeting.opt("version") == version
}
