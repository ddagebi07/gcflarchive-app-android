package kr.co.gcflarchive.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.divider.MaterialDivider
import kr.co.gcflarchive.app.BuildConfig
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.community.CommunityActivity
import kr.co.gcflarchive.app.databinding.FragmentMoreBinding
import kr.co.gcflarchive.app.databinding.ItemMoreRowBinding
import kr.co.gcflarchive.app.grade.GradeActivity
import kr.co.gcflarchive.app.grade.MyScoresActivity
import kr.co.gcflarchive.app.library.DocumentsActivity
import kr.co.gcflarchive.app.library.PastExamsActivity
import kr.co.gcflarchive.app.library.PhotosActivity
import kr.co.gcflarchive.app.library.VideosActivity
import kr.co.gcflarchive.app.map.MapActivity
import kr.co.gcflarchive.app.notice.NoticeListActivity
import kr.co.gcflarchive.app.util.Links

/** 더보기: 앱의 모든 메뉴를 목록으로 보여 줘요. */
class MoreFragment : Fragment() {
    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    /** One row: what it opens is decided when tapped. */
    private class Entry(val iconRes: Int, val labelRes: Int, val value: String? = null, val open: () -> Unit)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        fun start(intent: Intent) = startActivity(intent)
        fun tab(name: String) = (activity as? MainActivity)?.selectTab(name)

        section(R.string.more_section_community, listOf(
            Entry(R.drawable.ic_forum, R.string.more_community) { start(CommunityActivity.intent(ctx)) },
            Entry(R.drawable.ic_notice, R.string.link_notice) { start(Intent(ctx, NoticeListActivity::class.java)) },
        ))
        section(R.string.more_section_study, listOf(
            Entry(R.drawable.ic_grade, R.string.link_grades) { start(GradeActivity.intent(ctx)) },
            Entry(R.drawable.ic_score, R.string.more_my_scores) { start(MyScoresActivity.intent(ctx)) },
            Entry(R.drawable.ic_exam, R.string.link_past_exams) { start(Intent(ctx, PastExamsActivity::class.java)) },
            Entry(R.drawable.ic_pdf, R.string.link_documents) { start(Intent(ctx, DocumentsActivity::class.java)) },
        ))
        section(R.string.more_section_school, listOf(
            Entry(R.drawable.ic_meal, R.string.tab_meal) { tab(MainActivity.TAB_MEAL) },
            Entry(R.drawable.ic_map, R.string.link_map) { start(MapActivity.intent(ctx, null, null)) },
            Entry(R.drawable.ic_photo, R.string.link_photos) { start(Intent(ctx, PhotosActivity::class.java)) },
            Entry(R.drawable.ic_video, R.string.link_videos) { start(Intent(ctx, VideosActivity::class.java)) },
            Entry(R.drawable.ic_search, R.string.link_search) { tab(MainActivity.TAB_SEARCH) },
            Entry(R.drawable.ic_drive, R.string.tab_drive) { tab(MainActivity.TAB_DRIVE) },
        ))
        section(R.string.more_section_app, listOf(
            Entry(R.drawable.ic_settings, R.string.settings_title) { start(SettingsActivity.intent(ctx)) },
            Entry(R.drawable.ic_open_in_browser, R.string.more_website) { Links.openExternal(ctx, Uri.parse(Config.BASE_URL)) },
            Entry(R.drawable.ic_info, R.string.more_version, value = BuildConfig.VERSION_NAME) {},
        ))
    }

    private fun section(titleRes: Int, entries: List<Entry>) {
        val list = binding.list
        if (list.childCount > 1) {
            list.addView(MaterialDivider(requireContext()).apply {
                dividerColor = requireContext().getColor(R.color.krds_border)
            }, ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            })
        }
        list.addView(TextView(requireContext()).apply {
            setText(titleRes)
            setTextAppearance(R.style.TextAppearance_Gcfl_Section)
            setPadding(dp(20), dp(16), dp(20), dp(4))
        })
        for (e in entries) {
            val row = ItemMoreRowBinding.inflate(layoutInflater, list, false)
            row.icon.setImageResource(e.iconRes)
            row.label.setText(e.labelRes)
            row.value.isVisible = e.value != null
            row.value.text = e.value
            // The version row only shows a value; nothing to open.
            val opens = e.value == null
            row.chevron.isVisible = opens
            row.root.isClickable = opens
            if (opens) row.root.setOnClickListener { e.open() } else row.root.background = null
            list.addView(row.root)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
