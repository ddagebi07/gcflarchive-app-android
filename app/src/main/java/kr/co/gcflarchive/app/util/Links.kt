package kr.co.gcflarchive.app.util

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.NativePages
import kr.co.gcflarchive.app.R

/**
 * Where links go now that the app has no WebView screens: pages of our own site open
 * their native screen ([NativePages]); everything else (or a site page without a
 * native screen) opens in the browser.
 */
object Links {
    fun open(context: Context, uri: Uri) {
        if (Config.isOwnHost(uri)) {
            val path = (uri.path ?: "/").ifBlank { "/" } + (uri.query?.let { "?$it" } ?: "")
            NativePages.intentFor(context, path)?.let {
                context.startActivity(it)
                return
            }
        }
        openExternal(context, uri)
    }

    fun openExternal(context: Context, uri: Uri) {
        val intent = (
            if (uri.scheme == "intent") {
                runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            ) ?: return
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
    }

    /** Saves [url] to Downloads via DownloadManager, sending the site session cookie along. */
    fun download(
        context: Context,
        url: String,
        userAgent: String? = null,
        disposition: String? = null,
        mimeType: String? = null,
        fileName: String = URLUtil.guessFileName(url, disposition, mimeType),
    ) {
        val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val request = DownloadManager.Request(Uri.parse(url))
            .addRequestHeader("User-Agent", userAgent ?: "${System.getProperty("http.agent").orEmpty()} ${Config.USER_AGENT_SUFFIX}".trim())
            .setTitle(safeName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
        mimeType?.let { request.setMimeType(it) }
        if (Config.isOwnHost(Uri.parse(url))) {
            CookieManager.getInstance().getCookie(Config.BASE_URL)?.let { request.addRequestHeader("Cookie", it) }
        }
        context.getSystemService(DownloadManager::class.java)?.enqueue(request) ?: return
        Toast.makeText(context, context.getString(R.string.download_started, safeName), Toast.LENGTH_SHORT).show()
    }

    fun copy(context: Context, label: String, text: String, toastRes: Int = R.string.copied) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
    }

    fun share(context: Context, text: String, title: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, title))
    }
}
