package kr.co.gcflarchive.app.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Splits rich HTML (school posts, 공지사항) into blocks a native screen can show:
 * runs of inline/paragraph HTML for a TextView, plus images, tables and embeds
 * (video iframes) that need their own views. Replaces the WebView that used to
 * render these bodies.
 */
sealed interface HtmlBlock {
    /** HTML that android.text.Html can render (paragraphs, bold, links, lists…). */
    data class Text(val html: String) : HtmlBlock
    data class Image(val url: String, val alt: String) : HtmlBlock
    data class Table(val rows: List<List<Cell>>) : HtmlBlock
    /** iframe / video / embed: shown as a button that opens the source. */
    data class Embed(val url: String) : HtmlBlock

    data class Cell(val html: String, val header: Boolean, val colspan: Int)
}

object HtmlBlocks {
    private val SPECIAL = setOf("img", "table", "iframe", "video", "embed")
    private val BLOCK_TAGS = setOf(
        "p", "div", "section", "article", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "figure", "center", "pre",
    )

    /**
     * @param stripColors drop inline text/background colors (dark theme: author colors
     *   are picked for a white page and become unreadable).
     */
    fun parse(html: String, baseUrl: String, stripColors: Boolean = false): List<HtmlBlock> {
        val doc = Jsoup.parseBodyFragment(html, baseUrl)
        doc.select("script, style, noscript, link, meta").remove()
        if (stripColors) {
            doc.select("[style]").forEach { el ->
                val kept = el.attr("style").split(';')
                    .filter { decl -> decl.substringBefore(':').trim().lowercase() !in setOf("color", "background", "background-color") }
                    .joinToString(";")
                if (kept.isBlank()) el.removeAttr("style") else el.attr("style", kept)
            }
            doc.select("[color]").removeAttr("color")
            doc.select("[bgcolor]").removeAttr("bgcolor")
        }
        val out = mutableListOf<HtmlBlock>()
        val buf = StringBuilder()
        walk(doc.body(), out, buf)
        flush(out, buf)
        return out
    }

    private fun walk(parent: Element, out: MutableList<HtmlBlock>, buf: StringBuilder) {
        for (node: Node in parent.childNodes()) {
            when (node) {
                is TextNode -> buf.append(node.outerHtml())
                is Element -> when (val tag = node.normalName()) {
                    "img" -> {
                        val src = node.absUrl("src").ifBlank { node.attr("src") }
                        if (src.isNotBlank() && !src.startsWith("data:")) {
                            flush(out, buf)
                            out += HtmlBlock.Image(src, node.attr("alt"))
                        }
                    }
                    "table" -> {
                        flush(out, buf)
                        table(node)?.let { out += it }
                    }
                    "iframe", "video", "embed" -> {
                        val src = node.absUrl("src").ifBlank { node.selectFirst("source[src]")?.absUrl("src").orEmpty() }
                        if (src.isNotBlank()) {
                            flush(out, buf)
                            out += HtmlBlock.Embed(src)
                        }
                    }
                    else -> {
                        val containsSpecial = node.select(SPECIAL.joinToString(",")).isNotEmpty()
                        if (!containsSpecial) {
                            buf.append(node.outerHtml())
                        } else {
                            // Descend so the image/table can become its own view; block
                            // wrappers still break the surrounding text into paragraphs.
                            val block = tag in BLOCK_TAGS
                            if (block) flush(out, buf)
                            walk(node, out, buf)
                            if (block) flush(out, buf)
                        }
                    }
                }
            }
        }
    }

    private fun table(el: Element): HtmlBlock.Table? {
        val rows = el.select("tr").filter { tr -> tr.closest("table") == el }.map { tr ->
            tr.children().filter { it.normalName() == "td" || it.normalName() == "th" }.map { cell ->
                // Images inside cells fall back to their alt text.
                cell.select("img").forEach { img -> img.replaceWith(TextNode(img.attr("alt"))) }
                HtmlBlock.Cell(
                    html = cell.html(),
                    header = cell.normalName() == "th",
                    colspan = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 20) ?: 1,
                )
            }
        }.filter { it.isNotEmpty() }
        return if (rows.isEmpty()) null else HtmlBlock.Table(rows)
    }

    private fun flush(out: MutableList<HtmlBlock>, buf: StringBuilder) {
        val html = buf.toString()
        buf.setLength(0)
        if (hasVisibleText(html)) out += HtmlBlock.Text(html.trim())
    }

    /** True when the fragment has something besides whitespace, &nbsp; and empty tags. */
    fun hasVisibleText(html: String): Boolean =
        html.isNotBlank() && Jsoup.parseBodyFragment(html).text().replace(' ', ' ').isNotBlank()
}
