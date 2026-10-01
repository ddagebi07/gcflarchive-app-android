package kr.co.gcflarchive.admin.ui.kit

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding

/**
 * Keeps every screen clear of the status bar, navigation bar, display cutout and
 * keyboard. targetSdk 35 makes apps edge-to-edge on Android 15, so without this,
 * bottom buttons slide under the gesture bar or the keyboard and can't be tapped.
 */
object Insets {
    /**
     * @param root content root; its own padding is kept and the insets are added to it.
     * @param bottomBar bottom navigation that should extend under the navigation bar
     *   (it gets the bottom inset as padding) and hide while the keyboard is open.
     */
    fun apply(activity: ComponentActivity, root: View, bottomBar: View? = null) {
        activity.enableEdgeToEdge()
        val base = Padding(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        val barBase = bottomBar?.paddingBottom ?: 0
        // Material's bottom navigation pads itself from the insets it receives; children get
        // the consumed (zero) insets from the root, which would undo the padding set below.
        bottomBar?.let { ViewCompat.setOnApplyWindowInsetsListener(it) { _, insets -> insets } }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboard = ime.bottom > bars.bottom
            val bottom = when {
                keyboard -> ime.bottom
                bottomBar != null && bottomBar.isVisible -> 0
                else -> bars.bottom
            }
            v.setPadding(base.left + bars.left, base.top + bars.top, base.right + bars.right, base.bottom + bottom)
            if (bottomBar != null) {
                // The keyboard already covers the tab bar; hiding it gives the form the room.
                bottomBar.visibility = if (keyboard) View.GONE else View.VISIBLE
                bottomBar.updatePadding(bottom = barBase + if (keyboard) 0 else bars.bottom)
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)
}
