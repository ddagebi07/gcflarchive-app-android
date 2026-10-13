package kr.co.gcflarchive.core.html

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Inline HTML (one [HtmlBlock.Text] or a table cell) flattened to plain text plus style
 * ranges, so every platform's UI can draw it without a browser engine.
 */
data class StyledText(val text: String, val spans: List<StyledSpan>)

data class StyledSpan(val start: Int, val end: Int, val style: SpanStyleKind)

sealed interface SpanStyleKind {
    data object Bold : SpanStyleKind
    data object Italic : SpanStyleKind
    data object Underline : SpanStyleKind
    data object Strike : SpanStyleKind
    data object Monospace : SpanStyleKind
    /** h1…h6: relative size (1.0 = body). */
    data class Scale(val factor: Float) : SpanStyleKind
    data class Link(val url: String) : SpanStyleKind
    /** 0xAARRGGBB */
    data class Color(val argb: Long) : SpanStyleKind
    data class Background(val argb: Long) : SpanStyleKind
}

object RichText {
    private val BLOCKS = setOf(
        "p", "div", "section", "article", "header", "footer", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "figure", "figcaption", "center", "pre", "dl", "dt", "dd", "address", "hr",
    )

    fun parse(html: String, baseUrl: String = ""): StyledText {
        val doc = Ksoup.parseBodyFragment(html, baseUrl)
        val b = Builder()
        b.children(doc.body())
        return b.build()
    }

    private class Builder {
        val sb = StringBuilder()
        val spans = mutableListOf<StyledSpan>()
        private var pre = 0
        private val listCounters = ArrayDeque<Int?>()

        fun build(): StyledText {
            // Trim trailing / leading blank lines while keeping span offsets valid.
            var end = sb.length
            while (end > 0 && sb[end - 1].isWhitespace()) end--
            var start = 0
            while (start < end && sb[start].isWhitespace()) start++
            val text = sb.substring(start, end)
            val shifted = spans.mapNotNull { s ->
                val a = (s.start - start).coerceIn(0, text.length)
                val z = (s.end - start).coerceIn(0, text.length)
                if (z > a) StyledSpan(a, z, s.style) else null
            }
            return StyledText(text, shifted)
        }

        fun children(el: Element) {
            for (n: Node in el.childNodes()) node(n)
        }

        private fun node(n: Node) {
            when (n) {
                is TextNode -> text(n.getWholeText())
                is Element -> element(n)
            }
        }

        private fun text(raw: String) {
            if (pre > 0) {
                sb.append(raw)
                return
            }
            val collapsed = raw.replace(' ', ' ').replace(Regex("""[ \t\r\n\f]+"""), " ")
            if (collapsed == " " && (sb.isEmpty() || sb.last() == ' ' || sb.last() == '\n')) return
            val t = if (sb.isEmpty() || sb.last() == '\n' || sb.last() == ' ') collapsed.trimStart() else collapsed
            sb.append(t)
        }

        private fun newline(count: Int) {
            // Drop trailing spaces before a break.
            while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
            if (sb.isEmpty()) return
            var existing = 0
            var i = sb.length - 1
            while (i >= 0 && sb[i] == '\n') { existing++; i-- }
            repeat((count - existing).coerceAtLeast(0)) { sb.append('\n') }
        }

        private fun element(el: Element) {
            val tag = el.normalName()
            when (tag) {
                "script", "style", "noscript", "head", "img", "iframe", "video", "embed" -> return
                "br" -> { while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1); sb.append('\n'); return }
                "hr" -> { newline(1); sb.append("────────"); newline(1); return }
            }
            val block = tag in BLOCKS
            if (block) newline(if (tag == "li" || tag == "dd" || tag == "dt") 1 else 2)
            if (tag == "ul" || tag == "ol") listCounters.addLast(if (tag == "ol") 0 else null)
            if (tag == "li") {
                val counter = listCounters.lastOrNull()
                if (counter != null) {
                    listCounters[listCounters.lastIndex] = counter + 1
                    sb.append("${counter + 1}. ")
                } else {
                    sb.append("• ")
                }
            }
            if (tag == "pre") pre++
            val start = sb.length
            children(el)
            val end = sb.length
            if (tag == "pre") pre--
            if (tag == "ul" || tag == "ol") listCounters.removeLastOrNull()
            if (end > start) styles(el, tag).forEach { spans += StyledSpan(start, end, it) }
            if (block) newline(if (tag == "li" || tag == "dd" || tag == "dt") 1 else 2)
        }

        private fun styles(el: Element, tag: String): List<SpanStyleKind> = buildList {
            when (tag) {
                "b", "strong", "th" -> add(SpanStyleKind.Bold)
                "i", "em", "cite", "dfn" -> add(SpanStyleKind.Italic)
                "u", "ins" -> add(SpanStyleKind.Underline)
                "s", "strike", "del" -> add(SpanStyleKind.Strike)
                "code", "tt", "kbd", "pre", "samp" -> add(SpanStyleKind.Monospace)
                "h1" -> { add(SpanStyleKind.Bold); add(SpanStyleKind.Scale(1.5f)) }
                "h2" -> { add(SpanStyleKind.Bold); add(SpanStyleKind.Scale(1.35f)) }
                "h3" -> { add(SpanStyleKind.Bold); add(SpanStyleKind.Scale(1.2f)) }
                "h4", "h5", "h6" -> add(SpanStyleKind.Bold)
                "mark" -> add(SpanStyleKind.Background(0xFFFFF3B0))
                "a" -> {
                    val href = el.absUrl("href").ifBlank { el.attr("href") }
                    if (href.isNotBlank() && !href.startsWith("javascript:", ignoreCase = true) && !href.startsWith("#")) add(SpanStyleKind.Link(href))
                }
                "font" -> parseColor(el.attr("color"))?.let { add(SpanStyleKind.Color(it)) }
            }
            val style = el.attr("style")
            if (style.isNotBlank()) {
                style.split(';').forEach { decl ->
                    val key = decl.substringBefore(':').trim().lowercase()
                    val value = decl.substringAfter(':', "").trim().lowercase()
                    when (key) {
                        "font-weight" -> if (value == "bold" || value == "bolder" || (value.toIntOrNull() ?: 0) >= 600) add(SpanStyleKind.Bold)
                        "font-style" -> if (value == "italic" || value == "oblique") add(SpanStyleKind.Italic)
                        "text-decoration", "text-decoration-line" -> {
                            if ("underline" in value) add(SpanStyleKind.Underline)
                            if ("line-through" in value) add(SpanStyleKind.Strike)
                        }
                        "color" -> parseColor(value)?.let { add(SpanStyleKind.Color(it)) }
                        "background-color", "background" -> parseColor(value)?.takeIf { it != 0xFFFFFFFF }?.let { add(SpanStyleKind.Background(it)) }
                    }
                }
            }
        }
    }

    private val NAMED = mapOf(
        "black" to 0xFF000000, "white" to 0xFFFFFFFF, "red" to 0xFFFF0000, "green" to 0xFF008000, "blue" to 0xFF0000FF,
        "navy" to 0xFF000080, "gray" to 0xFF808080, "grey" to 0xFF808080, "orange" to 0xFFFFA500, "purple" to 0xFF800080,
        "maroon" to 0xFF800000, "teal" to 0xFF008080, "yellow" to 0xFFFFFF00, "brown" to 0xFFA52A2A,
    )

    /** "#rgb", "#rrggbb", "rgb(r, g, b)", "rgba(…)" or a basic color name → 0xAARRGGBB. */
    fun parseColor(raw: String): Long? {
        val v = raw.trim().lowercase().removeSuffix("!important").trim()
        if (v.isEmpty()) return null
        NAMED[v]?.let { return it }
        if (v.startsWith("#")) {
            val hex = v.drop(1)
            val full = when (hex.length) {
                3 -> hex.map { "$it$it" }.joinToString("")
                6 -> hex
                else -> return null
            }
            return full.toLongOrNull(16)?.let { 0xFF000000 or it }
        }
        val m = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)""").find(v) ?: return null
        val (r, g, b) = m.destructured.toList().take(3).map { it.toInt().coerceIn(0, 255).toLong() }
        val a = m.groupValues[4].toDoubleOrNull()?.let { (it.coerceIn(0.0, 1.0) * 255).toLong() } ?: 255L
        if (a == 0L) return null
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
