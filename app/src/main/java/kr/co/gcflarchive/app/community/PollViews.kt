package kr.co.gcflarchive.app.community

import android.content.Context
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kr.co.gcflarchive.app.R
import java.time.Instant
import java.time.temporal.ChronoUnit

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun lp(top: Int = 0, ctx: Context) =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(top) }

/** Deadline choices: label resource to hours from now (0 = 마감 없음). */
private val DEADLINES = listOf(
    R.string.poll_deadline_none to 0, R.string.poll_deadline_1h to 1, R.string.poll_deadline_1d to 24,
    R.string.poll_deadline_3d to 72, R.string.poll_deadline_7d to 168,
)

/**
 * 투표 만들기 (글쓰기 화면): 질문, 선택지 2~10개, 여러 개 고르기, 마감.
 * [locked]: votes exist, so only a note is shown and nothing is sent.
 */
class PollEditor(private val ctx: Context, initial: CommunityPoll?, val locked: Boolean, onRemove: () -> Unit) {
    val view = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_option)
        val pad = ctx.dp(16)
        setPadding(pad, pad, pad, pad)
    }
    private val question = field(ctx.getString(R.string.poll_question), 200)
    private val optionsBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val options = mutableListOf<TextInputEditText>()
    private val addOption = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        setText(R.string.poll_add_option)
        setIconResource(R.drawable.ic_add)
        setOnClickListener { addOptionRow("") }
    }
    private val multiple = MaterialCheckBox(ctx).apply { setText(R.string.poll_multiple); minHeight = ctx.dp(48) }
    private var deadlineHours = 0

    init {
        val header = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(ctx).apply {
            setText(R.string.poll_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(ctx.getColor(R.color.krds_text_strong))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!locked) header.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            setText(R.string.poll_remove)
            setTextColor(ctx.getColor(R.color.krds_danger))
            setOnClickListener { onRemove() }
        })
        view.addView(header)
        if (locked) {
            view.addView(TextView(ctx).apply {
                setText(R.string.poll_locked)
                setTextAppearance(R.style.TextAppearance_Krds_Meta)
            }, lp(4, ctx))
        } else {
            question.second.setText(initial?.question.orEmpty())
            view.addView(question.first, lp(8, ctx))
            view.addView(optionsBox, lp(0, ctx))
            (initial?.options?.map { it.text } ?: listOf("", "")).forEach(::addOptionRow)
            view.addView(addOption, lp(8, ctx))
            multiple.isChecked = initial?.multiple ?: false
            view.addView(multiple, lp(4, ctx))
            view.addView(deadlinePicker(), lp(4, ctx))
        }
    }

    private fun field(hint: String, max: Int): Pair<TextInputLayout, TextInputEditText> {
        val layout = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply { this.hint = hint }
        val edit = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(max))
            maxLines = 2
        }
        layout.addView(edit)
        return layout to edit
    }

    private fun addOptionRow(text: String) {
        if (options.size >= 10) return
        val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val (layout, edit) = field(ctx.getString(R.string.poll_option, options.size + 1), 80)
        edit.setText(text)
        row.addView(layout, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val remove = ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(android.R.color.transparent)
            setColorFilter(ctx.getColor(R.color.krds_text_secondary))
            contentDescription = ctx.getString(R.string.poll_option_remove)
            minimumWidth = ctx.dp(48)
            minimumHeight = ctx.dp(48)
            setOnClickListener {
                if (options.size <= 2) return@setOnClickListener
                options.remove(edit)
                optionsBox.removeView(row)
                renumber()
            }
        }
        row.addView(remove)
        options += edit
        optionsBox.addView(row, lp(8, ctx))
        renumber()
    }

    private fun renumber() {
        options.forEachIndexed { i, e -> (e.parent?.parent as? TextInputLayout)?.hint = ctx.getString(R.string.poll_option, i + 1) }
        addOption.isVisible = options.size < 10
    }

    private fun deadlinePicker(): View {
        val layout = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle)
            .apply { hint = ctx.getString(R.string.poll_deadline) }
        val labels = DEADLINES.map { ctx.getString(it.first) }
        val picker = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(ctx, R.layout.item_dropdown, labels))
            setText(labels[0], false)
            setOnItemClickListener { _, _, position, _ -> deadlineHours = DEADLINES[position].second }
        }
        layout.addView(picker)
        return layout
    }

    /** null when locked (keep the poll as it is). */
    fun draft(): PollDraft? {
        if (locked) return null
        val closes = if (deadlineHours <= 0) "" else Instant.now().plus(deadlineHours.toLong(), ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString()
        return PollDraft(question.second.text?.toString().orEmpty(), options.map { it.text?.toString().orEmpty() }, multiple.isChecked, closes)
    }
}

/** 투표 카드 (글 보기): 고르기 → 투표하기, 결과는 막대로. 다시 고르거나 취소할 수 있어요. */
object PollCard {
    fun render(container: LinearLayout, poll: CommunityPoll, busy: Boolean, onVote: (Set<Int>) -> Unit, changing: Boolean = false) {
        val ctx = container.context
        container.removeAllViews()
        container.isVisible = true
        container.setBackgroundResource(R.drawable.bg_option)
        val pad = ctx.dp(16)
        container.setPadding(pad, pad, pad, pad)

        container.addView(TextView(ctx).apply {
            text = poll.question
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(ctx.getColor(R.color.krds_text_strong))
        })
        container.addView(TextView(ctx).apply {
            text = listOfNotNull(
                ctx.getString(if (poll.multiple) R.string.poll_kind_multiple else R.string.poll_kind_single),
                ctx.getString(R.string.poll_voters, poll.voterCount),
                when {
                    poll.closed -> ctx.getString(R.string.poll_closed)
                    poll.closesAt.isNotBlank() -> ctx.getString(R.string.poll_closes_at, CommunityParser.displayTime(poll.closesAt))
                    else -> null
                },
            ).joinToString(" · ")
            setTextAppearance(R.style.TextAppearance_Krds_Meta)
        }, lp(4, ctx))

        val voted = poll.myChoices.isNotEmpty()
        val choosing = poll.canVote && (!voted || changing)
        val picked = poll.myChoices.toMutableSet()
        val voteButton = MaterialButton(ctx).apply {
            setText(if (voted) R.string.poll_revote else R.string.poll_vote)
            isEnabled = !busy && picked.isNotEmpty()
            setOnClickListener { onVote(picked.toSet()) }
        }

        if (choosing) {
            val rows = mutableListOf<android.widget.CompoundButton>()
            poll.options.forEachIndexed { i, option ->
                val button: android.widget.CompoundButton =
                    if (poll.multiple) MaterialCheckBox(ctx) else MaterialRadioButton(ctx)
                button.text = option.text
                button.setTextColor(ctx.getColor(R.color.krds_text_strong))
                button.isChecked = i in picked
                button.minHeight = ctx.dp(48)
                button.setBackgroundResource(if (i in picked) R.drawable.bg_option_selected else R.drawable.bg_option)
                button.setPaddingRelative(ctx.dp(8), 0, ctx.dp(12), 0)
                button.setOnClickListener {
                    if (poll.multiple) {
                        if (button.isChecked) picked += i else picked -= i
                    } else {
                        picked.clear(); picked += i
                    }
                    rows.forEachIndexed { j, b ->
                        b.isChecked = j in picked
                        b.setBackgroundResource(if (j in picked) R.drawable.bg_option_selected else R.drawable.bg_option)
                    }
                    voteButton.isEnabled = !busy && picked.isNotEmpty()
                }
                rows += button
                container.addView(button, lp(8, ctx))
            }
            val actions = LinearLayout(ctx)
            if (changing) actions.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                setText(R.string.poll_cancel_change)
                setOnClickListener { render(container, poll, busy, onVote, changing = false) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ctx.dp(8) })
            actions.addView(voteButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(actions, lp(12, ctx))
            return
        }

        poll.options.forEachIndexed { i, option -> container.addView(resultRow(ctx, poll, i, option), lp(8, ctx)) }
        when {
            poll.canVote && voted -> {
                val actions = LinearLayout(ctx)
                actions.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    setText(R.string.poll_unvote)
                    isEnabled = !busy
                    setOnClickListener { onVote(emptySet()) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ctx.dp(8) })
                actions.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    setText(R.string.poll_change)
                    isEnabled = !busy
                    setOnClickListener { render(container, poll, busy, onVote, changing = true) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                container.addView(actions, lp(12, ctx))
            }
            !poll.showResults -> container.addView(TextView(ctx).apply {
                setText(R.string.poll_results_after_vote)
                setTextAppearance(R.style.TextAppearance_Krds_Meta)
            }, lp(8, ctx))
        }
    }

    /** Bar behind dark text: a light tint, so the label is always readable. */
    private fun resultRow(ctx: Context, poll: CommunityPoll, i: Int, option: PollOption): View {
        val mine = i in poll.myChoices
        val frame = FrameLayout(ctx).apply {
            setBackgroundResource(if (mine) R.drawable.bg_option_selected else R.drawable.bg_option)
            minimumHeight = ctx.dp(48)
        }
        if (poll.showResults) {
            val pct = poll.percent(i)
            val bar = LinearLayout(ctx)
            bar.addView(View(ctx).apply { setBackgroundResource(R.drawable.bg_option_bar) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct.toFloat().coerceAtLeast(0.001f)))
            bar.addView(View(ctx), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (100 - pct).toFloat().coerceAtLeast(0.001f)))
            frame.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val row = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(12))
        }
        row.addView(TextView(ctx).apply {
            text = if (mine) ctx.getString(R.string.poll_mine_prefix, option.text) else option.text
            setTextColor(ctx.getColor(R.color.krds_text_strong))
            textSize = 16f
            if (mine) setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (poll.showResults) row.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.poll_result, poll.percent(i), option.votes ?: 0)
            setTextColor(ctx.getColor(R.color.krds_text_strong))
            textSize = 14f
        })
        frame.addView(row)
        frame.contentDescription = if (poll.showResults) ctx.getString(R.string.poll_result_desc, option.text, poll.percent(i), option.votes ?: 0) else option.text
        return frame
    }
}
