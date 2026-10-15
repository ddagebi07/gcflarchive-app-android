package kr.co.gcflarchive.app.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Turns picked photos into uploadable files. The server strips metadata and resizes again. */
object CommunityPhotos {
    /** routes/community.py IMAGES_PER_POST */
    const val MAX_PER_POST = 10
    private const val PASS_THROUGH_BYTES = 15 * 1024 * 1024
    private const val MAX_SIDE = 1600
    private val SERVER_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

    class Photo(val name: String, val bytes: ByteArray, val mimeType: String?)

    /** JPG/PNG/GIF/WEBP go as they are; other formats (HEIC) become JPEG on Android 9+. Call off the main thread. */
    fun prepare(ctx: Context, uri: Uri, n: Int): Photo? {
        val type = ctx.contentResolver.getType(uri).orEmpty()
        val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (type in SERVER_TYPES && raw.size <= PASS_THROUGH_BYTES) {
            return Photo("photo$n.${type.substringAfter('/').replace("jpeg", "jpg")}", raw, type)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return Photo("photo$n", raw, type.ifBlank { null })
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(raw))) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
            decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return Photo("photo$n.jpg", out.toByteArray(), "image/jpeg")
    }
}
