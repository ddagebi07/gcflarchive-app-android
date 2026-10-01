package kr.co.gcflarchive.admin.features.content

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.fragment.app.FragmentActivity
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminApi
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

/**
 * 간편 업로드: one PDF (file picker or share sheet) or one YouTube URL via POST /upload,
 * the same endpoint as the web admin. 대량 업로드는 웹.
 */
object Upload {
    fun pdfForm(activity: FragmentActivity, uri: Uri, onClose: (() -> Unit)? = null, onDone: () -> Unit): FormSheet {
        val name = displayName(activity, uri)
        return FormSheet(activity, activity.getString(R.string.upload_pdf_title), name)
            .apply { onClose?.let { onDismiss(it) } }
            .text("category", activity.getString(R.string.doc_category), "기출", hint = activity.getString(R.string.doc_category_hint))
            .text("collectionName", activity.getString(R.string.doc_collection))
            .text("tags", activity.getString(R.string.doc_tags), hint = activity.getString(R.string.doc_tags_hint))
            .text("notes", activity.getString(R.string.memo), multiline = true)
            .switch("public", activity.getString(R.string.doc_public), true)
            .switch("requireVerification", activity.getString(R.string.doc_require_verification), true)
            .switch("compressPdf", activity.getString(R.string.upload_compress), false)
            .submitText(activity.getString(R.string.upload))
            .show { v ->
                val fields = mapOf(
                    "archiveType" to "pdf",
                    "category" to v.text("category"),
                    "collectionName" to v.text("collectionName"),
                    "tags" to v.text("tags"),
                    "notes" to v.text("notes"),
                    "visibility" to if (v.bool("public")) "public" else "private",
                    "requireVerification" to v.bool("requireVerification").toString(),
                    "compressPdf" to v.bool("compressPdf").toString(),
                )
                AdminApi.get(activity).postMultipart("/upload", fields, listOf(Triple("files", name, uriBody(activity, uri))))
                onDone()
            }
    }

    fun youtubeForm(activity: FragmentActivity, initialUrl: String = "", onClose: (() -> Unit)? = null, onDone: () -> Unit): FormSheet {
        return FormSheet(activity, activity.getString(R.string.upload_video_title))
            .apply { onClose?.let { onDismiss(it) } }
            .text("youtubeUrl", activity.getString(R.string.upload_youtube_url), initialUrl, required = true,
                validate = { if (Regex("""(youtu\.be/|youtube\.com/)""").containsMatchIn(it)) null else activity.getString(R.string.upload_youtube_invalid) })
            .text("collectionName", activity.getString(R.string.upload_video_name), required = true)
            .text("category", activity.getString(R.string.doc_category), "행사")
            .text("tags", activity.getString(R.string.doc_tags), hint = activity.getString(R.string.doc_tags_hint))
            .text("notes", activity.getString(R.string.memo), multiline = true)
            .switch("public", activity.getString(R.string.doc_public), true)
            .submitText(activity.getString(R.string.upload))
            .show { v ->
                AdminApi.get(activity).postMultipart(
                    "/upload",
                    mapOf(
                        "archiveType" to "video",
                        "youtubeUrl" to v.text("youtubeUrl"),
                        "collectionName" to v.text("collectionName"),
                        "category" to v.text("category"),
                        "tags" to v.text("tags"),
                        "notes" to v.text("notes"),
                        "visibility" to if (v.bool("public")) "public" else "private",
                    ),
                    emptyList(),
                )
                onDone()
            }
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "upload.pdf"
    }

    private fun uriBody(context: Context, uri: Uri): RequestBody = object : RequestBody() {
        override fun contentType() = "application/pdf".toMediaType()
        override fun writeTo(sink: BufferedSink) {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("파일을 열 수 없습니다.")
            input.source().use { sink.writeAll(it) }
        }
    }
}
