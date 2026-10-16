package kr.co.gcflarchive.app.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.navigation.NavigationBarView
import kr.co.gcflarchive.core.i18n.I18n

/**
 * 화면 언어 for this app's own (View) screens: while a teacher uses another language, every TextView,
 * hint, toolbar title and bottom-tab label whose Korean text has a translation (I18n, edited on
 * /admin/i18n) is shown translated. Texts without one stay Korean. Students (한국어) are untouched.
 *
 * It re-checks after each layout, so texts set later in code (and list rows) are covered too; a text
 * is only replaced when its translation differs, so this can't loop.
 */
object I18nViews {
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = watch(activity.window.decorView)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private val watched = java.util.WeakHashMap<View, Boolean>()

    private fun watch(root: View) {
        if (watched.put(root, true) == null) {
            root.viewTreeObserver.addOnGlobalLayoutListener(ViewTreeObserver.OnGlobalLayoutListener { translate(root) })
        }
        translate(root)
    }

    /** Translates [root] and its children once (no-op in 한국어). */
    fun translate(root: View) {
        if (I18n.lang == I18n.DEFAULT) return
        walk(root)
    }

    private fun swap(text: CharSequence?): String? {
        val s = text?.toString() ?: return null
        if (s.none { it in '가'..'힣' }) return null
        return I18n.translationOf(s)?.takeIf { it != s }
    }

    private fun walk(v: View) {
        when (v) {
            is EditText -> swap(v.hint)?.let { v.hint = it }
            is TextView -> swap(v.text)?.let { v.text = it }
            is Toolbar -> swap(v.title)?.let { v.title = it }
        }
        if (v is NavigationBarView) {
            val menu = v.menu
            for (i in 0 until menu.size()) menu.getItem(i).let { item -> swap(item.title)?.let { item.title = it } }
        }
        swap(v.contentDescription)?.let { v.contentDescription = it }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
    }
}
