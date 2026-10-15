package kr.co.gcflarchive.app.survey

import android.content.Context
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kr.co.gcflarchive.app.R

/**
 * Small KRDS view builders for the 설문조사 screens (content is built in code, like the poll views).
 * Text is always dark on light surfaces; bars use a light tint behind the label so it stays readable.
 */
internal class SurveyViews(private val ctx: Context) {
    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun lp(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    fun weighted(end: Int = 0) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(end) }

    /** White card with a 1dp border (8dp corners), like a Google Forms question card. */
    fun card(error: Boolean = false): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(if (error) R.drawable.bg_option_error else R.drawable.bg_option)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    /** The form's head card: a primary stripe on top, then title and description. */
    fun headCard(): Pair<LinearLayout, LinearLayout> {
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_option)
            clipToOutline = true
        }
        outer.addView(View(ctx).apply { setBackgroundColor(ctx.getColor(R.color.krds_primary)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)))
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        outer.addView(inner)
        return outer to inner
    }

    fun text(value: CharSequence, size: Float = 16f, bold: Boolean = false, color: Int = R.color.krds_text_primary) = TextView(ctx).apply {
        text = value
        textSize = size
        setTextColor(ctx.getColor(color))
        setLineSpacing(0f, 1.3f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun pageTitle(value: CharSequence) = text(value, 22f, bold = true, color = R.color.krds_text_strong)
    fun sectionTitle(value: CharSequence) = text(value, 18f, bold = true, color = R.color.krds_text_strong)
    fun cardTitle(value: CharSequence) = text(value, 17f, bold = true, color = R.color.krds_text_strong)
    fun meta(value: CharSequence) = TextView(ctx).apply { text = value; setTextAppearance(R.style.TextAppearance_Krds_Meta) }
    fun error(value: CharSequence) = text(value, 14f, color = R.color.krds_danger)

    fun info(value: CharSequence) = text(value, 15f, color = R.color.krds_text_strong).apply {
        setBackgroundResource(R.drawable.bg_krds_info_box)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    fun primary(label: CharSequence, onClick: () -> Unit) = MaterialButton(ctx).apply {
        text = label
        minHeight = dp(48)
        setOnClickListener { onClick() }
    }

    fun secondary(label: CharSequence, danger: Boolean = false, onClick: () -> Unit) =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            minHeight = dp(48)
            if (danger) setTextColor(ctx.getColor(R.color.krds_danger))
            setOnClickListener { onClick() }
        }

    fun row(vararg views: Pair<View, LinearLayout.LayoutParams>) = LinearLayout(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { (v, p) -> addView(v, p) }
    }

    fun field(hint: CharSequence, value: String, max: Int, multiline: Boolean = false, onChange: (String) -> Unit): TextInputLayout {
        val layout = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply { this.hint = hint }
        val edit = TextInputEditText(layout.context).apply {
            setText(value)
            inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(max))
            if (multiline) minLines = 3
            doAfterTextChanged { onChange(it?.toString().orEmpty()) }
        }
        layout.addView(edit)
        return layout
    }

    fun <T> dropdown(hint: CharSequence, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit): TextInputLayout {
        val layout = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply { this.hint = hint }
        val view = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(ctx, R.layout.item_dropdown, options.map { it.second }))
            setText(options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty(), false)
            setOnItemClickListener { _, _, position, _ -> onSelect(options[position].first) }
        }
        layout.addView(view)
        return layout
    }

    fun check(label: CharSequence, checked: Boolean, onChange: (Boolean) -> Unit) = MaterialCheckBox(ctx).apply {
        text = label
        isChecked = checked
        minHeight = dp(48)
        setTextColor(ctx.getColor(R.color.krds_text_strong))
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    fun switchRow(title: CharSequence, desc: CharSequence?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(MaterialSwitch(ctx).apply {
            text = title
            isChecked = checked
            minHeight = dp(48)
            setTextColor(ctx.getColor(R.color.krds_text_strong))
            setOnCheckedChangeListener { _, on -> onChange(on) }
        })
        if (desc != null) box.addView(meta(desc))
        return box
    }

    /** A count bar: light primary tint sized to [percent], dark text on top. */
    fun bar(label: String, count: Int, percent: Int): View {
        val frame = FrameLayout(ctx).apply { setBackgroundResource(R.drawable.bg_option); minimumHeight = dp(44) }
        val fill = LinearLayout(ctx)
        fill.addView(View(ctx).apply { setBackgroundResource(R.drawable.bg_option_bar) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, percent.toFloat().coerceAtLeast(0.001f)))
        fill.addView(View(ctx), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (100 - percent).toFloat().coerceAtLeast(0.001f)))
        frame.addView(fill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        row.addView(text(label, 15f, color = R.color.krds_text_strong), weighted())
        row.addView(text(ctx.getString(R.string.survey_bar, count, percent), 14f, color = R.color.krds_text_strong))
        frame.addView(row)
        frame.contentDescription = "$label, ${count}명, $percent%"
        return frame
    }
}
