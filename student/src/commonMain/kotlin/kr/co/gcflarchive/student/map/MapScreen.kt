package kr.co.gcflarchive.student.map

import kr.co.gcflarchive.student.platform.MapPin
import kr.co.gcflarchive.core.util.fixed
import kr.co.gcflarchive.core.map.RecommendedIndex
import kr.co.gcflarchive.core.map.BusinessTypes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.encPath
import kr.co.gcflarchive.core.grade.GradeLogic
import kr.co.gcflarchive.core.map.DaySchedule
import kr.co.gcflarchive.core.map.MapLogic
import kr.co.gcflarchive.core.map.MapUser
import kr.co.gcflarchive.core.map.Place
import kr.co.gcflarchive.core.map.PlaceStats
import kr.co.gcflarchive.core.map.Review
import kr.co.gcflarchive.core.meal.MealRepository
import kr.co.gcflarchive.student.AppState
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.platform.PlaceMap
import kr.co.gcflarchive.student.platform.shareText
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsChip
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.userMessage

private fun AppState.loginNeeded(action: String) {
    toast("로그인하면 $action 기능을 쓸 수 있어요.")
    requireLogin()
}

/**
 * 지도 아카이브 (map.html): place search through the server's Kakao proxy, place details
 * with 후기, 즐겨찾기, 영업시간 제안, 장소 직접 등록. Apple 지도 on iOS, OSM on Android.
 * Phones: map above the list; iPad / wide: list beside the map.
 */
@Composable
fun MapScreen(placeId: String?, placeName: String?) {
    val app = LocalApp.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(MapUser(false, "")) }
    var stats by remember { mutableStateOf<Map<String, PlaceStats>>(emptyMap()) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf(placeName.orEmpty()) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var resultsTitle by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Place?>(null) }
    var busy by remember { mutableStateOf(false) }
    var askNickname by remember { mutableStateOf(false) }
    var registerDialog by remember { mutableStateOf(false) }
    var scheduleFor by remember { mutableStateOf<Place?>(null) }
    // 극플인의 추천 (places with reviews): colored pins by 업종 + the list shown before any search.
    var recommended by remember { mutableStateOf(RecommendedIndex.EMPTY) }
    var hiddenTypes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recommendedVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { stats = runCatching { app.services.map.stats() }.getOrDefault(emptyMap()) }
    LaunchedEffect(recommendedVersion) {
        recommended = runCatching { app.services.map.recommended() }.getOrDefault(recommended)
    }
    LaunchedEffect(app.sessionVersion) {
        user = runCatching { app.services.map.user() }.getOrDefault(MapUser(false, ""))
        if (user.verified) {
            favoriteIds = runCatching { app.services.map.favorites() }.getOrDefault(emptyList()).map { it.id }.toSet()
            if (user.nickname.isBlank()) askNickname = true
        }
    }
    // Shared link: /map?placeId=…&placeName=…
    LaunchedEffect(placeId) {
        val id = placeId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val place = runCatching {
            if (id.startsWith("cp_")) app.services.map.customPlaces().firstOrNull { it.id == id }
            else if (!placeName.isNullOrBlank()) app.services.map.search(placeName).firstOrNull { it.id == id }
            else null
        }.getOrNull() ?: return@LaunchedEffect
        results = listOf(place)
        selected = place
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        busy = true
        scope.launch {
            runCatching { app.services.map.search(q) }
                .onSuccess { places ->
                    results = places
                    selected = null
                    resultsTitle = if (places.isEmpty()) "검색 결과가 없어요." else "검색 결과 ${places.size}건"
                }
                .onFailure { if (it is CancellationException) throw it else app.toast("찾지 못했어요. 인터넷 연결을 확인하고 다시 시도해주세요.") }
            busy = false
        }
    }

    fun showFavorites() {
        busy = true
        scope.launch {
            val favs = runCatching { app.services.map.favorites() }.getOrDefault(emptyList())
            favoriteIds = favs.map { it.id }.toSet()
            results = favs
            selected = null
            resultsTitle = if (favs.isEmpty()) "즐겨찾기한 장소가 없어요." else "즐겨찾기 장소 ${favs.size}곳"
            busy = false
        }
    }

    ScreenScaffold(
        title = "지도 아카이브",
        onBack = {
            when {
                selected != null && (results.size > 1 || resultsTitle == null) -> selected = null
                resultsTitle != null -> { results = emptyList(); resultsTitle = null; selected = null } // back to 극플인의 추천
                else -> app.pop()
            }
        },
    ) {
        val panel: @Composable ColumnScope.() -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("상호명 또는 키워드 (과천시 내)") },
                    trailingIcon = { IconButton(onClick = ::search) { Icon(Icons.Filled.Search, "검색") } },
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("식당·카페·학원 등 · #해시태그 검색 가능", style = KrdsText.meta, modifier = Modifier.weight(1f))
                    if (user.verified) KrdsChip("★ 즐겨찾기", false, ::showFavorites)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Krds.colors.primary)
            }
            Hairline()
            val sel = selected
            if (sel != null) {
                PlaceDetail(
                    app, sel, user, stats[sel.id], starred = sel.id in favoriteIds,
                    businessType = recommended.typeOf(sel.id),
                    onBackToList = if (results.size > 1 || resultsTitle == null) ({ selected = null }) else null,
                    onFavorite = { starred -> favoriteIds = if (starred) favoriteIds + sel.id else favoriteIds - sel.id },
                    onNeedNickname = { askNickname = true },
                    onSuggestSchedule = { if (user.verified) scheduleFor = sel else app.loginNeeded("영업시간 제안") },
                    onReviewPosted = {
                        recommendedVersion++ // new 후기 → pin count / new pin
                        scope.launch { stats = runCatching { app.services.map.stats() }.getOrDefault(stats) }
                    },
                )
            } else {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    resultsTitle?.let { Text(it, style = KrdsText.label, modifier = Modifier.padding(vertical = 6.dp)) }
                    if (resultsTitle == null) {
                        RecommendedList(recommended, hiddenTypes, onToggleType = { t -> hiddenTypes = if (t in hiddenTypes) hiddenTypes - t else hiddenTypes + t }) { selected = it }
                    }
                    results.forEachIndexed { i, p ->
                        if (i > 0) Hairline()
                        val reviews = stats[p.id]?.reviewCount ?: 0
                        Row(Modifier.fillMaxWidth().clickable { selected = p }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = KrdsText.cardTitle)
                                Text(p.displayAddress, style = KrdsText.meta)
                            }
                            if (reviews > 0) Badge("후기 $reviews", BadgeTone.INFO) else if (p.isCustom) Badge("직접 등록", BadgeTone.PURPLE)
                        }
                    }
                    if (resultsTitle != null) {
                        Text("찾으시는 장소가 없나요?", style = KrdsText.meta, modifier = Modifier.padding(top = 16.dp))
                        SecondaryButton("장소 직접 등록", {
                            if (user.verified) registerDialog = true else app.loginNeeded("장소 직접 등록")
                        }, Modifier.fillMaxWidth().padding(top = 6.dp), icon = Icons.Filled.AddLocationAlt)
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Colored 극플인의 추천 pins always (unless filtered); search results / the selected place on top.
            val recPins = recommended.places.filter { it.businessType !in hiddenTypes }
                .map { MapPin(it.place, BusinessTypes.color(it.businessType), it.reviewCount.toString()) }
            val recIds = recPins.map { it.place.id }.toSet()
            val extra = results.ifEmpty { listOfNotNull(selected) }.filter { it.id !in recIds }.map { MapPin(it) }
            val shownPlaces = recPins + extra
            if (maxWidth >= 700.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState()), content = panel)
                    PlaceMap(shownPlaces, selected, { selected = it }, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PlaceMap(shownPlaces, selected, { selected = it }, Modifier.fillMaxWidth().weight(0.42f))
                    Column(Modifier.fillMaxWidth().weight(0.58f).verticalScroll(rememberScrollState()), content = panel)
                }
            }
        }
    }

    if (askNickname) NicknameDialog(app, onDismiss = { askNickname = false }) { name -> user = user.copy(nickname = name); askNickname = false }
    if (registerDialog) RegisterPlaceDialog(app, query, onDismiss = { registerDialog = false })
    scheduleFor?.let { p -> ScheduleDialog(app, p, stats[p.id]?.schedule.orEmpty(), onDismiss = { scheduleFor = null }) }
}

/** 극플인의 추천: places with 후기, most reviewed first; 업종 chips show / hide their pins. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendedList(index: RecommendedIndex, hidden: Set<String>, onToggleType: (String) -> Unit, onSelect: (Place) -> Unit) {
    if (index.places.isEmpty()) {
        InfoBox("가게 이름이나 키워드로 학교 주변 장소를 찾아보세요. 후기가 달린 장소는 '극플인의 추천'으로 지도에 표시돼요.")
        return
    }
    val visible = index.places.filter { it.businessType !in hidden }
    Text("극플인의 추천 | ${visible.size}곳", style = KrdsText.sectionTitle, modifier = Modifier.padding(vertical = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        index.types.forEach { (type, count) ->
            val on = type !in hidden
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, if (on) Color(BusinessTypes.color(type)) else Krds.colors.border, RoundedCornerShape(50))
                    .background(if (on) Color(BusinessTypes.color(type)).copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onToggleType(type) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(BusinessTypes.color(type)).copy(alpha = if (on) 1f else 0.35f)))
                Spacer(Modifier.width(6.dp))
                Text("$type $count", style = KrdsText.label.copy(color = if (on) Krds.colors.textStrong else Krds.colors.textDisabled))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    visible.forEachIndexed { i, rec ->
        if (i > 0) Hairline()
        Row(Modifier.fillMaxWidth().clickable { onSelect(rec.place) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(BusinessTypes.color(rec.businessType))), contentAlignment = Alignment.Center) {
                Text(rec.reviewCount.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.place.name, style = KrdsText.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(rec.businessType).append(" · 후기 ").append(rec.reviewCount)
                        rec.avgRating?.let { append(" · ★").append(it.fixed(1)) }
                    },
                    style = KrdsText.meta.copy(color = Color(BusinessTypes.color(rec.businessType))),
                )
                Text(rec.place.displayAddress, style = KrdsText.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlaceDetail(
    app: AppState,
    place: Place,
    user: MapUser,
    stats: PlaceStats?,
    starred: Boolean,
    businessType: String?,
    onBackToList: (() -> Unit)?,
    onFavorite: (Boolean) -> Unit,
    onNeedNickname: () -> Unit,
    onSuggestSchedule: () -> Unit,
    onReviewPosted: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var views by remember(place.id) { mutableStateOf<Int?>(null) }
    var reviews by remember(place.id) { mutableStateOf<Result<List<Review>>?>(null) }
    var reviewsVersion by remember(place.id) { mutableIntStateOf(0) }
    var rating by remember(place.id) { mutableIntStateOf(0) }
    var content by remember(place.id) { mutableStateOf("") }
    var tags by remember(place.id) { mutableStateOf("") }
    var anonymous by remember(place.id) { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }

    LaunchedEffect(place.id) { views = runCatching { app.services.map.view(place.id) }.getOrNull() }
    LaunchedEffect(place.id, reviewsVersion) {
        reviews = null
        reviews = runCatching { app.services.map.reviews(place.id) }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBackToList != null) TextButton(onClick = onBackToList) { Text("← 검색 결과로", color = Krds.colors.primaryText) }
        if (place.category.isNotBlank()) Text((businessType?.let { "[$it] " } ?: "") + place.category, style = KrdsText.meta)
        Text(place.name, style = KrdsText.pageTitle)
        Text(place.displayAddress, style = KrdsText.body)
        views?.let { Text("조회수 $it", style = KrdsText.meta) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("길찾기", {
                uri.openUri("https://map.kakao.com/link/map/${encPath(place.name)},${place.y},${place.x}")
            }, Modifier.weight(1f), icon = Icons.Filled.Directions)
            SecondaryButton("공유", { shareText("${place.name}\n${MapLogic.shareUrl(Config.BASE_URL, place)}") }, Modifier.weight(1f), icon = Icons.Filled.Share)
        }
        if (user.verified) {
            SecondaryButton(if (starred) "즐겨찾기 해제" else "즐겨찾기", {
                scope.launch {
                    runCatching { app.services.map.toggleFavorite(place) }
                        .onSuccess { on -> onFavorite(on); app.toast(if (on) "즐겨찾기에 넣었어요." else "즐겨찾기에서 뺐어요.") }
                        .onFailure { if (it is CancellationException) throw it else app.toast(it.userMessage()) }
                }
            }, Modifier.fillMaxWidth(), icon = if (starred) Icons.Filled.Star else Icons.Filled.StarBorder)
        }

        val schedule = stats?.schedule
        if (!schedule.isNullOrEmpty()) {
            Text("영업 정보", style = KrdsText.label.copy(color = Krds.colors.textStrong))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MapLogic.DAYS.forEach { (key, label) ->
                    schedule[key]?.let { d ->
                        Row {
                            Text(label, style = KrdsText.body, modifier = Modifier.width(28.dp))
                            Text(MapLogic.describe(d), style = KrdsText.body.copy(color = if (d.holiday) Krds.colors.danger else Krds.colors.text))
                        }
                    }
                }
            }
        }
        TextButton(onClick = onSuggestSchedule) {
            Icon(Icons.Filled.Schedule, null, tint = Krds.colors.primaryText)
            Text(" 영업시간 제안", color = Krds.colors.primaryText)
        }

        Hairline()
        Text("후기", style = KrdsText.sectionTitle)
        when (val r = reviews) {
            null -> Text("후기를 불러오는 중…", style = KrdsText.meta)
            else -> r.fold(
                onFailure = { Text("후기를 불러오지 못했어요.", style = KrdsText.meta) },
                onSuccess = { list ->
                    if (list.isEmpty()) Text("아직 작성된 후기가 없어요. 첫 후기를 남겨보세요", style = KrdsText.meta)
                    list.forEach { rv ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rv.author, style = KrdsText.cardTitle.copy(fontSize = 14.sp))
                                Spacer(Modifier.width(8.dp))
                                if (rv.rating > 0) Text("★".repeat(rv.rating) + "☆".repeat(5 - rv.rating), color = Krds.colors.star, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                GradeLogic.parseInstant(rv.timestamp)?.toLocalDateTime(MealRepository.SEOUL)?.let {
                                    Text("${it.year}. ${it.monthNumber}. ${it.dayOfMonth}.", style = KrdsText.meta)
                                }
                            }
                            val text = buildString {
                                append(rv.content)
                                if (rv.hashtags.isNotEmpty()) append((if (rv.content.isBlank()) "" else "\n") + rv.hashtags.joinToString(" ") { "#$it" })
                            }
                            if (text.isNotBlank()) Text(text, style = KrdsText.body, modifier = Modifier.padding(top = 2.dp))
                        }
                        Hairline()
                    }
                },
            )
        }

        Text("후기 남기기", style = KrdsText.label.copy(color = Krds.colors.textStrong), modifier = Modifier.padding(top = 8.dp))
        if (!user.verified) {
            InfoBox("로그인 후 후기를 등록할 수 있어요.")
            PrimaryButton("로그인", { app.requireLogin() }, Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { n ->
                    IconButton(onClick = { rating = if (rating == n) 0 else n }) {
                        Icon(if (n <= rating) Icons.Filled.Star else Icons.Filled.StarBorder, "${n}점", tint = Krds.colors.star)
                    }
                }
            }
            Text(MapLogic.RATING_LABELS[rating.coerceIn(0, 5)], style = KrdsText.meta)
            OutlinedTextField(content, { content = it.take(500) }, Modifier.fillMaxWidth(), placeholder = { Text("후기 쓰기 (최대 500자)") }, minLines = 3)
            OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("해시태그 (예: #맛집 #추천)") })
            Row(Modifier.fillMaxWidth().clickable { anonymous = !anonymous }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(anonymous, { anonymous = it }, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
                Text("닉네임·학번 가리고 올리기 (게시 후 변경 불가)", style = KrdsText.body.copy(fontSize = 14.sp))
            }
            PrimaryButton("후기 등록", {
                if (user.nickname.isBlank()) return@PrimaryButton onNeedNickname()
                if (content.isBlank() && tags.isBlank()) return@PrimaryButton app.toast("후기 내용을 입력해주세요.")
                posting = true
                scope.launch {
                    runCatching { app.services.map.postReview(place, rating, content.trim(), tags.trim(), anonymous) }
                        .onSuccess {
                            app.toast("후기를 올렸어요.")
                            rating = 0; content = ""; tags = ""; anonymous = false
                            reviewsVersion++
                            onReviewPosted()
                        }
                        .onFailure { if (it is CancellationException) throw it else app.toast(it.userMessage("후기를 등록하지 못했어요.")) }
                    posting = false
                }
            }, Modifier.fillMaxWidth(), enabled = !posting)
        }
    }
}

@Composable
private fun NicknameDialog(app: AppState, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("최초 사용 닉네임 설정") },
        text = {
            Column {
                Text("후기에 보일 닉네임이에요. 커뮤니티에서도 같은 닉네임을 쓰고, 나중에 더보기 › 설정에서 바꿀 수 있어요. 한글·영문·숫자·밑줄로 2~15자예요.", style = KrdsText.meta)
                OutlinedTextField(name, { name = it; error = null }, Modifier.padding(top = 10.dp), singleLine = true, label = { Text("닉네임") },
                    isError = error != null, supportingText = { error?.let { Text(it) } })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                MapLogic.nicknameError(n)?.let { error = it; return@TextButton }
                scope.launch {
                    runCatching { app.services.map.registerNickname(n) }
                        .onSuccess { app.toast("닉네임을 '$n'(으)로 정했어요."); onDone(n) }
                        .onFailure { if (it is CancellationException) throw it else error = it.userMessage() }
                }
            }) { Text("설정 완료") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun RegisterPlaceDialog(app: AppState, initialName: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var road by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("") }
    var other by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("신규 장소 직접 등록") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("검색되지 않는 과천시 내 장소를 직접 등록해요. (관리자 승인 후 게시)", style = KrdsText.meta)
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("가게 이름 *") })
                OutlinedTextField(road, { road = it }, singleLine = true, label = { Text("도로명 주소 *") })
                OutlinedTextField(floor, { floor = it }, singleLine = true, label = { Text("층수") })
                OutlinedTextField(other, { other = it }, singleLine = true, label = { Text("기타 정보") })
                error?.let { Text(it, style = KrdsText.meta.copy(color = Krds.colors.danger)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (name.isBlank() || road.isBlank()) { error = "가게 이름과 도로명 주소를 입력해주세요."; return@TextButton }
                busy = true
                scope.launch {
                    runCatching {
                        val (x, y) = app.services.map.geocode(road.trim())
                        app.services.map.registerPlace(name.trim(), road.trim(), floor.trim(), other.trim(), x, y)
                    }.onSuccess {
                        app.toast("장소 등록을 신청했어요. 관리자가 확인하면 지도에 보여요.")
                        onDismiss()
                    }.onFailure { if (it is CancellationException) throw it else error = it.userMessage() }
                    busy = false
                }
            }) { Text("등록 신청") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun ScheduleDialog(app: AppState, place: Place, current: Map<String, DaySchedule>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val days = remember {
        MapLogic.DAYS.associate { (key, _) -> key to mutableStateOf(current[key] ?: DaySchedule("09:00", "21:00", "", "", false)) }
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("영업시간 제안 · ${place.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("요일별 영업시간, 브레이크타임, 휴무를 입력해주세요. 시간은 HH:MM 형식이에요. (관리자 승인 후 반영)", style = KrdsText.meta)
                MapLogic.DAYS.forEach { (key, label) ->
                    val state = days.getValue(key)
                    val d = state.value
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = KrdsText.cardTitle, modifier = Modifier.weight(1f))
                            Checkbox(d.holiday, { state.value = d.copy(holiday = it) })
                            Text("휴무", style = KrdsText.body)
                        }
                        if (!d.holiday) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TimeField("시작", d.open, Modifier.weight(1f)) { state.value = state.value.copy(open = it) }
                                TimeField("종료", d.close, Modifier.weight(1f)) { state.value = state.value.copy(close = it) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TimeField("브레이크 시작", d.breakStart, Modifier.weight(1f)) { state.value = state.value.copy(breakStart = it) }
                                TimeField("브레이크 끝", d.breakEnd, Modifier.weight(1f)) { state.value = state.value.copy(breakEnd = it) }
                            }
                        }
                    }
                }
                error?.let { Text(it, style = KrdsText.meta.copy(color = Krds.colors.danger)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val schedule = days.mapValues { (_, s) -> s.value.let { it.copy(open = it.open.trim(), close = it.close.trim(), breakStart = it.breakStart.trim(), breakEnd = it.breakEnd.trim()) } }
                val bad = schedule.values.any { d -> !d.holiday && listOf(d.open, d.close, d.breakStart, d.breakEnd).any { !MapLogic.isValidTime(it) } }
                if (bad) { error = "시간은 09:30처럼 HH:MM 형식으로 입력해주세요."; return@TextButton }
                scope.launch {
                    runCatching { app.services.map.suggestSchedule(place, schedule) }
                        .onSuccess { app.toast("영업 정보를 보냈어요. 관리자가 확인하면 반영해요."); onDismiss() }
                        .onFailure { if (it is CancellationException) throw it else error = it.userMessage() }
                }
            }) { Text("제출") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ':' }.take(5)) },
        modifier = modifier,
        singleLine = true,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text("09:00") },
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
    )
}
