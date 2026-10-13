package kr.co.gcflarchive.core.util

import kotlin.math.abs
import kotlin.math.roundToLong

/** "%.{digits}f" without java.lang.String.format (works on iOS). */
fun Double.fixed(digits: Int): String {
    if (isNaN()) return "-"
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val scaled = (abs(this) * factor).roundToLong()
    val intPart = scaled / factor
    val frac = scaled % factor
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    return if (digits == 0) "$sign$intPart" else "$sign$intPart.${frac.toString().padStart(digits, '0')}"
}

/** 12.0 → "12", 12.5 → "12.5". */
fun Double.trimmed(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "-"
    val units = listOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else "${v.fixed(1)} ${units[i]}"
}

/**
 * Korean-friendly ordering: Hangul syllables are encoded in 가나다 order, so a
 * case-insensitive code-point comparison matches the web's localeCompare('ko') for
 * the titles and subjects we sort.
 */
val KoreanOrder: Comparator<String> = Comparator { a, b -> a.lowercase().compareTo(b.lowercase()) }

/** "2026-09-15T03:00:00Z" → "2026.09.15" */
fun displayDate(iso: String): String = iso.take(10).replace('-', '.')
