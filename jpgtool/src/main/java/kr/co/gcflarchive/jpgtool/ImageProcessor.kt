package kr.co.gcflarchive.jpgtool

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo the user picked. */
data class InputImage(val uri: Uri, val name: String, val size: Long, val mime: String?)

data class Output(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    /** JPEG quality used, or null when the original file was kept as is. */
    val quality: Int?,
    /** False when even the smallest attempt is bigger than the target. */
    val fits: Boolean,
)

object ImageProcessor {
    /** Bigger photos are decoded at a reduced size so the app never runs out of memory. */
    private const val MAX_DECODE_PIXELS = 30_000_000L
    const val SAVE_FOLDER = "JPG Tool"

    fun describe(context: Context, uri: Uri): InputImage {
        var name = "photo"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }?.let { name = it }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        return InputImage(uri, name, size, context.contentResolver.getType(uri))
    }

    /**
     * Decodes [uri] upright (EXIF rotation applied) onto a white, mutable ARGB bitmap,
     * with its longest side at most [maxSide] (0 = full resolution).
     */
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Cannot open file")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")

        var sample = 1
        fun tooBig(s: Int): Boolean {
            val w = bounds.outWidth / s
            val h = bounds.outHeight / s
            return w.toLong() * h > MAX_DECODE_PIXELS || (maxSide > 0 && max(w, h) >= maxSide * 2)
        }
        while (tooBig(sample)) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Cannot decode image")

        val exif = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (_: Exception) {
            null
        }
        val matrix = Matrix()
        if (exif != null) {
            if (exif.isFlipped) matrix.postScale(-1f, 1f)
            if (exif.rotationDegrees != 0) matrix.postRotate(exif.rotationDegrees.toFloat())
        }
        val rotated = exif != null && (exif.rotationDegrees % 180 != 0)
        val uprightW = if (rotated) raw.height else raw.width
        val uprightH = if (rotated) raw.width else raw.height
        val fit = SizeSearch.fitFactor(uprightW, uprightH, maxSide)
        matrix.postScale(fit, fit)

        val outW = max(1, (uprightW * fit).roundToInt())
        val outH = max(1, (uprightH * fit).roundToInt())
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE) // transparent PNGs would turn black as JPG
        // Move the transformed image back into the visible area.
        val mapped = android.graphics.RectF(0f, 0f, raw.width.toFloat(), raw.height.toFloat())
        matrix.mapRect(mapped)
        matrix.postTranslate(-mapped.left, -mapped.top)
        canvas.drawBitmap(raw, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        raw.recycle()
        return out
    }

    fun loadLogo(file: File): Bitmap? =
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null

    /** Copies the picked logo into app storage, so it is still there next time the app opens. */
    fun saveLogo(context: Context, uri: Uri, file: File): Bitmap {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IOException("Cannot read logo")
        // Keep it reasonably small; it is scaled to the photo anyway.
        val f = SizeSearch.fitFactor(bmp.width, bmp.height, 2048)
        val logo = if (f < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * f).roundToInt(), (bmp.height * f).roundToInt(), true) else bmp
        file.outputStream().use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return logo
    }

    /** Draws [logo] on [photo] (which must be mutable) according to [s]. */
    fun applyWatermark(photo: Bitmap, logo: Bitmap, s: Settings) {
        val box = WatermarkLayout.place(photo.width, photo.height, logo.width, logo.height, s.anchor, s.logoPercent, s.marginPercent)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (s.opacity.coerceIn(0f, 1f) * 255).roundToInt()
        }
        Canvas(photo).drawBitmap(logo, null, Rect(box.left, box.top, box.left + box.width, box.top + box.height), paint)
    }

    private fun encode(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /**
     * Finds the best-looking JPG of [photo] that is at most [target] bytes: highest quality first,
     * and only if even the lowest quality is too big, fewer pixels. Recycles [photo].
     */
    fun compress(photo: Bitmap, target: Long): Output {
        var bmp = photo
        var smallest: Output
        var attempt = 0
        while (true) {
            val cache = HashMap<Int, ByteArray>()
            val q = SizeSearch.highestQualityFitting(target) { quality ->
                cache.getOrPut(quality) { encode(bmp, quality) }.size.toLong()
            }
            if (q != null) {
                return Output(cache.getValue(q), bmp.width, bmp.height, q, fits = true).also { bmp.recycle() }
            }
            val atMin = cache[SizeSearch.MIN_QUALITY] ?: encode(bmp, SizeSearch.MIN_QUALITY)
            smallest = Output(atMin, bmp.width, bmp.height, SizeSearch.MIN_QUALITY, fits = false)
            if (++attempt >= 8 || bmp.width <= Settings.MIN_SIDE || bmp.height <= Settings.MIN_SIDE) break
            val f = SizeSearch.downscaleFactor(atMin.size.toLong(), target)
            val next = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * f).roundToInt()), max(1, (bmp.height * f).roundToInt()), true)
            bmp.recycle()
            bmp = next
        }
        bmp.recycle()
        return smallest
    }

    /** The whole job for one photo. */
    fun process(context: Context, input: InputImage, s: Settings, logo: Bitmap?): Output {
        val target = s.targetBytes ?: throw IllegalArgumentException("Invalid target size")
        val maxSide = s.maxSide ?: 0
        val isJpeg = input.mime == "image/jpeg" || input.name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") }
        val untouched = !(s.watermark && logo != null) && maxSide == 0
        if (s.keepIfSmaller && untouched && isJpeg && input.size in 1..target) {
            val bytes = context.contentResolver.openInputStream(input.uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot open file")
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
            return Output(bytes, b.outWidth, b.outHeight, quality = null, fits = true)
        }
        val photo = decode(context, input.uri, maxSide)
        if (s.watermark && logo != null) applyWatermark(photo, logo, s)
        return compress(photo, target)
    }

    fun outputName(original: String): String =
        original.substringBeforeLast('.').ifBlank { "photo" } + "_small.jpg"

    /** Saves into Pictures/JPG Tool and returns the new file's content Uri. */
    fun save(context: Context, name: String, bytes: ByteArray): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SAVE_FOLDER")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create file")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Cannot write file")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVE_FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create folder")
        var file = File(dir, name)
        var n = 1
        while (file.exists()) file = File(dir, name.removeSuffix(".jpg") + " (${n++}).jpg")
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("image/jpeg"), null)
        return Uri.fromFile(file)
    }
}
