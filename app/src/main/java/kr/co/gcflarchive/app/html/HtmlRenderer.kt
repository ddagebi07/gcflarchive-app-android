package kr.co.gcflarchive.app.html

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.button.MaterialButton
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.library.ImageLoader
import kr.co.gcflarchive.app.util.Links

/** Renders [HtmlBlocks] output into native views (text, images, scrollable tables, embeds). */
object HtmlRenderer {
    fun render(container: LinearLayout, html: String, baseUrl: String, scope: LifecycleCoroutineScope) {
        val ctx = container.context
        container.removeAllViews()
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val blocks = HtmlBlocks.parse(html, baseUrl, stripColors = night)
        if (blocks.isEmpty()) {
            container.addView(textView(ctx).apply { setText(R.string.html_empty) })
            return
        }
        for (block in blocks) {
            val view = when (block) {
                is HtmlBlock.Text -> textView(ctx).apply { text = spanned(ctx, block.html) }
                is HtmlBlock.Image -> image(ctx, block, scope)
                is HtmlBlock.Table -> table(ctx, block)
                is HtmlBlock.Embed -> embed(ctx, block)
            }
            container.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(ctx, 12)
            })
        }
    }

    private fun textView(ctx: Context) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setLineSpacing(0f, 1.55f)
        setTextColor(ctx.getColor(R.color.krds_text_primary))
        setLinkTextColor(ctx.getColor(R.color.krds_primary_text))
        movementMethod = LinkMovementMethod.getInstance()
        breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
    }

    /** Html → Spanned with links routed through [Links] (own pages open natively). */
    private fun spanned(ctx: Context, html: String): CharSequence {
        val sp = SpannableStringBuilder(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY))
        for (span in sp.getSpans(0, sp.length, URLSpan::class.java)) {
            val start = sp.getSpanStart(span)
            val end = sp.getSpanEnd(span)
            val url = span.url
            sp.removeSpan(span)
            sp.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = Links.open(widget.context, Uri.parse(url))
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Html adds paragraph spacing after the last block too.
        var end = sp.length
        while (end > 0 && sp[end - 1].isWhitespace()) end--
        return sp.subSequence(0, end)
    }

    private fun image(ctx: Context, block: HtmlBlock.Image, scope: LifecycleCoroutineScope): View =
        ImageView(ctx).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = block.alt.ifBlank { null }
            minimumHeight = dp(ctx, 48)
            setOnClickListener { Links.openExternal(ctx, Uri.parse(block.url)) }
            ImageLoader.into(this, block.url, scope, targetWidth = ctx.resources.displayMetrics.widthPixels)
        }

    private fun table(ctx: Context, block: HtmlBlock.Table): View {
        val table = TableLayout(ctx).apply {
            setBackgroundResource(R.drawable.bg_html_table)
            setPadding(1, 1, 1, 1)
        }
        for (cells in block.rows) {
            val row = TableRow(ctx)
            for (cell in cells) {
                val tv = TextView(ctx).apply {
                    text = spanned(ctx, cell.html)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(ctx.getColor(R.color.krds_text_primary))
                    setLinkTextColor(ctx.getColor(R.color.krds_primary_text))
                    movementMethod = LinkMovementMethod.getInstance()
                    maxWidth = dp(ctx, 260)
                    setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
                    setBackgroundResource(if (cell.header) R.drawable.bg_html_cell_header else R.drawable.bg_html_cell)
                    if (cell.header) setTypeface(typeface, Typeface.BOLD)
                }
                row.addView(tv, TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT).apply {
                    span = cell.colspan
                })
            }
            table.addView(row)
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = true
            addView(table)
        }
    }

    private fun embed(ctx: Context, block: HtmlBlock.Embed): View =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.html_open_embed)
            setIconResource(R.drawable.ic_play)
            setOnClickListener { Links.openExternal(ctx, Uri.parse(block.url)) }
        }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
