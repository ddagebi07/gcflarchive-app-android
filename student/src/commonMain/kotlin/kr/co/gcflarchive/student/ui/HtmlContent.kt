package kr.co.gcflarchive.student.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.Color
import kr.co.gcflarchive.core.html.RichText
import kr.co.gcflarchive.core.html.SpanStyleKind
import kr.co.gcflarchive.core.html.StyledText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kr.co.gcflarchive.core.html.HtmlBlock
import kr.co.gcflarchive.core.html.HtmlBlocks
import kr.co.gcflarchive.student.LocalApp

/**
 * Renders rich HTML (school posts, 공지사항) with native views: paragraphs, images,
 * scrollable tables and video buttons. Site links open their screen in the app.
 */
@Composable
fun HtmlContent(html: String, baseUrl: String, dark: Boolean, modifier: Modifier = Modifier) {
    val blocks = remember(html, dark) { HtmlBlocks.parse(html, baseUrl, stripColors = dark) }
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    val linkColor = Krds.colors.primaryText
    val textStyle = KrdsText.body.copy(lineHeight = 26.sp)
    Column(modifier) {
        if (blocks.isEmpty()) Text("내용이 없어요.", style = KrdsText.meta)
        blocks.forEach { block ->
            when (block) {
                is HtmlBlock.Text -> {
                    val text = remember(block.html, linkColor) {
                        RichText.parse(block.html, baseUrl).toAnnotated(linkColor) { u -> app.openLink(u) { uri.openUri(it) } }
                    }
                    BasicText(text, style = textStyle, modifier = Modifier.padding(bottom = 12.dp))
                }
                is HtmlBlock.Image -> AsyncImage(
                    model = block.url,
                    contentDescription = block.alt.ifBlank { null },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { uri.openUri(block.url) },
                )
                is HtmlBlock.Table -> HtmlTable(block)
                is HtmlBlock.Embed -> SecondaryButton("영상·외부 콘텐츠 열기", { uri.openUri(block.url) }, Modifier.padding(bottom = 12.dp), icon = Icons.Filled.PlayCircle)
            }
        }
    }
}

/** Builds a Compose string from the shared [StyledText] (bold, links, colors, headings…). */
fun StyledText.toAnnotated(linkColor: Color, onLink: ((String) -> Unit)? = null): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { s ->
        when (val k = s.style) {
            SpanStyleKind.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s.start, s.end)
            SpanStyleKind.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s.start, s.end)
            SpanStyleKind.Underline -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s.start, s.end)
            SpanStyleKind.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s.start, s.end)
            SpanStyleKind.Monospace -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace), s.start, s.end)
            is SpanStyleKind.Scale -> addStyle(SpanStyle(fontSize = k.factor.em), s.start, s.end)
            is SpanStyleKind.Color -> addStyle(SpanStyle(color = Color(k.argb)), s.start, s.end)
            is SpanStyleKind.Background -> addStyle(SpanStyle(background = Color(k.argb)), s.start, s.end)
            is SpanStyleKind.Link -> addLink(
                LinkAnnotation.Url(
                    k.url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = onLink?.let { open -> { link -> (link as? LinkAnnotation.Url)?.url?.let(open) } },
                ),
                s.start, s.end,
            )
        }
    }
}

@Composable
private fun HtmlTable(table: HtmlBlock.Table) {
    val border = Krds.colors.borderStrong
    Column(
        Modifier.padding(bottom = 12.dp).horizontalScroll(rememberScrollState()).border(1.dp, border),
    ) {
        table.rows.forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                row.forEach { cell ->
                    Text(
                        remember(cell.html) { RichText.parse(cell.html).toAnnotated(Color.Unspecified) },
                        style = KrdsText.body.copy(fontSize = 14.sp, fontWeight = if (cell.header) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier
                            .widthIn(min = (64 * cell.colspan).dp, max = (260 * cell.colspan).dp)
                            .fillMaxHeight()
                            .background(if (cell.header) Krds.colors.surfaceRaised else Krds.colors.surface)
                            .border(0.5.dp, border)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
