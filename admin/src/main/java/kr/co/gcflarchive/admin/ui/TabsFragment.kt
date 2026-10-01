package kr.co.gcflarchive.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.tabs.TabLayout
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.databinding.FragmentTabsBinding

/** ② 보안·차단: IP 차단 / 학번 차단 / 차단 시도 로그 as top tabs (permitted ones only). */
class TabsFragment : Fragment() {
    private var _binding: FragmentTabsBinding? = null
    private val binding get() = _binding!!
    private lateinit var routes: List<Route>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        routes = Route.forTab(Tab.SECURITY, AdminSession.get(requireContext()).grants)
        routes.forEach { binding.tabs.addTab(binding.tabs.newTab().setText(it.title)) }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        val initial = savedInstanceState?.getInt(STATE_TAB) ?: routes.indexOf(Route.of(arguments?.getString(SectionActivity.EXTRA_ROUTE))).coerceAtLeast(0)
        binding.tabs.getTabAt(initial)?.select()
        if (childFragmentManager.findFragmentById(binding.tabContainer.id) == null) show(initial)
    }

    fun select(route: Route) {
        val i = routes.indexOf(route)
        if (i >= 0) binding.tabs.getTabAt(i)?.select()
    }

    private fun show(index: Int) {
        val route = routes.getOrNull(index) ?: return
        childFragmentManager.commit { replace(binding.tabContainer.id, route.create!!.invoke()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.let { outState.putInt(STATE_TAB, it.tabs.selectedTabPosition) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val STATE_TAB = "tab"
    }
}
