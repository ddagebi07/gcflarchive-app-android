package kr.co.gcflarchive.app.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity

/**
 * Shared WebView wiring for every screen that shows the website: keeps the Flask
 * session cookie (so one login covers the WebView and native uploads), handles
 * <input type="file">, downloads, and sends foreign links to the browser.
 *
 * Must be created during the owner's initialization (it registers an activity result).
 */
class GcflWebView(
    caller: ActivityResultCaller,
    private val listener: Listener = object : Listener {},
) {
    interface Listener {
        fun onPageStarted(url: String) {}
        fun onPageFinished(url: String) {}
        fun onProgress(progress: Int) {}
        fun onTitle(title: String) {}
        /** The site asked for login and the user closed the native login screen. */
        fun onLoginCancelled() {}
    }

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var webView: WebView? = null
    private var pendingNext: String? = null

    // /verify (the web login page) is replaced by the native LoginActivity; afterwards
    // the page the site wanted to show (?next=) is loaded with the fresh session.
    private val login = caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val next = pendingNext ?: "/"
        pendingNext = null
        if (result.resultCode == android.app.Activity.RESULT_OK) webView?.loadUrl(Config.url(next)) else listener.onLoginCancelled()
    }

    private val fileChooser = caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingFileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        pendingFileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(webView: WebView) {
        this.webView = webView
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, false)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "$userAgentString ${Config.USER_AGENT_SUFFIX}"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (Config.isOwnHost(uri)) {
                    if (request.isForMainFrame && isLoginPage(uri)) {
                        openLogin(view.context, uri)
                        return true
                    }
                    return false
                }
                // Everything else (Google Drive, YouTube, tel:, mailto:, intent:) leaves the app.
                openExternal(view.context, uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                listener.onPageStarted(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                cookies.flush()
                listener.onPageFinished(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = listener.onProgress(newProgress)

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank()) listener.onTitle(title)
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: WebChromeClient.FileChooserParams,
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = callback
                return try {
                    fileChooser.launch(params.createIntent())
                    true
                } catch (_: ActivityNotFoundException) {
                    pendingFileCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(webView.context, url, userAgent, contentDisposition, mimeType)
        }
    }

    /**
     * Loads [url], but opens the native login instead when it is the web login page
     * (loadUrl() does not pass through shouldOverrideUrlLoading).
     */
    fun load(url: String) {
        val uri = Uri.parse(url)
        val view = webView ?: return
        if (Config.isOwnHost(uri) && isLoginPage(uri)) openLogin(view.context, uri) else view.loadUrl(url)
    }

    private fun openLogin(context: Context, uri: Uri) {
        if (pendingNext != null) return
        pendingNext = uri.getQueryParameter("next")?.takeIf { it.startsWith("/") && !it.startsWith("//") } ?: "/"
        login.launch(LoginActivity.intent(context))
    }

    companion object {
        fun isLoginPage(uri: Uri): Boolean = uri.path?.trimEnd('/') == "/verify"

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
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            context.getSystemService(DownloadManager::class.java)?.enqueue(request) ?: return
            Toast.makeText(context, context.getString(R.string.download_started, safeName), Toast.LENGTH_SHORT).show()
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
    }
}
