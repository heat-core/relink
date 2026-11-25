package app.tether.audio

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import app.tether.helper.SyntheticPipeProtocol

/** DUMP is checked by the framework and explicitly on call(), which operates on no content rows. */
class PipeProbeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val uid = Binder.getCallingUid()
        if (uid != SyntheticPipeProtocol.shellUid) return null
        context?.enforceCallingPermission("android.permission.DUMP", "Shell probe requires DUMP") ?: return null
        return PipeProbe.receive(method, extras, uid)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
