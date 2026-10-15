package kr.co.gcflarchive.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.ArchiveLink
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.community.CommunityActivity
import kr.co.gcflarchive.app.community.CommunityParser
import kr.co.gcflarchive.app.community.CommunityPostActivity
import kr.co.gcflarchive.app.community.CommunityRepository
import kr.co.gcflarchive.app.data.LoginState
import kr.co.gcflarchive.app.data.SiteApi
import kr.co.gcflarchive.app.data.SiteSession
import kr.co.gcflarchive.app.databinding.FragmentHomeBinding
import kr.co.gcflarchive.app.databinding.ItemArchiveTileBinding
import kr.co.gcflarchive.app.meal.MealRepository
import kr.co.gcflarchive.app.share.ShareReceiverActivity
import kr.co.gcflarchive.app.util.Links
import org.json.JSONObject

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) startActivity(ShareReceiverActivity.intent(requireContext(), uris))
    }

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (_binding != null) loadWelcome()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildArchiveGrid()

        binding.uploadCard.setOnClickListener { pickFiles.launch(arrayOf("*/*")) }
        binding.driveOpen.setOnClickListener { (activity as? MainActivity)?.selectTab(MainActivity.TAB_DRIVE) }
        binding.mealCard.setOnClickListener { (activity as? MainActivity)?.selectTab(MainActivity.TAB_MEAL) }
        binding.timetableCard.onOpen = {
            startActivity(kr.co.gcflarchive.student.school.SchoolActivity.intent(requireContext(), tab = "TIMETABLE"))
        }
        binding.bell.setOnClickListener {
            startActivity(kr.co.gcflarchive.student.school.SchoolActivity.intent(requireContext(), tab = "ALERTS"))
        }
        binding.schoolCard.setOnClickListener { startActivity(kr.co.gcflarchive.student.school.SchoolActivity.intent(requireContext())) }
        binding.communityCard.setOnClickListener { startActivity(CommunityActivity.intent(requireContext())) }
        binding.loginBanner.setOnClickListener { login.launch(LoginActivity.intent(requireContext())) }
        binding.btnBannerLogin.setOnClickListener { login.launch(LoginActivity.intent(requireContext())) }
        binding.openSite.setOnClickListener { Links.openExternal(requireContext(), android.net.Uri.parse(Config.BASE_URL)) }

        loadTodayMeal()
    }

    /** Rows of [TILES_PER_ROW] equal-width tiles with even gaps (no overlap, equal heights). */
    private fun buildArchiveGrid() {
        val gap = (8 * resources.displayMetrics.density).toInt()
        binding.archiveGrid.removeAllViews()
        ArchiveLink.entries.chunked(TILES_PER_ROW).forEachIndexed { r, links ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            for (i in 0 until TILES_PER_ROW) {
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) marginStart = gap }
                val link = links.getOrNull(i)
                if (link == null) {
                    row.addView(View(requireContext()), lp) // keeps the last row's tiles the same width
                    continue
                }
                val tile = ItemArchiveTileBinding.inflate(layoutInflater, row, false)
                tile.icon.setImageResource(link.iconRes)
                tile.label.setText(link.titleRes)
                tile.root.contentDescription = getString(link.titleRes)
                tile.root.setOnClickListener {
                    if (link == ArchiveLink.SEARCH) {
                        (activity as? MainActivity)?.selectTab(MainActivity.TAB_SEARCH)
                    } else {
                        Links.open(requireContext(), android.net.Uri.parse(Config.url(link.path)))
                    }
                }
                row.addView(tile.root, lp)
            }
            binding.archiveGrid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (r > 0) topMargin = gap
            })
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check on every return: the user may have just logged in or out elsewhere.
        loadWelcome()
        loadCommunity()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        // The date may have rolled over while the app sat in the background.
        if (!hidden && _binding != null) {
            loadTodayMeal()
            loadWelcome()
            loadCommunity()
        }
    }

    private fun loadWelcome() {
        viewLifecycleOwner.lifecycleScope.launch {
            val state = SiteSession.state()
            val b = _binding ?: return@launch
            val userId = (state as? LoginState.LoggedIn)?.userId
            b.welcomeRow.isVisible = userId != null
            if (userId != null) {
                loadAlertCount()
                b.timetableCard.refresh()
            } else {
                b.bellCount.isVisible = false
            }
            // Only a definite "not logged in" shows the banner; offline (Unknown) keeps it hidden.
            b.loginBanner.isVisible = state == LoginState.LoggedOut
            if (userId != null) {
                // 학생: '30512님, 반가워요!' / 교사: '이혜경 선생님, 안녕하세요!'
                val teacher = (state as LoginState.LoggedIn).userType == "teacher"
                val name = state.displayName.ifBlank { userId }
                b.welcome.text = buildSpannedString {
                    if (teacher) {
                        bold { append(getString(R.string.home_welcome_teacher_name, name)) }
                        append(getString(R.string.home_welcome_teacher_suffix))
                    } else {
                        bold { append(getString(R.string.home_welcome_name, userId)) }
                        append(getString(R.string.home_welcome_suffix))
                    }
                }
            }
        }
    }

    /** 종 아이콘의 빨간 숫자: 학교생활 알림 중 안 읽은 것 (99개 넘으면 99+). */
    private fun loadAlertCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            val unread = runCatching { JSONObject(SiteApi.get("/api/notifications?limit=1")).optInt("unread") }.getOrNull()
            val b = _binding ?: return@launch
            if (unread == null) return@launch  // offline: keep what was shown
            b.bellCount.isVisible = unread > 0
            b.bellCount.text = if (unread > 99) "99+" else unread.toString()
            b.bell.contentDescription = if (unread > 0) getString(R.string.home_alerts_count, unread) else getString(R.string.home_alerts)
        }
    }

    /** 홈의 커뮤니티 카드: 최근 글 3개 (읽을 수 있는 게시판만). */
    private fun loadCommunity() {
        val b = _binding ?: return
        if (!b.communityCard.isVisible) return  // 카드를 숨겨 둔 동안은 불러오지 않아요.
        if (b.communityPosts.childCount == 0) {
            b.communityStatus.isVisible = true
            b.communityStatus.setText(R.string.home_community_loading)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { CommunityRepository.recent(3) }
            val v = _binding ?: return@launch
            v.communityPosts.removeAllViews()
            val recent = result.getOrNull()
            val status = when {
                recent == null -> R.string.home_community_failed
                !recent.loggedIn -> R.string.home_community_login
                recent.posts.isEmpty() -> R.string.home_community_empty
                else -> 0
            }
            v.communityStatus.isVisible = status != 0
            if (status != 0) v.communityStatus.setText(status)
            recent?.posts?.forEach { p ->
                val row = layoutInflater.inflate(R.layout.item_home_community_post, v.communityPosts, false)
                row.findViewById<android.widget.TextView>(R.id.title).text = p.title
                row.findViewById<android.widget.TextView>(R.id.meta).text = listOfNotNull(
                    p.boardName.ifBlank { null },
                    CommunityParser.displayTime(p.createdAt).ifBlank { null },
                    getString(R.string.community_comments, p.commentCount),
                    if (p.imageCount > 0) getString(R.string.community_photos_count, p.imageCount) else null,
                ).joinToString(" · ")
                row.setOnClickListener { startActivity(CommunityPostActivity.intent(requireContext(), p.id)) }
                v.communityPosts.addView(row)
            }
        }
    }

    private fun loadTodayMeal() {
        binding.mealSummary.setText(R.string.meal_loading)
        binding.mealKind.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            val meals = runCatching { MealRepository.fetch(MealRepository.today()) }
            val b = _binding ?: return@launch
            meals.onSuccess { list ->
                // Show the next relevant meal: lunch by default, dinner in the afternoon.
                val hour = java.time.LocalTime.now(MealRepository.SEOUL).hour
                val preferred = if (hour >= 14) "석식" else "중식"
                val meal = list.firstOrNull { it.kind == preferred } ?: list.firstOrNull()
                if (meal == null) {
                    b.mealSummary.setText(R.string.meal_none_today)
                } else {
                    b.mealKind.isVisible = true
                    b.mealKind.text = meal.kind
                    b.mealSummary.text = meal.dishes.joinToString(" · ") { it.name }
                }
            }.onFailure {
                b.mealSummary.setText(R.string.meal_load_failed)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val TILES_PER_ROW = 4
    }
}
