package kr.co.gcflarchive.jpgtool

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where the logo sits: a 3×3 grid. [h] and [v] are 0 (left/top), 0.5 (center) or 1 (right/bottom). */
enum class Anchor(val h: Float, val v: Float) {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), CENTER_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f),
}

enum class SizeUnit(val bytes: Long) { KB(1024), MB(1024 * 1024) }

data class Settings(
    /** Target file size as typed, e.g. "500" or "1.5". */
    val targetText: String = "500",
    val unit: SizeUnit = SizeUnit.KB,
    /** If a JPG is already small enough (and nothing else changes it), save it untouched. */
    val keepIfSmaller: Boolean = true,
    /** Longest side in pixels as typed; blank = keep the original resolution. */
    val maxSideText: String = "",
    val watermark: Boolean = false,
    val anchor: Anchor = Anchor.BOTTOM_RIGHT,
    /** Logo width as % of the photo width. */
    val logoPercent: Float = 20f,
    /** 0 = invisible, 1 = fully opaque. */
    val opacity: Float = 0.7f,
    /** Gap from the edges as % of the photo's shorter side. */
    val marginPercent: Float = 3f,
) {
    /** Target in bytes, or null when the field is not a positive number. */
    val targetBytes: Long?
        get() = targetText.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it > 0 && it.isFinite() }
            ?.let { (it * unit.bytes).toLong() }
            ?.takeIf { it >= MIN_TARGET_BYTES }

    /** Max longest side, 0 = no limit, null = invalid. */
    val maxSide: Int?
        get() = maxSideText.trim().let { t ->
            if (t.isEmpty()) 0 else t.toIntOrNull()?.takeIf { it >= MIN_SIDE }
        }

    companion object {
        /** Below ~5 KB even a thumbnail barely fits; refuse instead of producing mush. */
        const val MIN_TARGET_BYTES = 5L * 1024
        const val MIN_SIDE = 64
    }
}

data class Box(val left: Int, val top: Int, val width: Int, val height: Int)

object WatermarkLayout {
    /**
     * Where to draw a [logoW]×[logoH] logo on a [imgW]×[imgH] photo. The logo keeps its aspect
     * ratio, is [logoPercent]% of the photo width (but never taller than the photo minus margins),
     * and sits [marginPercent]% of the shorter side away from the edges it is anchored to.
     */
    fun place(
        imgW: Int, imgH: Int, logoW: Int, logoH: Int,
        anchor: Anchor, logoPercent: Float, marginPercent: Float,
    ): Box {
        require(imgW > 0 && imgH > 0 && logoW > 0 && logoH > 0)
        val margin = (min(imgW, imgH) * marginPercent / 100f).roundToInt()
        val freeW = max(1, imgW - 2 * margin)
        val freeH = max(1, imgH - 2 * margin)
        var w = imgW * logoPercent / 100f
        var h = w * logoH / logoW
        if (w > freeW) { h *= freeW / w; w = freeW.toFloat() }
        if (h > freeH) { w *= freeH / h; h = freeH.toFloat() }
        val wi = max(1, w.roundToInt())
        val hi = max(1, h.roundToInt())
        val left = margin + ((freeW - wi) * anchor.h).roundToInt()
        val top = margin + ((freeH - hi) * anchor.v).roundToInt()
        return Box(left, top, wi, hi)
    }
}

object SizeSearch {
    const val MIN_QUALITY = 5
    const val MAX_QUALITY = 100

    /**
     * Highest JPEG quality in [lo]..[hi] whose encoded size is ≤ [limit], found by binary search
     * (JPEG size grows with quality). Returns null when even [lo] is too big.
     * [sizeAt] encodes at a quality and returns the byte count; it is called about 7 times.
     */
    fun highestQualityFitting(limit: Long, lo: Int = MIN_QUALITY, hi: Int = MAX_QUALITY, sizeAt: (Int) -> Long): Int? {
        var a = lo
        var b = hi
        var best: Int? = null
        while (a <= b) {
            val mid = (a + b) ushr 1
            if (sizeAt(mid) <= limit) { best = mid; a = mid + 1 } else b = mid - 1
        }
        return best
    }

    /**
     * When the lowest quality is still [sizeAtMin] bytes and we need [limit], shrink width and height
     * by this factor (file size scales roughly with pixel count, so with the square of the factor).
     */
    fun downscaleFactor(sizeAtMin: Long, limit: Long): Float {
        val f = sqrt(limit.toDouble() / sizeAtMin) * 0.9
        return f.coerceIn(0.25, 0.9).toFloat()
    }

    /** Factor to fit the longest side into [maxSide] (1 = no change). */
    fun fitFactor(w: Int, h: Int, maxSide: Int): Float =
        if (maxSide <= 0 || max(w, h) <= maxSide) 1f else maxSide.toFloat() / max(w, h)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
