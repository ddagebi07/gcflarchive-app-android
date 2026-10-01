package kr.co.gcflarchive.admin.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kr.co.gcflarchive.admin.core.AdminApi

object Navigator {
    /** Opens a route: native screens in SectionActivity, web-only tools in a Custom Tab. */
    fun open(context: Context, route: Route) {
        val web = route.webPath
        if (web != null) {
            CustomTabsIntent.Builder().setShowTitle(true).build()
                .launchUrl(context, Uri.parse(AdminApi.get(context).url(web)))
        } else {
            context.startActivity(SectionActivity.intent(context, route))
        }
    }
}
