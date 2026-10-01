package kr.co.gcflarchive.app.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.BuildConfig
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.databinding.ActivityMapBinding
import kr.co.gcflarchive.app.databinding.DialogInputBinding
import kr.co.gcflarchive.app.databinding.ItemPlaceBinding
import kr.co.gcflarchive.app.databinding.ItemReviewBinding
import kr.co.gcflarchive.app.grade.GradeLogic
import kr.co.gcflarchive.app.meal.MealRepository
import kr.co.gcflarchive.app.util.Links
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * 지도 아카이브 (map.html), native: OpenStreetMap tiles (osmdroid), place search through
 * the server's Kakao proxy, place details with 후기, 즐겨찾기, 영업시간 제안, 장소 직접 등록.
 */
class MapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapBinding
    private lateinit var sheet: BottomSheetBehavior<View>

    private var user = MapUser(false, "")
    private var stats: Map<String, PlaceStats> = emptyMap()
    private var favoriteIds: Set<String> = emptySet()
    private var shown: List<Place> = emptyList()
    private var selected: Place? = null
    private val markers = mutableListOf<Marker>()

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { loadUser() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid: identify the app to the tile servers and keep its cache private.
        Configuration.getInstance().apply {
            userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpMap()
        sheet = BottomSheetBehavior.from<View>(binding.sheet)
        sheet.state = BottomSheetBehavior.STATE_HIDDEN

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnSearch.setOnClickListener { search() }
        binding.searchInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        binding.chipFavorites.setOnClickListener { showFavorites() }
        binding.fabSchool.setOnClickListener { binding.map.controller.animateTo(SCHOOL, 17.0, 600L) }
        binding.btnRegisterPlace.setOnClickListener { if (requireLogin(getString(R.string.map_register_place))) registerPlace() }
        setUpDetailActions()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.detailGroup.isVisible && shown.isNotEmpty() -> showResults(shown, binding.resultsTitle.text.toString())
                    sheet.state != BottomSheetBehavior.STATE_HIDDEN -> sheet.state = BottomSheetBehavior.STATE_HIDDEN
                    else -> finish()
                }
            }
        })

        lifecycleScope.launch { stats = runCatching { MapRepository.stats() }.getOrDefault(emptyMap()) }
        loadUser()
        openSharedPlace(intent)
    }

    private fun setUpMap() {
        val map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.minZoomLevel = 12.0
        map.controller.setZoom(16.5)
        map.controller.setCenter(SCHOOL)
        map.overlays += CopyrightOverlay(this)
        map.setOnTouchListener { _, _ ->
            hideKeyboard()
            false
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        binding.map.onPause()
        super.onPause()
    }

    // ── account ───────────────────────────────────────────────────────────

    private fun loadUser() {
        lifecycleScope.launch {
            user = runCatching { MapRepository.user() }.getOrDefault(MapUser(false, ""))
            binding.chipFavorites.isVisible = user.verified
            if (user.verified) {
                favoriteIds = runCatching { MapRepository.favorites() }.getOrDefault(emptyList()).map { it.id }.toSet()
                if (user.nickname.isBlank()) askNickname()
            }
            selected?.let(::bindDetailHeader)
            bindReviewForm()
        }
    }

    private fun requireLogin(action: String): Boolean {
        if (user.verified) return true
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.map_login_needed, action))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.home_login_banner_button) { _, _ -> login.launch(LoginActivity.intent(this)) }
            .show()
        return false
    }

    private fun askNickname() {
        val field = DialogInputBinding.inflate(layoutInflater)
        field.inputLayout.hint = getString(R.string.map_nickname_hint)
        field.input.inputType = InputType.TYPE_CLASS_TEXT
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.map_nickname_title)
            .setMessage(R.string.map_nickname_desc)
            .setView(field.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.map_nickname_submit, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = field.input.text?.toString().orEmpty().trim()
            MapLogic.nicknameError(name)?.let {
                field.inputLayout.error = it
                return@setOnClickListener
            }
            lifecycleScope.launch {
                runCatching { MapRepository.registerNickname(name) }
                    .onSuccess {
                        user = user.copy(nickname = name)
                        dialog.dismiss()
                        snack(getString(R.string.map_nickname_set, name))
                    }
                    .onFailure { e -> if (e is CancellationException) throw e else field.inputLayout.error = e.message }
            }
        }
    }

    // ── search / results ──────────────────────────────────────────────────

    private fun search() {
        val q = binding.searchInput.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return
        hideKeyboard()
        binding.progress.isVisible = true
        lifecycleScope.launch {
            val result = runCatching { MapRepository.search(q) }
            binding.progress.isVisible = false
            result.onSuccess { places ->
                showResults(places, if (places.isEmpty()) getString(R.string.map_no_results) else getString(R.string.map_results, places.size))
                binding.registerGroup.isVisible = true
            }.onFailure { e ->
                if (e is CancellationException) throw e
                snack(getString(R.string.map_search_failed))
            }
        }
    }

    private fun showFavorites() {
        binding.progress.isVisible = true
        lifecycleScope.launch {
            val favs = runCatching { MapRepository.favorites() }.getOrDefault(emptyList())
            binding.progress.isVisible = false
            favoriteIds = favs.map { it.id }.toSet()
            showResults(favs, if (favs.isEmpty()) getString(R.string.map_no_favorites) else getString(R.string.map_favorites_title, favs.size))
            binding.registerGroup.isVisible = false
        }
    }

    private fun showResults(places: List<Place>, title: String) {
        shown = places
        binding.detailGroup.isVisible = false
        binding.resultsGroup.isVisible = true
        binding.resultsTitle.text = title
        binding.resultsList.removeAllViews()
        for (p in places) {
            val row = ItemPlaceBinding.inflate(layoutInflater, binding.resultsList, false)
            row.name.text = p.name
            row.address.text = p.displayAddress
            val reviews = stats[p.id]?.reviewCount ?: 0
            row.badge.isVisible = p.isCustom || reviews > 0
            row.badge.text = if (reviews > 0) getString(R.string.map_reviews_badge, reviews) else getString(R.string.map_custom_badge)
            row.root.setOnClickListener { select(p, pan = true) }
            binding.resultsList.addView(row.root)
        }
        placeMarkers(places)
        binding.sheet.scrollTo(0, 0)
        sheet.state = BottomSheetBehavior.STATE_HALF_EXPANDED
    }

    private fun placeMarkers(places: List<Place>) {
        val map = binding.map
        map.overlays.removeAll(markers.toSet())
        markers.clear()
        for (p in places) {
            val m = Marker(map).apply {
                position = GeoPoint(p.y, p.x)
                title = p.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    select(p, pan = false)
                    true
                }
            }
            markers += m
            map.overlays += m
        }
        map.invalidate()
        when (places.size) {
            0 -> Unit
            1 -> map.controller.animateTo(GeoPoint(places[0].y, places[0].x), 17.5, 500L)
            else -> map.post {
                val box = BoundingBox.fromGeoPointsSafe(places.map { GeoPoint(it.y, it.x) })
                map.zoomToBoundingBox(box.increaseByScale(1.3f), true, dp(48))
            }
        }
    }

    // ── place detail ──────────────────────────────────────────────────────

    private fun setUpDetailActions() {
        binding.btnDirections.setOnClickListener {
            val p = selected ?: return@setOnClickListener
            // Kakao Map app if installed, otherwise its web page.
            val app = Uri.parse("kakaomap://look?p=${p.y},${p.x}")
            val intent = Intent(Intent.ACTION_VIEW, app)
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else Links.openExternal(this, Uri.parse("https://map.kakao.com/link/map/${Uri.encode(p.name)},${p.y},${p.x}"))
        }
        binding.btnShare.setOnClickListener {
            val p = selected ?: return@setOnClickListener
            Links.share(this, "${p.name}\n${MapLogic.shareUrl(Config.BASE_URL, p)}")
        }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }
        binding.btnSchedule.setOnClickListener {
            if (requireLogin(getString(R.string.map_schedule_suggest))) selected?.let(::suggestSchedule)
        }
        binding.rating.setOnRatingBarChangeListener { _, value, _ ->
            binding.ratingText.text = MapLogic.RATING_LABELS[value.toInt().coerceIn(0, 5)]
        }
        binding.btnPostReview.setOnClickListener { postReview() }
        binding.reviewLogin.setOnClickListener { login.launch(LoginActivity.intent(this)) }
    }

    private fun select(place: Place, pan: Boolean) {
        selected = place
        binding.resultsGroup.isVisible = false
        binding.detailGroup.isVisible = true
        bindDetailHeader(place)
        bindReviewForm()
        resetReviewForm()
        if (pan) binding.map.controller.animateTo(GeoPoint(place.y, place.x))
        markers.firstOrNull { it.title == place.name }?.showInfoWindow()
        binding.sheet.scrollTo(0, 0)
        sheet.state = BottomSheetBehavior.STATE_HALF_EXPANDED

        binding.placeViews.text = ""
        lifecycleScope.launch {
            runCatching { MapRepository.view(place.id) }.onSuccess { views ->
                if (selected?.id == place.id) binding.placeViews.text = getString(R.string.map_views, views)
            }
        }
        loadReviews(place)
    }

    private fun bindDetailHeader(place: Place) {
        binding.placeCategory.text = place.category
        binding.placeTitle.text = place.name
        binding.placeAddress.text = place.displayAddress
        binding.btnFavorite.isVisible = user.verified
        val starred = place.id in favoriteIds
        binding.btnFavorite.setText(if (starred) R.string.map_favorite_remove else R.string.map_favorite_add)
        binding.btnFavorite.setIconResource(if (starred) R.drawable.ic_star else R.drawable.ic_star_outline)
        val schedule = stats[place.id]?.schedule
        binding.placeSchedule.isVisible = !schedule.isNullOrEmpty()
        if (!schedule.isNullOrEmpty()) {
            binding.placeSchedule.text = buildString {
                append(getString(R.string.map_schedule_title))
                for ((key, label) in MapLogic.DAYS) schedule[key]?.let { append("\n$label  ${MapLogic.describe(it)}") }
            }
        }
    }

    private fun loadReviews(place: Place) {
        binding.reviewList.removeAllViews()
        binding.reviewList.addView(metaText(getString(R.string.map_reviews_loading)))
        lifecycleScope.launch {
            val result = runCatching { MapRepository.reviews(place.id) }
            if (selected?.id != place.id) return@launch
            binding.reviewList.removeAllViews()
            result.onSuccess { reviews ->
                if (reviews.isEmpty()) binding.reviewList.addView(metaText(getString(R.string.map_reviews_empty)))
                for (r in reviews) {
                    val v = ItemReviewBinding.inflate(layoutInflater, binding.reviewList, false)
                    v.author.text = r.author
                    v.stars.text = if (r.rating > 0) "★".repeat(r.rating) + "☆".repeat(5 - r.rating) else ""
                    v.content.text = buildString {
                        append(r.content)
                        if (r.hashtags.isNotEmpty()) append((if (r.content.isBlank()) "" else "\n") + r.hashtags.joinToString(" ") { "#$it" })
                    }
                    v.date.text = GradeLogic.parseInstant(r.timestamp)?.atZone(MealRepository.SEOUL)?.format(DATE) ?: ""
                    binding.reviewList.addView(v.root)
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                binding.reviewList.addView(metaText(getString(R.string.map_reviews_failed)))
            }
        }
    }

    private fun bindReviewForm() {
        val enabled = user.verified
        listOf(binding.rating, binding.reviewContent, binding.reviewTags, binding.reviewAnonymous, binding.btnPostReview).forEach { it.isEnabled = enabled }
        binding.reviewLogin.isVisible = !enabled
    }

    private fun resetReviewForm() {
        binding.rating.rating = 0f
        binding.ratingText.text = MapLogic.RATING_LABELS[0]
        binding.reviewContent.text = null
        binding.reviewTags.text = null
        binding.reviewAnonymous.isChecked = false
    }

    private fun postReview() {
        val place = selected ?: return
        if (!requireLogin(getString(R.string.map_post_review))) return
        if (user.nickname.isBlank()) return askNickname()
        val content = binding.reviewContent.text?.toString()?.trim().orEmpty()
        val tags = binding.reviewTags.text?.toString()?.trim().orEmpty()
        if (content.isEmpty() && tags.isEmpty()) return snack(getString(R.string.map_review_empty))
        binding.btnPostReview.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                MapRepository.postReview(place, binding.rating.rating.toInt(), content, tags, binding.reviewAnonymous.isChecked)
            }
            binding.btnPostReview.isEnabled = true
            result.onSuccess {
                snack(getString(R.string.map_review_posted))
                resetReviewForm()
                stats = runCatching { MapRepository.stats() }.getOrDefault(stats)
                loadReviews(place)
            }.onFailure { e -> if (e is CancellationException) throw e else snack(e.message ?: getString(R.string.map_reviews_failed)) }
        }
    }

    private fun toggleFavorite() {
        val place = selected ?: return
        lifecycleScope.launch {
            runCatching { MapRepository.toggleFavorite(place) }
                .onSuccess { starred ->
                    favoriteIds = if (starred) favoriteIds + place.id else favoriteIds - place.id
                    bindDetailHeader(place)
                    snack(getString(if (starred) R.string.map_favorite_added else R.string.map_favorite_removed))
                }
                .onFailure { e -> if (e is CancellationException) throw e else snack(e.message ?: "") }
        }
    }

    // ── dialogs: 영업시간 제안 / 장소 직접 등록 ──────────────────────────────

    private fun suggestSchedule(place: Place) {
        val current = stats[place.id]?.schedule.orEmpty()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
        }
        container.addView(metaText(getString(R.string.map_schedule_dialog_desc)))
        class DayFields(val open: TextInputEditText, val close: TextInputEditText, val bStart: TextInputEditText, val bEnd: TextInputEditText, val holiday: MaterialCheckBox)
        val fields = LinkedHashMap<String, DayFields>()
        for ((key, label) in MapLogic.DAYS) {
            val d = current[key] ?: DaySchedule("09:00", "21:00", "", "", false)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_Krds_CardTitle)
                minWidth = dp(24)
            })
            fun time(value: String, hint: String) = TextInputEditText(this).apply {
                setText(value)
                this.hint = hint
                inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
                textSize = 14f
                maxLines = 1
            }
            val open = time(d.open, "09:00")
            val close = time(d.close, "21:00")
            val bStart = time(d.breakStart, "")
            val bEnd = time(d.breakEnd, "")
            listOf(open, close, bStart, bEnd).forEach { row.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
            val holiday = MaterialCheckBox(this).apply {
                text = getString(R.string.map_schedule_holiday)
                isChecked = d.holiday
                setOnCheckedChangeListener { _, c -> listOf(open, close, bStart, bEnd).forEach { f -> f.isEnabled = !c } }
            }
            listOf(open, close, bStart, bEnd).forEach { it.isEnabled = !d.holiday }
            row.addView(holiday)
            container.addView(row)
            fields[key] = DayFields(open, close, bStart, bEnd, holiday)
        }
        container.addView(metaText("${getString(R.string.map_schedule_open)} · ${getString(R.string.map_schedule_close)} · ${getString(R.string.map_schedule_break)}"))
        val scroll = androidx.core.widget.NestedScrollView(this).apply { addView(container) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.map_schedule_suggest) + " · " + place.name)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.map_submit, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val schedule = fields.mapValues { (_, f) ->
                DaySchedule(f.open.text.toString().trim(), f.close.text.toString().trim(), f.bStart.text.toString().trim(), f.bEnd.text.toString().trim(), f.holiday.isChecked)
            }
            val bad = schedule.values.any { d -> !d.holiday && listOf(d.open, d.close, d.breakStart, d.breakEnd).any { !MapLogic.isValidTime(it) } }
            if (bad) return@setOnClickListener snack(getString(R.string.map_schedule_invalid))
            lifecycleScope.launch {
                runCatching { MapRepository.suggestSchedule(place, schedule) }
                    .onSuccess {
                        dialog.dismiss()
                        snack(getString(R.string.map_schedule_done))
                    }
                    .onFailure { e -> if (e is CancellationException) throw e else snack(e.message ?: "") }
            }
        }
    }

    private fun registerPlace() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
        }
        fun field(hintRes: Int): TextInputEditText {
            val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply { hint = getString(hintRes) }
            val edit = TextInputEditText(layout.context).apply { maxLines = 1; inputType = InputType.TYPE_CLASS_TEXT }
            layout.addView(edit)
            container.addView(layout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            return edit
        }
        val name = field(R.string.map_register_name)
        val road = field(R.string.map_register_road)
        val floor = field(R.string.map_register_floor)
        val other = field(R.string.map_register_other)
        name.setText(binding.searchInput.text)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.map_register_title)
            .setMessage(R.string.map_register_desc)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.map_register_submit, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim()
            val r = road.text.toString().trim()
            if (n.isEmpty() || r.isEmpty()) return@setOnClickListener snack(getString(R.string.map_register_required))
            lifecycleScope.launch {
                runCatching {
                    val (x, y) = MapRepository.geocode(r)
                    MapRepository.registerPlace(n, r, floor.text.toString().trim(), other.text.toString().trim(), x, y)
                }.onSuccess {
                    dialog.dismiss()
                    snack(getString(R.string.map_register_done))
                }.onFailure { e -> if (e is CancellationException) throw e else snack(e.message ?: "") }
            }
        }
    }

    // ── deep link: /map?placeId=…&placeName=… ─────────────────────────────

    private fun openSharedPlace(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_PLACE_ID)?.takeIf { it.isNotBlank() } ?: return
        val name = intent.getStringExtra(EXTRA_PLACE_NAME).orEmpty()
        lifecycleScope.launch {
            val place = runCatching {
                if (id.startsWith("cp_")) MapRepository.customPlaces().firstOrNull { it.id == id }
                else if (name.isNotBlank()) MapRepository.search(name).firstOrNull { it.id == id }
                else null
            }.getOrNull() ?: return@launch
            shown = listOf(place)
            placeMarkers(shown)
            select(place, pan = true)
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun metaText(text: String) = TextView(this).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_Krds_Meta)
        setPadding(0, dp(12), 0, dp(12))
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        binding.searchInput.clearFocus()
    }

    private fun snack(message: String) {
        if (message.isBlank()) return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_PLACE_ID = "place_id"
        private const val EXTRA_PLACE_NAME = "place_name"

        /** Same origin map.html uses for its first view. */
        private val SCHOOL = GeoPoint(37.4313541, 126.9892723)
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. M. d.")

        fun intent(context: Context, placeId: String? = null, placeName: String? = null): Intent =
            Intent(context, MapActivity::class.java).putExtra(EXTRA_PLACE_ID, placeId).putExtra(EXTRA_PLACE_NAME, placeName)
    }
}
