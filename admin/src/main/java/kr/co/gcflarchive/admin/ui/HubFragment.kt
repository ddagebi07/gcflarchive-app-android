package kr.co.gcflarchive.admin.ui

import android.os.Bundle
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing

/** Tabs ③④⑤: the permitted destinations grouped under section headers. */
class HubFragment : ListFragment() {
    private val tab: Tab get() = Tab.valueOf(requireArguments().getString(ARG_TAB) ?: Tab.MORE.name)

    override suspend fun load(): List<Row> {
        val routes = Route.forTab(tab, grants)
        return routes.groupBy { it.group }.flatMap { (group, items) ->
            listOf(Row.header(group)) + items.map {
                Row(id = it.key, title = it.title, subtitle = it.desc, icon = it.icon, trailing = Trailing.Chevron, payload = it)
            }
        }
    }

    override fun onRowClick(row: Row) {
        (row.payload as? Route)?.let { Navigator.open(requireContext(), it) }
    }

    companion object {
        private const val ARG_TAB = "tab"
        fun of(tab: Tab) = HubFragment().apply { arguments = Bundle().apply { putString(ARG_TAB, tab.name) } }
    }
}
