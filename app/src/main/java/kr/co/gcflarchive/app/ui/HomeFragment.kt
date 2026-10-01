package kr.co.gcflarchive.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.ArchiveLink
import kr.co.gcflarchive.app.NativePages
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.data.LoginState
import kr.co.gcflarchive.app.data.SiteSession
import kr.co.gcflarchive.app.databinding.FragmentHomeBinding
import kr.co.gcflarchive.app.databinding.ItemArchiveTileBinding
import kr.co.gcflarchive.app.meal.MealRepository
import kr.co.gcflarchive.app.share.ShareReceiverActivity
import kr.co.gcflarchive.app.web.WebViewActivity

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
        for (link in ArchiveLink.entries) {
            val tile = ItemArchiveTileBinding.inflate(layoutInflater, binding.archiveGrid, false)
            tile.icon.setImageResource(link.iconRes)
            tile.label.setText(link.titleRes)
            tile.root.setOnClickListener {
                if (link == ArchiveLink.SEARCH) {
                    (activity as? MainActivity)?.selectTab(MainActivity.TAB_SEARCH)
                } else {
                    startActivity(
                        NativePages.intentFor(requireContext(), link.path)
                            ?: WebViewActivity.intent(requireContext(), link.path, getString(link.titleRes)),
                    )
                }
            }
            tile.root.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply { width = 0 }
            binding.archiveGrid.addView(tile.root)
        }

        binding.uploadCard.setOnClickListener { pickFiles.launch(arrayOf("*/*")) }
        binding.driveOpen.setOnClickListener { (activity as? MainActivity)?.selectTab(MainActivity.TAB_DRIVE) }
        binding.mealCard.setOnClickListener { (activity as? MainActivity)?.selectTab(MainActivity.TAB_MEAL) }
        binding.loginBanner.setOnClickListener { login.launch(LoginActivity.intent(requireContext())) }
        binding.btnBannerLogin.setOnClickListener { login.launch(LoginActivity.intent(requireContext())) }
        binding.openSite.setOnClickListener { startActivity(WebViewActivity.intent(requireContext(), "/")) }

        loadTodayMeal()
    }

    override fun onResume() {
        super.onResume()
        // Re-check on every return: the user may have just logged in or out in a web screen.
        loadWelcome()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        // The date may have rolled over while the app sat in the background.
        if (!hidden && _binding != null) {
            loadTodayMeal()
            loadWelcome()
        }
    }

    private fun loadWelcome() {
        viewLifecycleOwner.lifecycleScope.launch {
            val state = SiteSession.state()
            val b = _binding ?: return@launch
            val userId = (state as? LoginState.LoggedIn)?.userId
            b.welcome.isVisible = userId != null
            // Only a definite "not logged in" shows the banner; offline (Unknown) keeps it hidden.
            b.loginBanner.isVisible = state == LoginState.LoggedOut
            if (userId != null) {
                b.welcome.text = buildSpannedString {
                    bold { append(getString(R.string.home_welcome_name, userId)) }
                    append(getString(R.string.home_welcome_suffix))
                }
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
}
