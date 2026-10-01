package kr.co.gcflarchive.admin.core

/**
 * Objective answers for the 40-question grid, in the server's text format
 * (answer_sheet_pdf.parse_objective_answers): digits 1–9 per question, "(23)" for
 * several correct answers, "?" for unknown, spaces only group visually.
 */
object AnswerGrid {
    const val QUESTIONS = 40
    const val MAX_ESSAYS = 10

    /** Each cell is "" (blank), "?" or a sorted digit string like "3" / "24". */
    fun parse(text: String): List<String> {
        val out = mutableListOf<String>()
        var src = text.trim()
        if (src.endsWith(".")) src = src.dropLast(1).trimEnd()
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            when {
                ch.isWhitespace() -> i++
                ch in "123456789?" -> { out += ch.toString(); i++ }
                ch == '(' -> {
                    val end = src.indexOf(')', i + 1)
                    require(end > 0) { "괄호가 닫히지 않았습니다." }
                    out += src.substring(i + 1, end).filter { it in "123456789" }.toSet().sorted().joinToString("")
                    i = end + 1
                }
                else -> throw IllegalArgumentException("사용할 수 없는 문자입니다: '$ch'")
            }
        }
        return (out + List((QUESTIONS - out.size).coerceAtLeast(0)) { "" }).take(QUESTIONS)
    }

    /**
     * Grid → server text. Trailing blanks are dropped; a blank in the middle becomes
     * "?" so the following questions keep their numbers.
     */
    fun serialize(cells: List<String>): String {
        val last = cells.indexOfLast { it.isNotEmpty() }
        if (last < 0) return ""
        return cells.take(last + 1).map { cell ->
            val digits = cell.filter { it in "123456789" }.toSet().sorted().joinToString("")
            when {
                cell == "?" || digits.isEmpty() -> "?"
                digits.length == 1 -> digits
                else -> "($digits)"
            }
        }.chunked(5).joinToString(" ") { it.joinToString("") }
    }

    /** Toggles [choice] (1–5) in a cell; "?" is replaced by the choice. */
    fun toggle(cell: String, choice: Int): String {
        val set = cell.filter { it in "123456789" }.toHashSet()
        val c = choice.digitToChar()
        if (!set.remove(c)) set += c
        return set.sorted().joinToString("")
    }
}

/** RFC 4180-ish CSV with columns in first-seen order. */
object Csv {
    fun build(rows: List<Map<String, String>>, preferred: List<String> = emptyList()): String {
        val columns = LinkedHashSet<String>()
        preferred.forEach { col -> if (rows.any { it.containsKey(col) }) columns += col }
        rows.forEach { columns.addAll(it.keys) }
        val sb = StringBuilder("﻿") // BOM so Excel opens Korean text correctly
        sb.append(columns.joinToString(",") { esc(it) }).append("\r\n")
        rows.forEach { row -> sb.append(columns.joinToString(",") { esc(row[it].orEmpty()) }).append("\r\n") }
        return sb.toString()
    }

    private fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

/** Client-side check matching what /api/blocked-ips accepts (server normalizes further). */
object IpBand {
    private val v4 = Regex("""^(\d{1,3})(\.\d{1,3}){3}$""")
    private val v4Cidr = Regex("""^(\d{1,3})(\.\d{1,3}){3}/(\d{1,2})$""")
    private val v4Prefix = Regex("""^(\d{1,3}\.){1,3}$""")
    private val v6 = Regex("""^[0-9a-fA-F:]+(/\d{1,3})?$""")

    fun isValid(input: String): Boolean {
        val s = input.trim()
        if (s.isEmpty()) return false
        if (v4.matches(s)) return octetsOk(s)
        if (v4Cidr.matches(s)) return octetsOk(s.substringBefore('/')) && s.substringAfter('/').toInt() in 0..32
        if (v4Prefix.matches(s)) return octetsOk(s.trimEnd('.'))
        if (s.contains(':') && v6.matches(s)) return s.count { it == ':' } >= 1
        return false
    }

    private fun octetsOk(s: String) = s.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }

    /** Bands the server added itself (record_suspicious_access_and_autoblock). */
    fun isAuto(reason: String): Boolean = reason.trimStart().startsWith("Auto-blocked", ignoreCase = true)
}

/** "2026-10-01T03:04:05.123Z" → "2026.10.01 03:04" (input time zone kept). */
fun shortTime(iso: String): String {
    if (iso.length < 16) return iso
    return iso.take(10).replace('-', '.') + " " + iso.substring(11, 16)
}
