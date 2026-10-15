package kr.co.gcflarchive.admin.features.content

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.features.jsonArray
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.features.strings
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * 공지: 학생 앱 '공지'와 웹 공지사항에 올라가요. 커뮤니티 게시판 관리 화면과 같은 방식으로
 * 목록에서 누르면 수정, 밀면 삭제예요. 올릴 때 '푸시 알림 보내기'를 고르면 학생 앱에 알림이 가요.
 * 사진은 커뮤니티 사진처럼 서버가 위치 정보를 지우고 크기를 줄여 저장해요.
 */
class NoticesFragment : ListFragment() {
    override val fab get() = getString(R.string.notice_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)
    override val emptyText get() = getString(R.string.notice_empty)
    override val searchHint get() = getString(R.string.notice_search_hint)

    /** The photo strip of the form that is open now (picker results go here). */
    private var strip: PhotoStrip? = null

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(NoticeAdmin.PHOTOS_MAX)) { uris ->
        val target = strip ?: return@registerForActivityResult
        val room = NoticeAdmin.PHOTOS_MAX - target.ids.size
        if (uris.isNotEmpty() && room > 0) target.upload(uris.take(room))
    }

    override suspend fun load(): List<Row> {
        val notices = JSONArray(fetch("/api/notices").body).objects()
        setSummary(getString(R.string.notice_summary, notices.size))
        return notices.map { n ->
            Row(
                id = n.optInt("id").toString(),
                title = n.str("title"),
                subtitle = NoticeAdmin.preview(n.str("content")).ifBlank { null },
                meta = getString(R.string.notice_meta, n.str("date"), n.optInt("views")),
                badges = listOfNotNull(
                    if (n.optBoolean("isCritical")) getString(R.string.notice_critical_badge) to Badge.DANGER else null,
                    if (n.str("pushAt").isNotBlank()) getString(R.string.notice_pushed_badge) to Badge.INFO else null,
                    if (!n.optBoolean("editableInApp", true)) getString(R.string.notice_web_badge) to Badge.NEUTRAL else null,
                ),
                trailing = Trailing.Chevron,
                swipeable = true,
                payload = n,
            )
        }
    }

    override fun onFab() {
        if (guardWrite()) edit(null)
    }

    override fun onRowClick(row: Row) {
        val n = row.payload as? JSONObject ?: return
        if (!n.optBoolean("editableInApp", true)) {
            Dialogs.toast(requireContext(), getString(R.string.notice_web_only))
            return
        }
        if (guardWrite()) edit(n)
    }

    private fun edit(notice: JSONObject?) {
        val photos = PhotoStrip(notice?.optJSONArray("imageIds").strings())
        strip = photos
        FormSheet(
            requireActivity(),
            getString(if (notice == null) R.string.notice_add else R.string.notice_edit),
            getString(R.string.notice_form_desc),
        )
            .text("title", getString(R.string.notice_title), notice?.str("title").orEmpty(), required = true,
                validate = NoticeAdmin::titleError)
            .text("text", getString(R.string.notice_text), notice?.str("text").orEmpty(), multiline = true,
                hint = getString(R.string.notice_text_hint))
            .view(photos.view)
            .switch("isCritical", getString(R.string.notice_critical), notice?.optBoolean("isCritical") ?: false,
                getString(R.string.notice_critical_desc))
            .switch("push", getString(if (notice == null) R.string.notice_push else R.string.notice_push_again), false,
                getString(R.string.notice_push_desc))
            .submitText(getString(if (notice == null) R.string.notice_submit else R.string.save))
            .onDismiss { if (strip === photos) strip = null }
            .show { v ->
                if (photos.uploading > 0) throw IllegalStateException(getString(R.string.notice_photo_wait))
                NoticeAdmin.bodyError(v.text("text"), photos.ids.size)?.let { throw IllegalArgumentException(it) }
                val body = JSONObject()
                    .put("title", v.text("title"))
                    .put("text", v.text("text"))
                    .put("images", jsonArray(photos.ids))
                    .put("isCritical", v.bool("isCritical"))
                    .put("push", v.bool("push"))
                val res = if (notice == null) api.postJson("/api/notices", body)
                else api.putJson("/api/notices/${notice.optInt("id")}", body)
                Dialogs.toast(
                    requireContext(),
                    getString(if (res.optBoolean("pushed")) R.string.notice_saved_pushed else R.string.notice_saved),
                )
                reload()
            }
    }

    override fun onSwipe(row: Row) {
        val n = row.payload as? JSONObject ?: return
        Dialogs.confirm(
            requireContext(),
            getString(R.string.notice_delete_title, row.title),
            getString(R.string.notice_delete_desc),
            getString(R.string.delete),
        ) {
            act(getString(R.string.deleted)) { api.deleteJson("/api/notices/${n.optInt("id")}") }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** 사진 붙이기: thumbnails with ✕, an add button, uploads to /api/notices/images. */
    private inner class PhotoStrip(initial: List<String>) {
        val ids = initial.toMutableList()
        var uploading = 0
        private val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        private val scroll = HorizontalScrollView(requireContext()).apply { addView(row); isHorizontalScrollBarEnabled = false }
        private val label = TextView(requireContext()).apply { setTextAppearance(R.style.TextAppearance_Krds_DetailValue) }
        private val add = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setIconResource(R.drawable.ic_photo)
            setOnClickListener { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        val view = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(add, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        init {
            render()
        }

        fun upload(uris: List<Uri>) {
            viewLifecycleOwner.lifecycleScope.launch {
                for (uri in uris) {
                    uploading++
                    render()
                    runCatching {
                        val (name, bytes, mime) = withContext(Dispatchers.IO) { prepare(uri) }
                        val res = api.postMultipart("/api/notices/images", emptyMap(),
                            listOf(Triple("file", name, bytes.toRequestBody(mime.toMediaType()))))
                        res.optJSONObject("image")?.str("id")?.ifBlank { null } ?: throw IllegalStateException(getString(R.string.notice_photo_failed))
                    }.onSuccess { ids += it }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            Dialogs.toast(requireContext(), Dialogs.messageOf(requireContext(), e))
                        }
                    uploading--
                    render()
                }
            }
        }

        fun render() {
            label.text = getString(R.string.notice_photo_label, ids.size, NoticeAdmin.PHOTOS_MAX)
            row.removeAllViews()
            val size = dp(88)
            for (id in ids.toList()) {
                val frame = FrameLayout(requireContext())
                val thumb = ImageView(requireContext()).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(requireContext().getColor(R.color.krds_surface_raised))
                    contentDescription = getString(R.string.notice_photo_desc, ids.indexOf(id) + 1)
                }
                frame.addView(thumb, FrameLayout.LayoutParams(size, size))
                frame.addView(ImageButton(requireContext()).apply {
                    setImageResource(R.drawable.ic_close)
                    setColorFilter(android.graphics.Color.WHITE)
                    setBackgroundColor(0x8C000000.toInt())
                    contentDescription = getString(R.string.notice_photo_remove)
                    setOnClickListener { ids.remove(id); render() }
                }, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
                row.addView(frame, LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8) })
                viewLifecycleOwner.lifecycleScope.launch {
                    val bmp = runCatching {
                        val bytes = api.bytes(NoticeAdmin.imageUrl(id))
                        withContext(Dispatchers.Default) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })
                        }
                    }.getOrNull()
                    if (bmp != null) thumb.setImageBitmap(bmp)
                }
            }
            scroll.isVisible = ids.isNotEmpty()
            add.isEnabled = uploading == 0 && ids.size < NoticeAdmin.PHOTOS_MAX
            add.text = when {
                uploading > 0 -> getString(R.string.notice_photo_uploading)
                ids.size >= NoticeAdmin.PHOTOS_MAX -> getString(R.string.notice_photo_full, NoticeAdmin.PHOTOS_MAX)
                else -> getString(R.string.notice_photo_add)
            }
        }

        /** JPG/PNG/GIF/WEBP as they are; HEIC and others become JPEG (Android 9+). */
        private fun prepare(uri: Uri): Triple<String, ByteArray, String> {
            val resolver = requireContext().contentResolver
            val type = resolver.getType(uri).orEmpty()
            val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException(getString(R.string.notice_photo_failed))
            val asIs = Triple("photo.${type.substringAfter('/', "jpg").replace("jpeg", "jpg")}", raw, type.ifBlank { "application/octet-stream" })
            if (type in setOf("image/jpeg", "image/png", "image/gif", "image/webp")) return asIs
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return asIs
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(raw))) { decoder, info, _ ->
                val scale = minOf(1f, 1600f / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            return Triple("photo.jpg", out.toByteArray(), "image/jpeg")
        }
    }
}
