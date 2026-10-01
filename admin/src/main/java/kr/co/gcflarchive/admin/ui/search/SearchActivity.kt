package kr.co.gcflarchive.admin.ui.search

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminApi
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.IpBand
import kr.co.gcflarchive.admin.core.Permission
import kr.co.gcflarchive.admin.databinding.ActivitySearchBinding
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.BaseActivity
import kr.co.gcflarchive.admin.ui.Navigator
import kr.co.gcflarchive.admin.ui.Route
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.RowAdapter
import kr.co.gcflarchive.admin.ui.kit.Trailing

/** 전역 검색: 메뉴 · 자료 · 학번 · IP. Each hit opens the screen that manages it. */
class SearchActivity : BaseActivity(), RowAdapter.Listener {
    private lateinit var binding: ActivitySearchBinding
    private val adapter = RowAdapter(this)
    private var index: List<Row> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setScreen(binding.root, binding.toolbar, title = "")
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.query.doAfterTextChanged { render() }
        binding.query.requestFocus()
        buildIndex()
    }

    private fun buildIndex() {
        val g = AdminSession.get(this).grants
        val api = AdminApi.get(this)
        binding.progress.isVisible = true
        lifecycleScope.launch {
            val menus = Route.entries.filter { it.visible(g) }.map {
                Row("m:${it.key}", it.title, it.desc, icon = it.icon, badges = listOf("메뉴" to Badge.NEUTRAL), trailing = Trailing.Chevron, payload = it)
            }
            suspend fun source(enabled: Boolean, block: suspend () -> List<Row>): List<Row> = if (enabled) runCatching { block() }.getOrDefault(emptyList()) else emptyList()
            val parts = listOf(
                async {
                    source(g.has(Permission.BLOCKED_IPS)) {
                        api.get("/api/blocked-ips").json().optJSONArray("blocked").objects().map {
                            Row("ip:${it.str("band")}", it.str("band"), it.str("reason"),
                                badges = listOfNotNull("IP" to Badge.DANGER, if (IpBand.isAuto(it.str("reason"))) "AUTO" to Badge.PURPLE else null), payload = Route.IP_BLOCKS)
                        }
                    }
                },
                async {
                    source(g.has(Permission.BLOCKED_HAKBUNS)) {
                        api.get("/api/blocked-hakbuns").json().optJSONArray("blocked").objects().map {
                            Row("hb:${it.str("hakbun")}", it.str("hakbun"), it.str("reason"), badges = listOf("차단 학번" to Badge.DANGER), payload = Route.HAKBUN_BLOCKS)
                        }
                    }
                },
                async {
                    source(g.has(Permission.CREDENTIALS)) {
                        api.get("/api/admins").json().optJSONArray("admins").objects().map {
                            Row("ad:${it.str("hakbun")}", it.str("hakbun"), getString(R.string.search_admin_account), badges = listOf("관리자" to Badge.INFO), payload = Route.ADMINS)
                        } + api.get("/api/custom-accounts").json().optJSONArray("accounts").objects().map {
                            Row("ca:${it.str("hakbun")}", it.str("hakbun"), it.str("name"), badges = listOf("특수 계정" to Badge.INFO), payload = Route.CUSTOM_ACCOUNTS)
                        }
                    }
                },
                async {
                    source(g.has(Permission.PDFS)) {
                        api.get("/api/documents").json().optJSONArray("documents").objects().map {
                            Row("doc:${it.str("filename")}", it.str("displayName").ifBlank { it.str("filename") }, it.str("category"),
                                badges = listOf("자료" to Badge.SUCCESS), payload = Route.DOCUMENTS)
                        }
                    }
                },
            ).awaitAll().flatten()
            index = menus + parts
            binding.progress.isVisible = false
            render()
        }
    }

    private fun render() {
        val q = binding.query.text?.toString().orEmpty().trim()
        val hits = if (q.isEmpty()) index.filter { it.id.startsWith("m:") } else index.filter { it.matches(q) }.take(200)
        adapter.submit(hits)
        binding.empty.isVisible = q.isNotEmpty() && hits.isEmpty()
    }

    override fun onRowClick(row: Row) {
        val route = row.payload as? Route ?: return
        if (route.tab == kr.co.gcflarchive.admin.ui.Tab.SECURITY) {
            startActivity(kr.co.gcflarchive.admin.ui.MainActivity.intent(this, route.key))
        } else {
            Navigator.open(this, route)
        }
    }

}
