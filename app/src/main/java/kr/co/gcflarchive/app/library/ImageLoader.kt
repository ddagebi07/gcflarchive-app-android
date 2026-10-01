package kr.co.gcflarchive.app.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.data.SiteApi

/**
 * Minimal image loader for thumbnails and viewer pages: memory cache + downsampling.
 * Requests carry the site cookie (needed for the protected PDF page images).
 */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    /** [lowMemory] decodes as RGB_565 (half the memory; fine for document pages). */
    suspend fun load(url: String, targetWidth: Int, lowMemory: Boolean = false): Bitmap? {
        cache.get(url)?.let { return it }
        val bytes = runCatching { SiteApi.bytes(url) }.getOrNull() ?: return null
        val bitmap = withContext(Dispatchers.Default) { decode(bytes, targetWidth, lowMemory) } ?: return null
        cache.put(url, bitmap)
        return bitmap
    }

    private fun decode(bytes: ByteArray, targetWidth: Int, lowMemory: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        if (targetWidth > 0) while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            if (lowMemory) inPreferredConfig = Bitmap.Config.RGB_565
        })
    }

    /** Loads [url] into [view], ignoring results that arrive after the view was rebound. */
    fun into(view: ImageView, url: String?, scope: LifecycleCoroutineScope, targetWidth: Int = 600) {
        (view.getTag(R.id.image_loader_job) as? Job)?.cancel()
        view.setTag(R.id.image_loader_url, url)
        if (url.isNullOrBlank()) {
            view.setImageDrawable(null)
            return
        }
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        val job = scope.launch {
            val bitmap = load(url, targetWidth)
            if (view.getTag(R.id.image_loader_url) == url && bitmap != null) view.setImageBitmap(bitmap)
        }
        view.setTag(R.id.image_loader_job, job)
    }
}
