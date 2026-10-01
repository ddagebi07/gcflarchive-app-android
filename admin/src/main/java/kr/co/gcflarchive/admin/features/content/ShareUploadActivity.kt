package kr.co.gcflarchive.admin.features.content

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.Permission
import kr.co.gcflarchive.admin.ui.BaseActivity
import kr.co.gcflarchive.admin.ui.kit.Dialogs

/** 공유 시트 → 간편 업로드: PDF 1건 또는 YouTube 링크 (signed in + unlocked only). */
class ShareUploadActivity : BaseActivity() {
    private var shown = false

    override fun onResume() {
        super.onResume()
        // BaseActivity may have sent us to login/lock first; show the form once we're through.
        if (shown || !AdminSession.get(this).isSignedIn || app.lock.locked) return
        shown = true
        val g = AdminSession.get(this).grants
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val youtube = Regex("""https?://\S*(youtu\.be|youtube\.com)\S*""").find(text)?.value
        when {
            stream != null && g.has(Permission.PDFS) ->
                Upload.pdfForm(this, stream, onClose = { finish() }) { Dialogs.toast(this, getString(R.string.upload_done)) }
            youtube != null && g.has(Permission.VIDEOS) ->
                Upload.youtubeForm(this, youtube, onClose = { finish() }) { Dialogs.toast(this, getString(R.string.upload_done)) }
            else -> {
                Dialogs.toast(this, getString(R.string.upload_not_allowed))
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shown = savedInstanceState?.getBoolean("shown") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("shown", shown)
    }
}
