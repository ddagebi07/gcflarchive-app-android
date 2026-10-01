package kr.co.gcflarchive.admin.ui.kit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.databinding.ItemHeaderBinding
import kr.co.gcflarchive.admin.databinding.ItemRowBinding

/** .tag-badge variants (shared KRDS drawables/colors). */
enum class Badge(val background: Int, val textColor: Int) {
    INFO(R.drawable.bg_krds_badge_info, R.color.krds_badge_info_fg),
    SUCCESS(R.drawable.bg_krds_badge_success, R.color.krds_badge_success_fg),
    PURPLE(R.drawable.bg_krds_badge_purple, R.color.krds_badge_purple_fg),
    NEUTRAL(R.drawable.bg_krds_badge_neutral, R.color.krds_badge_neutral_fg),
    DANGER(R.drawable.bg_krds_badge_danger, R.color.krds_danger),
}

fun setBadges(group: ViewGroup, badges: List<Pair<String, Badge>>) {
    group.removeAllViews()
    val inflater = LayoutInflater.from(group.context)
    for ((label, badge) in badges) {
        if (label.isBlank()) continue
        val tv = inflater.inflate(R.layout.view_badge, group, false) as TextView
        tv.text = label
        tv.setBackgroundResource(badge.background)
        tv.setTextColor(ContextCompat.getColor(group.context, badge.textColor))
        group.addView(tv)
    }
    group.isVisible = group.childCount > 0
}

sealed interface Trailing {
    data object None : Trailing
    data object Chevron : Trailing
    data class Switch(val checked: Boolean, val enabled: Boolean = true) : Trailing
    data class Text(val text: String) : Trailing
}

/** One list entry. [header] rows render as section titles. */
data class Row(
    val id: String,
    val title: CharSequence,
    val subtitle: CharSequence? = null,
    val meta: CharSequence? = null,
    val badges: List<Pair<String, Badge>> = emptyList(),
    val icon: Int? = null,
    val trailing: Trailing = Trailing.None,
    val header: Boolean = false,
    /** Can be picked in multi-select mode (long press). */
    val selectable: Boolean = false,
    /** Swipe-to-act allowed (e.g. 차단 해제). */
    val swipeable: Boolean = false,
    val payload: Any? = null,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank() || header) return true
        val q = query.lowercase()
        return listOf(title, subtitle, meta).any { it?.toString()?.lowercase()?.contains(q) == true } ||
            badges.any { it.first.lowercase().contains(q) }
    }

    companion object {
        fun header(title: String) = Row(id = "header:$title", title = title, header = true)
    }
}

class RowAdapter(private val listener: Listener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    interface Listener {
        fun onRowClick(row: Row) {}
        fun onRowSwitch(row: Row, checked: Boolean) {}
        fun onSelectionChanged(selected: Set<String>) {}
    }

    var items: List<Row> = emptyList()
        private set
    val selected = linkedSetOf<String>()
    val selecting: Boolean get() = selected.isNotEmpty()

    @Suppress("NotifyDataSetChanged") // lists are rebuilt from fresh server data
    fun submit(rows: List<Row>) {
        items = rows
        selected.retainAll(rows.map { it.id }.toSet())
        notifyDataSetChanged()
    }

    fun toggleSelection(row: Row) {
        if (!row.selectable) return
        if (!selected.remove(row.id)) selected += row.id
        notifyItemChanged(items.indexOf(row))
        listener.onSelectionChanged(selected)
    }

    @Suppress("NotifyDataSetChanged")
    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
        listener.onSelectionChanged(selected)
    }

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = if (items[position].header) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) HeaderHolder(ItemHeaderBinding.inflate(inflater, parent, false))
        else RowHolder(ItemRowBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = items[position]
        when (holder) {
            is HeaderHolder -> holder.b.header.text = row.title
            is RowHolder -> holder.bind(row)
        }
    }

    private class HeaderHolder(val b: ItemHeaderBinding) : RecyclerView.ViewHolder(b.root)

    private inner class RowHolder(val b: ItemRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row) {
            b.title.text = row.title
            b.subtitle.isVisible = !row.subtitle.isNullOrBlank()
            b.subtitle.text = row.subtitle
            b.meta.isVisible = !row.meta.isNullOrBlank()
            b.meta.text = row.meta
            setBadges(b.badges, row.badges)
            b.icon.isVisible = row.icon != null
            row.icon?.let { b.icon.setImageResource(it) }
            b.select.isVisible = selecting && row.selectable
            b.select.isChecked = row.id in selected
            b.trailingSwitch.setOnCheckedChangeListener(null)
            b.trailingSwitch.isVisible = row.trailing is Trailing.Switch && !selecting
            b.chevron.isVisible = row.trailing is Trailing.Chevron && !selecting
            b.trailingText.isVisible = row.trailing is Trailing.Text
            when (val t = row.trailing) {
                is Trailing.Switch -> {
                    b.trailingSwitch.isChecked = t.checked
                    b.trailingSwitch.isEnabled = t.enabled
                    b.trailingSwitch.setOnCheckedChangeListener { _, c -> listener.onRowSwitch(row, c) }
                }
                is Trailing.Text -> b.trailingText.text = t.text
                else -> Unit
            }
            b.card.setOnClickListener {
                if (selecting) toggleSelection(row) else listener.onRowClick(row)
            }
            b.card.setOnLongClickListener {
                if (row.selectable) {
                    toggleSelection(row)
                    true
                } else {
                    false
                }
            }
            b.card.isClickable = true
        }
    }
}

/** Convenience for building "label: value · label: value" meta lines. */
fun joinMeta(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

fun View.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
