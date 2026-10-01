package kr.co.gcflarchive.app.share

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityShareReceiverBinding
import kr.co.gcflarchive.app.databinding.ItemShareFileBinding

/**
 * Receives ACTION_SEND / ACTION_SEND_MULTIPLE from any app's share sheet (and the
 * in-app "파일 올리기" picker) and uploads the files to 극플드라이브.
 */
class ShareReceiverActivity : AppCompatActivity() {
    private lateinit var binding: ActivityShareReceiverBinding
    private lateinit var uploader: DriveUploader
    private var files: List<PickedFile> = emptyList()
    private var uploading = false

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startUpload() else showReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setFinishOnTouchOutside(false)
        uploader = DriveUploader(contentResolver)

        files = extractUris(intent).distinct().map(uploader::describe)
        renderFileList()

        binding.btnCancel.setOnClickListener { finish() }
        binding.btnUpload.setOnClickListener { startUpload() }
        binding.btnOpenDrive.setOnClickListener {
            startActivity(MainActivity.intent(this, MainActivity.TAB_DRIVE))
            finish()
        }
        binding.btnDone.setOnClickListener { finish() }

        showReady()
    }

    private fun extractUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }.ifEmpty {
        // Some senders (and our own picker) only populate ClipData.
        val clip = intent.clipData ?: return@ifEmpty emptyList()
        (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    private fun renderFileList() {
        binding.fileList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (f in files) {
            val row = ItemShareFileBinding.inflate(inflater, binding.fileList, false)
            row.name.text = f.name
            row.meta.text = when {
                !f.isAllowed -> getString(R.string.share_unsupported_type, f.extension.ifBlank { "?" })
                f.size >= 0 -> Formatter.formatShortFileSize(this, f.size)
                else -> ""
            }
            row.meta.setTextColor(getColor(if (f.isAllowed) R.color.text_secondary else R.color.error))
            row.icon.setImageResource(if (f.extension == "pdf") R.drawable.ic_pdf else R.drawable.ic_file)
            binding.fileList.addView(row.root)
        }
    }

    /** Client-side check of the same rules the server enforces, so users see why before uploading. */
    private fun validationError(): String? {
        if (files.isEmpty()) return getString(R.string.share_no_files)
        if (files.none { it.isAllowed }) return getString(R.string.share_none_allowed)
        val total = files.filter { it.isAllowed }.sumOf { it.size.coerceAtLeast(0) }
        if (total > Config.DRIVE_MAX_TOTAL_BYTES) return getString(R.string.share_too_large, Formatter.formatShortFileSize(this, total))
        return null
    }

    private fun showReady() {
        uploading = false
        val error = validationError()
        binding.status.isVisible = true
        binding.status.text = error ?: buildString {
            append(getString(R.string.share_ready, files.count { it.isAllowed }))
            if (files.any { !it.isAllowed }) append("\n").append(getString(R.string.share_skipping_unsupported))
        }
        binding.status.setTextColor(getColor(if (error != null) R.color.error else R.color.text_secondary))
        binding.progress.isVisible = false
        binding.btnUpload.isVisible = true
        binding.btnUpload.isEnabled = error == null
        binding.btnCancel.isVisible = true
        binding.resultGroup.isVisible = false
    }

    private fun startUpload() {
        if (uploading || validationError() != null) return
        uploading = true
        binding.btnUpload.isEnabled = false
        binding.btnCancel.isVisible = false
        binding.progress.isVisible = true
        binding.progress.isIndeterminate = false
        binding.progress.progress = 0
        binding.status.setTextColor(getColor(R.color.text_secondary))
        binding.status.text = getString(R.string.share_uploading)

        lifecycleScope.launch {
            val result = uploader.upload(files.filter { it.isAllowed }) { fraction ->
                runOnUiThread { binding.progress.setProgressCompat((fraction * 100).toInt(), true) }
            }
            when (result) {
                is UploadResult.Success -> showSuccess(result)
                UploadResult.NeedLogin -> {
                    uploading = false
                    binding.status.text = getString(R.string.share_need_login)
                    Toast.makeText(this@ShareReceiverActivity, R.string.share_need_login, Toast.LENGTH_SHORT).show()
                    login.launch(LoginActivity.intent(this@ShareReceiverActivity))
                }
                is UploadResult.Failure -> {
                    showReady()
                    binding.status.text = result.message
                    binding.status.setTextColor(getColor(R.color.error))
                }
            }
        }
    }

    private fun showSuccess(result: UploadResult.Success) {
        uploading = false
        binding.progress.isVisible = false
        binding.btnUpload.isVisible = false
        binding.btnCancel.isVisible = false
        binding.resultGroup.isVisible = true
        binding.status.setTextColor(getColor(R.color.success))
        binding.status.text = if (result.zipped) getString(R.string.share_done_zipped) else getString(R.string.share_done, result.files.size)

        binding.fileList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (f in result.files) {
            val row = ItemShareFileBinding.inflate(inflater, binding.fileList, false)
            row.name.text = f.name
            row.meta.text = f.shortUrl
            row.icon.setImageResource(R.drawable.ic_link)
            row.copy.visibility = View.VISIBLE
            row.copy.setOnClickListener { copyLink(f.shortUrl) }
            binding.fileList.addView(row.root)
        }
    }

    private fun copyLink(url: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("극플드라이브 링크", url))
        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun finish() {
        if (uploading) {
            Toast.makeText(this, R.string.share_wait, Toast.LENGTH_SHORT).show()
            return
        }
        super.finish()
    }

    companion object {
        /** Routes files picked inside the app through the same upload screen. */
        fun intent(context: Context, uris: List<Uri>): Intent {
            val clip = ClipData.newRawUri("files", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            return Intent(context, ShareReceiverActivity::class.java)
                .setAction(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .also { it.clipData = clip }
        }
    }
}
