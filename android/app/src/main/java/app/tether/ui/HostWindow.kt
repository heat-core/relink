package app.tether.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Keeps system bars and the keyboard outside controls on edge-to-edge Android versions. */
fun Activity.fitHostWindow(view: View) {
    if (Build.VERSION.SDK_INT < 30) return
    window.setDecorFitsSystemWindows(false)
    val left = view.paddingLeft
    val top = view.paddingTop
    val right = view.paddingRight
    val bottom = view.paddingBottom
    view.setOnApplyWindowInsetsListener { target, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val ime = insets.getInsets(WindowInsets.Type.ime())
        target.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + maxOf(bars.bottom, ime.bottom))
        WindowInsets.CONSUMED
    }
    view.requestApplyInsets()
}
