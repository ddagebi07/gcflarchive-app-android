package kr.co.gcflarchive.core.school

import kotlinx.datetime.daysUntil
import io.ktor.http.encodeURLParameter
import kr.co.gcflarchive.core.net.JObject
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.UploadFile
import kr.co.gcflarchive.core.net.jsonOf
import kr.co.gcflarchive.core.net.objects
import kr.co.gcflarchive.core.net.strings

/**
 * 학교생활: 시간표·보강, 연락, 1:1 대화, 질문(방문 신청), 소속, 알림
 * (routes/school_timetable.py, routes/school_contact.py on the server).
 */

enum class SchoolRole { STUDENT, TEACHER, OTHER }

data class SchoolMe(
    val loggedIn: Boolean,
    val userId: String,
    val role: SchoolRole,
    val teacherName: String?,
    val canManageGroups: Boolean,
    val unreadNotifications: Int,
    val unreadMessages: Int,
    val pendingVisits: Int,
    val pendingSubstitutes: Int,
)

// ── 시간표 ─────────────────────────────────────────────────────────────────

/** 학교생활 화면의 교사 이름 표기: "이혜경" → "이혜경T", 시간표 이름 "이외고(영)" → "이외고T(영)". */
fun teacherLabel(name: String): String {
    val n = name.trim()
    val m = Regex("""^(.*?)\s*(\([^()]*\))?$""").find(n) ?: return n
    val base = m.groupValues[1]
    if (base.isEmpty() || base.endsWith("T")) return n
    return base + "T" + m.groupValues[2]
}

data class TtPeriod(val key: String, val label: String, val start: String, val end: String, val isClass: Boolean)
data class TtDay(val date: String, val weekday: String, val isToday: Boolean)

data class TtCell(
    val subject: String,
    val teachers: List<String>,
    val room: String,
    val place: String,
    val moving: Boolean,
    val changed: Boolean,
    val empty: Boolean,
    val note: String,
    /** Teacher view: this class was handed to another teacher (보강). */
    val handedOver: Boolean,
    /** 교과목명 파일의 정식 이름 (예: 심F → 심화영어 Ⅱ); "" when none. */
    val fullName: String = "",
    /** 수업 교과 (예: 영어과); "" or 전체과 when it's for everyone. */
    val track: String = "",
) {
    /** "심화영어 Ⅱ (심F)" or just "문학". */
    val title: String get() = if (fullName.isNotBlank()) "$fullName ($subject)" else subject
    val trackLabel: String get() = track.takeIf { it.isNotBlank() && it != "전체과" }.orEmpty()

    /** Grid cell text: 줄임말 (심F), or the full name when [full] and there is one. */
    fun shortOrFull(full: Boolean): String = if (full && fullName.isNotBlank()) fullName else subject

    /** "2-3 · 물리실 · 최과학T" */
    val detail: String get() = listOf(place, room, teachers.joinToString(", ") { teacherLabel(it) }).filter { it.isNotBlank() }.joinToString(" · ")
}

data class TtWeek(
    val kind: String,
    val title: String,
    val days: List<TtDay>,
    val periods: List<TtPeriod>,
    /** cells[day][period - 1], period 1..7 */
    val cells: List<List<TtCell>>,
    val hasTimetable: Boolean,
) {
    val classPeriods: List<TtPeriod> get() = periods.filter { it.isClass }
    val todayIndex: Int get() = days.indexOfFirst { it.isToday }.coerceAtLeast(0)
    fun cell(day: Int, period: TtPeriod): TtCell = cells.getOrNull(day)?.getOrNull(period.key.toInt() - 1) ?: EMPTY_CELL
}

val EMPTY_CELL = TtCell("", emptyList(), "", "", false, false, true, "", false)

data class FreeTeacher(val teacherId: String, val name: String, val department: String, val asked: Boolean)
data class FreeTeachers(val date: String, val period: Int, val myClasses: List<String>, val teachers: List<FreeTeacher>)

data class SubRequest(
    val id: Int,
    val date: String,
    val weekday: String,
    val period: Int,
    val note: String,
    val status: String,
    val requesterName: String,
    val candidateName: String,
) {
    val statusLabel: String get() = SUB_STATUS[status] ?: status
}

val SUB_STATUS = mapOf("pending" to "답을 기다려요", "accepted" to "수락", "declined" to "거절", "cancelled" to "취소")

data class SubRequests(val sent: List<SubRequest>, val received: List<SubRequest>)

data class ClassRef(val grade: Int, val klass: Int) {
    val label: String get() = "${grade}학년 ${klass}반"
    val key: String get() = "$grade-$klass"
}

// ── 연락·대화 ──────────────────────────────────────────────────────────────

data class SchoolFile(val id: String, val name: String, val size: Long, val url: String) {
    val sizeLabel: String get() = if (size >= 1024 * 1024) "${(size * 10 / (1024 * 1024)) / 10.0}MB" else "${(size / 1024).coerceAtLeast(1)}KB"
}

data class SchoolMessage(
    val id: Int,
    val title: String,
    val senderName: String,
    val audience: String,
    val createdAt: String,
    val preview: String,
    val fileCount: Int,
    val allowReply: Boolean,
    val read: Boolean,
    val readCount: Int,
    val recipientCount: Int,
    val byClass: Boolean,
)

data class MessageDetail(
    val summary: SchoolMessage,
    val body: String,
    val files: List<SchoolFile>,
    val threadId: Int?,
    /** Sender view only. */
    val readers: List<String>?,
    val unread: List<String>,
)

data class ThreadSummary(
    val id: Int,
    val subject: String,
    val teacherName: String,
    val studentKey: String,
    val lastPost: String,
    val updatedAt: String,
    val unread: Boolean,
)

data class ThreadPost(val id: Int, val mine: Boolean, val authorName: String, val body: String, val createdAt: String, val files: List<SchoolFile>)
data class ThreadDetail(val summary: ThreadSummary, val posts: List<ThreadPost>)

/** 받는 사람: 학년 전체, 학급("2-3"), 소속 id, 학번 목록. */
data class Audience(
    val grades: List<Int> = emptyList(),
    val classes: List<String> = emptyList(),
    val groups: List<Int> = emptyList(),
    val hakbuns: String = "",
) {
    val isEmpty: Boolean get() = grades.isEmpty() && classes.isEmpty() && groups.isEmpty() && hakbuns.isBlank()
}

// ── 질문 (방문 신청) ───────────────────────────────────────────────────────

data class TeacherCard(val teacherId: String, val name: String, val department: String, val duties: List<String>, val location: String)

data class VisitOption(val period: String, val label: String, val start: String, val end: String) {
    val text: String get() = "$label ($start~$end)"
}

/** [dates]: the days a student can pick (today if time is left, through 3 weeks; weekdays). */
data class VisitOptions(val teacher: TeacherCard, val date: String, val defaultDate: String, val options: List<VisitOption>, val dates: List<String> = emptyList())

/** 질문 날짜 표기 'mm/dd(오늘)': 오늘·내일·모레, 그 뒤로는 '목요일'. [iso] and [today] are yyyy-mm-dd. */
fun visitDayText(iso: String, today: kotlinx.datetime.LocalDate): String {
    val d = runCatching { kotlinx.datetime.LocalDate.parse(iso) }.getOrNull() ?: return iso
    val rel = when (today.daysUntil(d)) {
        0 -> "오늘"
        1 -> "내일"
        2 -> "모레"
        else -> "${"월화수목금토일"[d.dayOfWeek.ordinal]}요일"
    }
    return "${d.monthNumber.toString().padStart(2, '0')}/${d.dayOfMonth.toString().padStart(2, '0')}($rel)"
}

data class VisitSlot(val date: String, val period: String, val label: String)

data class VisitHistory(val type: String, val fromName: String, val toName: String, val note: String, val notified: Boolean)

data class Visit(
    val id: Int,
    val studentKey: String,
    val studentName: String,
    val purpose: String,
    val slots: List<VisitSlot>,
    val status: String,
    val acceptedIndex: Int?,
    val teacherNote: String,
    val teacherName: String,
    val location: String,
    val createdAt: String,
    val threadId: Int?,
    val history: List<VisitHistory>,
) {
    val statusLabel: String get() = VISIT_STATUS[status] ?: status
    val forwards: List<VisitHistory> get() = history.filter { it.type == "forwarded" }
}

val VISIT_STATUS = mapOf("pending" to "답을 기다려요", "answered" to "답변했어요", "accepted" to "방문 수락", "cancelled" to "취소")

// ── 소속 ───────────────────────────────────────────────────────────────────

data class GroupMember(val hakbun: String, val role: String)

data class SchoolGroup(
    val id: Int,
    val kind: String,
    val category: String,
    val name: String,
    val memberCount: Int,
    val canEdit: Boolean,
    val members: List<GroupMember> = emptyList(),
) {
    val isPrivate: Boolean get() = kind == "private"
    /** "20315 부장\n20301" — the edit box text. */
    val membersText: String get() = members.joinToString("\n") { if (it.role.isBlank()) it.hakbun else "${it.hakbun} ${it.role}" }
}

data class GroupList(val groups: List<SchoolGroup>, val canManageShared: Boolean, val categories: List<String>)

// ── 알림 ───────────────────────────────────────────────────────────────────

data class SchoolNotification(val id: Int, val kind: String, val title: String, val body: String, val target: String, val createdAt: String, val read: Boolean)

/** Where an alert opens: "visit:12" → (VISIT, 12). */
enum class TargetKind { MESSAGE, THREAD, VISIT, SUBSTITUTE, TIMETABLE, NONE }

data class AlertTarget(val kind: TargetKind, val id: Int?)

fun parseTarget(target: String): AlertTarget {
    val kind = when (target.substringBefore(':')) {
        "message" -> TargetKind.MESSAGE
        "thread" -> TargetKind.THREAD
        "visit" -> TargetKind.VISIT
        "substitute" -> TargetKind.SUBSTITUTE
        "timetable" -> TargetKind.TIMETABLE
        else -> TargetKind.NONE
    }
    return AlertTarget(kind, target.substringAfter(':', "").toIntOrNull())
}

// ── parsing (pure, unit-tested) ────────────────────────────────────────────

object SchoolParse {
    fun me(o: JObject): SchoolMe {
        val unread = o.optJSONObject("unread") ?: JObject()
        return SchoolMe(
            loggedIn = o.optBoolean("loggedIn"),
            userId = o.optString("userId"),
            role = when (o.optString("role")) { "teacher" -> SchoolRole.TEACHER; "student" -> SchoolRole.STUDENT; else -> SchoolRole.OTHER },
            teacherName = o.optJSONObject("teacher")?.str("name"),
            canManageGroups = o.optBoolean("canManageGroups"),
            unreadNotifications = unread.optInt("notifications"),
            unreadMessages = unread.optInt("messages"),
            pendingVisits = unread.optInt("visits"),
            pendingSubstitutes = unread.optInt("substitutes"),
        )
    }

    fun week(o: JObject): TtWeek {
        val cells = o.optJSONArray("cells")?.raw.orEmpty().map { row ->
            (row as? kotlinx.serialization.json.JsonArray).orEmpty().map { c ->
                val j = JObject(c as? kotlinx.serialization.json.JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap()))
                TtCell(
                    subject = j.optString("subject"), teachers = j.optJSONArray("teachers").strings(),
                    room = j.optString("room"), place = j.optString("place"), moving = j.optBoolean("moving"),
                    changed = j.optBoolean("changed"), empty = j.optBoolean("empty", true), note = j.optString("note"),
                    handedOver = j.optBoolean("handedOver"),
                    fullName = j.optString("fullName"), track = j.optString("track"),
                )
            }
        }
        return TtWeek(
            kind = o.optString("kind"), title = o.optString("title"),
            days = o.optJSONArray("days").objects().map { TtDay(it.optString("date"), it.optString("weekday"), it.optBoolean("isToday")) },
            periods = o.optJSONArray("periods").objects().map {
                TtPeriod(it.optString("key"), it.optString("label"), it.optString("start"), it.optString("end"), it.optBoolean("isClass"))
            },
            cells = cells, hasTimetable = o.optBoolean("hasTimetable"),
        )
    }

    fun sub(o: JObject) = SubRequest(
        id = o.optInt("id"), date = o.optString("date"), weekday = o.optString("weekday"), period = o.optInt("period"),
        note = o.optString("note"), status = o.optString("status"),
        requesterName = o.optString("requesterName"), candidateName = o.optString("candidateName"),
    )

    fun file(o: JObject) = SchoolFile(o.optString("id"), o.optString("name"), o.optLong("size"), o.optString("url"))

    fun message(o: JObject) = SchoolMessage(
        id = o.optInt("id"), title = o.optString("title"), senderName = o.optString("senderName"), audience = o.optString("audience"),
        createdAt = o.optString("createdAt"), preview = o.optString("preview"), fileCount = o.optInt("fileCount"),
        allowReply = o.optBoolean("allowReply", true), read = o.optBoolean("read", true), readCount = o.optInt("readCount"),
        recipientCount = o.optInt("recipientCount"), byClass = o.optBoolean("byClass"),
    )

    fun messageDetail(o: JObject) = MessageDetail(
        summary = message(o), body = o.optString("body"), files = o.optJSONArray("files").objects().map(::file),
        threadId = o.optInt("threadId", 0).takeIf { it > 0 },
        readers = if (o.has("readers")) o.optJSONArray("readers").objects().map { it.optString("hakbun") } else null,
        unread = o.optJSONArray("unread").strings(),
    )

    fun thread(o: JObject) = ThreadSummary(
        id = o.optInt("id"), subject = o.optString("subject"), teacherName = o.optString("teacherName"),
        studentKey = o.optString("studentKey"), lastPost = o.optString("lastPost"), updatedAt = o.optString("updatedAt"),
        unread = o.optBoolean("unread"),
    )

    fun threadDetail(o: JObject) = ThreadDetail(thread(o), o.optJSONArray("posts").objects().map {
        ThreadPost(it.optInt("id"), it.optBoolean("mine"), it.optString("authorName"), it.optString("body"), it.optString("createdAt"),
            it.optJSONArray("files").objects().map(::file))
    })

    fun teacher(o: JObject) = TeacherCard(o.optString("teacherId"), o.optString("name"), o.optString("department"),
        o.optJSONArray("duties").strings(), o.optString("location"))

    fun visitOptions(o: JObject) = VisitOptions(
        teacher = teacher(o.optJSONObject("teacher") ?: JObject()), date = o.optString("date"), defaultDate = o.optString("defaultDate"),
        options = o.optJSONArray("options").objects().map { VisitOption(it.optString("period"), it.optString("label"), it.optString("start"), it.optString("end")) },
        dates = o.optJSONArray("dates").strings(),
    )

    fun visit(o: JObject) = Visit(
        id = o.optInt("id"), studentKey = o.optString("studentKey"), studentName = o.optString("studentName"), purpose = o.optString("purpose"),
        slots = o.optJSONArray("slots").objects().map { VisitSlot(it.optString("date"), it.optString("period"), it.optString("label")) },
        status = o.optString("status"), acceptedIndex = if (o.isNull("acceptedIndex")) null else o.optInt("acceptedIndex"),
        teacherNote = o.optString("teacherNote"), teacherName = o.optString("teacherName"), location = o.optString("location"),
        createdAt = o.optString("createdAt"), threadId = o.optInt("threadId", 0).takeIf { it > 0 },
        history = o.optJSONArray("history").objects().map {
            VisitHistory(it.optString("type"), it.optString("fromName"), it.optString("toName"), it.optString("note"), it.optBoolean("notified", true))
        },
    )

    fun group(o: JObject) = SchoolGroup(
        id = o.optInt("id"), kind = o.optString("kind"), category = o.optString("category"), name = o.optString("name"),
        memberCount = o.optInt("memberCount"), canEdit = o.optBoolean("canEdit"),
        members = o.optJSONArray("members").objects().map { GroupMember(it.optString("hakbun"), it.optString("role")) },
    )

    fun notification(o: JObject) = SchoolNotification(o.optInt("id"), o.optString("kind"), o.optString("title"), o.optString("body"),
        o.optString("target"), o.optString("createdAt"), o.optBoolean("read"))
}

/** "(1~3지망 중) 고른 것" → request body slots; blanks dropped. */
fun visitSlotsBody(picks: List<Pair<String, String>>): List<Map<String, String>> =
    picks.filter { it.first.isNotBlank() && it.second.isNotBlank() }.map { mapOf("date" to it.first, "period" to it.second) }

// ── repository ─────────────────────────────────────────────────────────────

class SchoolRepository(private val site: SiteClient) {
    private fun enc(s: String) = s.encodeURLParameter()

    suspend fun me(): SchoolMe = SchoolParse.me(site.getJson("/api/school/me"))

    suspend fun myWeek(date: String? = null): TtWeek = SchoolParse.week(site.getJson("/api/timetable/me" + (date?.let { "?date=$it" } ?: "")))
    suspend fun classWeek(c: ClassRef, date: String? = null): TtWeek =
        SchoolParse.week(site.getJson("/api/timetable/class/${c.grade}/${c.klass}" + (date?.let { "?date=$it" } ?: "")))
    /** 교사별 시간표 (학생은 공개된 선생님만). */
    suspend fun teacherWeek(teacherId: String, date: String? = null): TtWeek =
        SchoolParse.week(site.getJson("/api/timetable/teacher/${enc(teacherId)}" + (date?.let { "?date=$it" } ?: "")))
    suspend fun classes(): List<ClassRef> = site.getJson("/api/timetable/classes").optJSONArray("classes").objects().map { ClassRef(it.optInt("grade"), it.optInt("klass")) }

    suspend fun freeTeachers(date: String, period: Int): FreeTeachers {
        val o = site.getJson("/api/timetable/free-teachers?date=$date&period=$period")
        return FreeTeachers(o.optString("date"), o.optInt("period"),
            o.optJSONArray("myClasses").objects().map { "${it.optString("place")} ${it.optString("subject")}".trim() },
            o.optJSONArray("teachers").objects().map { FreeTeacher(it.optString("teacherId"), it.optString("name"), it.optString("department"), it.optBoolean("asked")) })
    }

    suspend fun substitutes(): SubRequests {
        val o = site.getJson("/api/timetable/substitutes")
        return SubRequests(o.optJSONArray("sent").objects().map(SchoolParse::sub), o.optJSONArray("received").objects().map(SchoolParse::sub))
    }

    suspend fun askSubstitute(date: String, period: Int, candidateId: String, note: String) {
        site.postJson("/api/timetable/substitutes", jsonOf("date" to date, "period" to period, "candidateId" to candidateId, "note" to note))
    }

    /** action: accept | decline | cancel */
    suspend fun substituteAction(id: Int, action: String) { site.postJson("/api/timetable/substitutes/$id/$action") }

    suspend fun messages(): List<SchoolMessage> = site.getJson("/api/school/messages").optJSONArray("messages").objects().map(SchoolParse::message)
    suspend fun message(id: Int): MessageDetail = SchoolParse.messageDetail(site.getJson("/api/school/messages/$id"))
    suspend fun sendMessage(title: String, body: String, audience: Audience, fileIds: List<String>, allowReply: Boolean) {
        site.postJson("/api/school/messages", jsonOf(
            "title" to title, "body" to body, "files" to fileIds, "allowReply" to allowReply,
            "audience" to mapOf("grades" to audience.grades, "classes" to audience.classes, "groups" to audience.groups, "hakbuns" to audience.hakbuns),
        ))
    }
    /** Returns the 1:1 thread id. */
    suspend fun reply(messageId: Int, body: String, fileIds: List<String>): Int =
        site.postForJson("/api/school/messages/$messageId/reply", jsonOf("body" to body, "files" to fileIds)).optInt("threadId")

    suspend fun uploadFile(file: UploadFile): SchoolFile {
        val res = site.upload("/api/school/files", "file", listOf(file))
        if (res.code == 401) throw kr.co.gcflarchive.core.net.LoginRequiredException()
        if (!res.isSuccessful) throw kr.co.gcflarchive.core.net.ApiException(res.code, res.error ?: "파일을 올리지 못했어요.")
        return SchoolParse.file(res.json.optJSONObject("file") ?: JObject())
    }

    suspend fun threads(): List<ThreadSummary> = site.getJson("/api/school/threads").optJSONArray("threads").objects().map(SchoolParse::thread)
    suspend fun thread(id: Int): ThreadDetail = SchoolParse.threadDetail(site.getJson("/api/school/threads/$id"))
    suspend fun post(threadId: Int, body: String, fileIds: List<String>) {
        site.postJson("/api/school/threads/$threadId", jsonOf("body" to body, "files" to fileIds))
    }

    suspend fun teachers(q: String): List<TeacherCard> =
        site.getJson("/api/school/teachers?q=${enc(q)}").optJSONArray("teachers").objects().map(SchoolParse::teacher)
    suspend fun visitOptions(teacherId: String, date: String? = null): VisitOptions =
        SchoolParse.visitOptions(site.getJson("/api/school/visit-options?teacherId=${enc(teacherId)}" + (date?.let { "&date=$it" } ?: "")))
    suspend fun visits(status: String? = null): List<Visit> =
        site.getJson("/api/school/visits" + (status?.let { "?status=$it" } ?: "")).optJSONArray("visits").objects().map(SchoolParse::visit)
    suspend fun visit(id: Int): Visit = SchoolParse.visit(site.getJson("/api/school/visits/$id").optJSONObject("visit") ?: JObject())
    suspend fun askVisit(teacherId: String, purpose: String, name: String, slots: List<Pair<String, String>>): Visit =
        SchoolParse.visit(site.postForJson("/api/school/visits", jsonOf("teacherId" to teacherId, "purpose" to purpose, "name" to name,
            "slots" to visitSlotsBody(slots))).optJSONObject("visit") ?: JObject())
    suspend fun answerVisit(id: Int, body: String) { site.postJson("/api/school/visits/$id/answer", jsonOf("body" to body)) }
    suspend fun acceptVisit(id: Int, index: Int, note: String) { site.postJson("/api/school/visits/$id/accept", jsonOf("index" to index, "note" to note)) }
    suspend fun forwardVisit(id: Int, teacherId: String, note: String, notifyStudent: Boolean) {
        site.postJson("/api/school/visits/$id/forward", jsonOf("teacherId" to teacherId, "note" to note, "notifyStudent" to notifyStudent))
    }
    suspend fun cancelVisit(id: Int) { site.postJson("/api/school/visits/$id/cancel") }

    suspend fun groups(): GroupList {
        val o = site.getJson("/api/school/groups")
        return GroupList(o.optJSONArray("groups").objects().map(SchoolParse::group), o.optBoolean("canManageShared"),
            o.optJSONArray("categories").strings().ifEmpty { listOf("동아리", "추가소속", "기타") })
    }
    suspend fun group(id: Int): SchoolGroup = SchoolParse.group(site.getJson("/api/school/groups/$id").optJSONObject("group") ?: JObject())
    /** id 0 = new. */
    suspend fun saveGroup(id: Int, kind: String, category: String, name: String, members: String) {
        val body = jsonOf("kind" to kind, "category" to category, "name" to name, "members" to members)
        if (id == 0) site.postJson("/api/school/groups", body) else site.checkedExchange("/api/school/groups/$id", "PUT", body)
    }
    suspend fun deleteGroup(id: Int) { site.checkedExchange("/api/school/groups/$id", "DELETE", null) }

    suspend fun notifications(since: String? = null): List<SchoolNotification> =
        site.getJson("/api/notifications" + (since?.let { "?since=${enc(it)}" } ?: "")).optJSONArray("notifications").objects().map(SchoolParse::notification)
    suspend fun markAllRead() { site.postJson("/api/notifications/read", jsonOf("all" to true)) }
    /** 안 읽은 알림 개수 (홈의 종 아이콘). */
    suspend fun unreadCount(): Int = site.getJson("/api/notifications?limit=1").optInt("unread")
    suspend fun markRead(id: Int) { site.postJson("/api/notifications/read", jsonOf("ids" to listOf(id))) }

    /**
     * New alerts since the last check, for platforms without push (or as a fallback). The first
     * check only sets the cursor. Returns nothing when this device has an FCM token (push shows them).
     */
    suspend fun newAlerts(prefs: kr.co.gcflarchive.core.prefs.AppPrefs, max: Int = 5): List<SchoolNotification> {
        val since = prefs.schoolAlertSince
        val list = notifications(since)
        list.maxOfOrNull { it.createdAt }?.let { prefs.schoolAlertSince = it }
        if (since == null) {
            if (list.isEmpty()) prefs.schoolAlertSince = kotlinx.datetime.Clock.System.now().toString()
            return emptyList()
        }
        return if (prefs.pushToken != null) emptyList() else list.filter { !it.read }.take(max)
    }

    /** Tells the server this device wants alerts: its FCM token, or "inbox:<install id>". */
    suspend fun registerThisDevice(prefs: kr.co.gcflarchive.core.prefs.AppPrefs, platform: String) {
        registerDevice(prefs.pushToken ?: "inbox:${prefs.installId}", platform)
    }

    /** FCM token, or "inbox:<install id>" when the device has no push. */
    suspend fun registerDevice(token: String, platform: String) {
        site.postJson("/api/push/register", jsonOf("token" to token, "platform" to platform, "app" to "student"))
    }
}

/** Exchange that throws like get/post for PUT/DELETE. */
suspend fun SiteClient.checkedExchange(path: String, method: String, body: kotlinx.serialization.json.JsonObject?): String {
    val res = exchange(path, method, body)
    if (res.code == 401) throw kr.co.gcflarchive.core.net.LoginRequiredException(res.error ?: "로그인하면 이용할 수 있어요.")
    if (!res.isSuccessful) throw kr.co.gcflarchive.core.net.ApiException(res.code, res.error ?: "처리하지 못했어요. 잠시 후 다시 시도해주세요.")
    return res.body
}
