package kr.co.gcflarchive.app.library

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Models and parsing for the site's file archives (기출 / PDF / 사진 / 영상).
 * Filtering rules are ported from past-exams.html, documents.html, photo.html and
 * video.html so the app lists exactly what the website lists.
 */

data class ExamMeta(
    val year: Int,
    val grade: Int,
    val department: String,
    val period: String,
    val subject: String,
    val cutoffs: String,
    val average: String,
)

data class LibraryDocument(
    val filename: String,
    val displayName: String,
    val size: Long,
    val modified: String,
    val collectionName: String,
    val category: String,
    val tags: List<String>,
    val visibility: String,
    val notes: String,
    val viewCount: Int,
    val downloadCount: Int,
    val hasAnswerSheet: Boolean,
    val examMetaOverride: JSONObject?,
) {
    val title: String get() = cleanTitle(displayName.ifBlank { filename })
    val isPdf: Boolean get() = filename.lowercase().endsWith(".pdf")
    val isPastExam: Boolean get() = category.contains("기출")
    val isPrivate: Boolean get() = visibility == "private"

    /** past-exams.html getExamMeta(): admin overrides first, then the filename convention. */
    val examMeta: ExamMeta by lazy {
        val ov = examMetaOverride
        val parsed = parseExamFilename(filename)
        ExamMeta(
            year = ov.intOrZero("year").takeIf { it > 0 } ?: parsed?.year ?: 0,
            grade = ov.intOrZero("grade").takeIf { it > 0 } ?: parsed?.grade ?: 0,
            department = ov.str("department") ?: parsed?.department ?: "공통",
            period = ov.str("period") ?: parsed?.period ?: "",
            subject = ov.str("subject") ?: parsed?.subject ?: "",
            cutoffs = ov.str("cutoffs") ?: "",
            average = ov.str("average") ?: "",
        )
    }

    /** Text the web search box matches against (lower-cased, NFC). */
    fun searchHaystack(): String = listOf(
        filename, displayName, collectionName, category, notes, tags.joinToString(" "),
        examMeta.subject, examMeta.period, examMeta.department,
    ).joinToString(" ").normalizedForSearch()
}

data class SubjectGroup(val label: String, val subjects: List<String>)

data class SubjectConfig(val groups: List<SubjectGroup>, val departments: List<String>)

data class AnswerSheet(
    val title: String,
    val subject: String,
    val year: String,
    val term: String,
    val teachers: List<String>,
    val answers: String,
    val essays: List<String>,
    val notes: String,
)

data class PhotoAlbum(
    val id: String,
    val title: String,
    val albumUrl: String,
    val albumDate: String,
    val collectionName: String,
    val copyright: String,
    val notes: String,
    val thumbnailUrl: String,
    val performers: List<String>,
) {
    fun searchHaystack(): String =
        listOf(title, collectionName, notes, copyright, performers.joinToString(" ")).joinToString(" ").normalizedForSearch()
}

data class VideoEntry(
    val youtubeUrl: String,
    val title: String,
    val category: String,
    val tags: List<String>,
    val notes: String,
    val timestamp: String,
) {
    val youtubeId: String? get() = youtubeIdOf(youtubeUrl)
    val thumbnailUrl: String? get() = youtubeId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    fun searchHaystack(): String =
        listOf(title, category, notes, tags.joinToString(" ")).joinToString(" ").normalizedForSearch()
}

/** documents.html category tabs. */
enum class DocCategory(val label: String) {
    ALL("전체"), STUDY("수업자료"), ADMIN("행정서식"), RULE("학교 규정/안내"), ETC("기타");

    /** Same predicates as documents.html applyFiltersAndSort(). */
    fun matches(category: String): Boolean = when (this) {
        ALL -> true
        STUDY -> category.contains("수업") || category.contains("학습")
        ADMIN -> category.contains("행정") || category.contains("서식")
        RULE -> category.contains("규정") || category.contains("일정") || category.contains("학교")
        ETC -> !category.contains("수업") && !category.contains("행정") && !category.contains("규정")
    }
}

enum class DocSort(val label: String) {
    DATE_DESC("최신 등록순"), DATE_ASC("오래된순"), VIEWS_DESC("조회수 많은순"), NAME_ASC("가나다순"), SIZE_DESC("파일 크기순");

    val comparator: Comparator<LibraryDocument>
        get() = when (this) {
            DATE_DESC -> compareByDescending { it.modified }
            DATE_ASC -> compareBy { it.modified }
            VIEWS_DESC -> compareByDescending { it.viewCount + it.downloadCount }
            NAME_ASC -> compareBy(KOREAN_COLLATOR) { it.title }
            SIZE_DESC -> compareByDescending { it.size }
        }
}

/** Multi-select filters of past-exams.html; an empty set means "전체". */
data class ExamFilters(
    val years: Set<Int> = emptySet(),
    val grades: Set<Int> = emptySet(),
    val departments: Set<String> = emptySet(),
    val periods: Set<String> = emptySet(),
    val subjects: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
) {
    val activeCount: Int
        get() = years.size + grades.size + departments.size + periods.size + subjects.size

    fun matches(doc: LibraryDocument, favorites: Set<String>, query: String): Boolean {
        val m = doc.examMeta
        if (years.isNotEmpty() && m.year !in years) return false
        if (grades.isNotEmpty() && m.grade !in grades) return false
        if (departments.isNotEmpty() && m.department !in departments) return false
        if (periods.isNotEmpty() && m.period !in periods) return false
        if (subjects.isNotEmpty()) {
            val subjectHay = m.subject.compact()
            val docHay = (doc.tags.joinToString(" ") + " " + doc.displayName.ifBlank { doc.filename }).compact()
            if (subjects.none { val s = it.compact(); subjectHay.contains(s) || docHay.contains(s) }) return false
        }
        if (favoritesOnly && doc.filename !in favorites) return false
        val q = query.normalizedForSearch().trim()
        return q.isEmpty() || doc.searchHaystack().contains(q)
    }

    companion object {
        val GRADES = listOf(1, 2, 3)
        val PERIODS = listOf("1학기 중간", "1학기 기말", "2학기 중간", "2학기 기말")
        val DEFAULT_DEPARTMENTS = listOf("공통", "영어과", "비영어과")

        /** Newest year first, then grade, then title (past-exams.html sort). */
        val ORDER: Comparator<LibraryDocument> =
            compareByDescending<LibraryDocument> { it.examMeta.year }
                .thenBy { it.examMeta.grade }
                .thenBy(KOREAN_COLLATOR) { it.title }
    }
}

val KOREAN_COLLATOR: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.KOREAN)

object LibraryParser {

    fun documents(body: String): List<LibraryDocument> {
        val arr = JSONObject(body).optJSONArray("documents") ?: return emptyList()
        return arr.objects().map { o ->
            LibraryDocument(
                filename = o.optString("filename"),
                displayName = o.str("displayName") ?: "",
                size = o.optLong("size"),
                modified = o.optString("modified"),
                collectionName = o.str("collectionName") ?: "",
                category = o.str("category") ?: "",
                tags = o.optJSONArray("tags").strings(),
                visibility = o.str("visibility") ?: "public",
                notes = o.str("notes") ?: "",
                viewCount = o.optInt("viewCount"),
                downloadCount = o.optInt("downloadCount"),
                hasAnswerSheet = o.optBoolean("hasAnswerSheet"),
                examMetaOverride = o.optJSONObject("examMeta"),
            )
        }.filter { it.filename.isNotBlank() }
    }

    fun subjects(body: String): SubjectConfig {
        val json = JSONObject(body)
        val groups = json.optJSONArray("groups")?.objects().orEmpty().map {
            SubjectGroup(it.optString("label").trim(), it.optJSONArray("subjects").strings())
        }.filter { it.subjects.isNotEmpty() }
        val departments = json.optJSONArray("departments").strings().ifEmpty { ExamFilters.DEFAULT_DEPARTMENTS }
        return SubjectConfig(groups, departments)
    }

    fun favorites(body: String): Set<String> = JSONObject(body).optJSONArray("favorites").strings().toSet()

    fun answerSheet(body: String): AnswerSheet {
        val o = JSONObject(body)
        return AnswerSheet(
            title = cleanTitle(o.str("displayName") ?: ""),
            subject = o.str("subject") ?: "",
            year = o.str("year") ?: "",
            term = o.str("term") ?: "",
            teachers = o.optJSONArray("teachers").strings(),
            answers = o.str("answers") ?: "",
            essays = o.optJSONArray("essays").strings(),
            notes = o.str("notes") ?: "",
        )
    }

    fun photoAlbums(body: String): List<PhotoAlbum> =
        JSONObject(body).optJSONArray("albums")?.objects().orEmpty().map { o ->
            PhotoAlbum(
                id = o.optString("id"),
                title = o.str("title") ?: o.str("collectionName") ?: "제목 없음",
                albumUrl = o.str("albumUrl") ?: "",
                albumDate = o.str("albumDate") ?: "",
                collectionName = o.str("collectionName") ?: "",
                copyright = o.str("copyright") ?: "",
                notes = o.str("notes") ?: "",
                thumbnailUrl = o.str("resolvedThumbnailUrl") ?: o.str("thumbnailUrl") ?: "",
                performers = o.optJSONArray("performers").strings(),
            )
        }

    fun videos(body: String): List<VideoEntry> =
        JSONObject(body).optJSONArray("videos")?.objects().orEmpty().map { o ->
            VideoEntry(
                youtubeUrl = o.str("youtubeUrl") ?: "",
                title = o.str("collectionName") ?: "제목 없음",
                category = o.str("category") ?: "행사",
                tags = o.optJSONArray("tags").strings(),
                notes = o.str("notes") ?: "",
                timestamp = o.str("timestamp") ?: "",
            )
        }.filter { it.youtubeUrl.isNotBlank() }
}

/** Filename convention "YY-GSE-과목(학과).pdf" (G=학년, S=학기, E=1 중간 / 2 기말). */
internal fun parseExamFilename(filename: String): ExamMeta? {
    val m = Regex("""^(\d{2})-(\d)(\d)(\d)-(.+?)(?:\.\w+)?$""").find(filename) ?: return null
    val (yy, grade, sem, examType, rest) = m.destructured
    val period = (if (sem == "1") "1학기 " else "2학기 ") + (if (examType == "1") "중간" else "기말")
    var subject = rest.trim()
    var department = "공통"
    Regex("""(.+?)\((공통|영어과|비영어과)\)$""").find(subject)?.let {
        subject = it.groupValues[1].trim()
        department = it.groupValues[2].trim()
    }
    return ExamMeta(2000 + yy.toInt(), grade.toInt(), department, period, subject, "", "")
}

/** past-exams.html openCutoffsModal(): JSON list, plain score list, or "1등급:95, 2등급:88". */
fun parseCutoffs(raw: String): List<Pair<String, String>> {
    val text = raw.trim()
    if (text.isEmpty()) return emptyList()
    if (text.startsWith("[")) {
        runCatching {
            val arr = JSONArray(text)
            return arr.objects().map { o ->
                (o.str("grade") ?: o.str("등급") ?: "-") to (o.str("score") ?: o.str("원점수") ?: "-")
            }
        }
    }
    val parts = text.split(Regex("""[\s,/;]+""")).filter { it.isNotBlank() }
    if (parts.isNotEmpty() && parts.all { it.toDoubleOrNull() != null }) {
        return parts.mapIndexed { i, s -> "${i + 1}등급" to String.format(java.util.Locale.ROOT, "%.1f", s.toDouble()) }
    }
    return text.split(Regex("[,;]+")).map { it.trim() }.filter { it.isNotEmpty() }.map { item ->
        val m = Regex("""(.+?)(?::|\s+|=)\s*(\d+(\.\d+)?)""").find(item)
        if (m != null) m.groupValues[1].trim() to m.groupValues[2].trim() else item to "-"
    }
}

fun youtubeIdOf(url: String): String? =
    Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?v=|embed/|shorts/|live/))([\w-]{11})""").find(url)?.groupValues?.get(1)

fun cleanTitle(name: String): String =
    name.replace(Regex("""\.(pdf|hwp|hwpx|docx?|xlsx?|pptx?|zip|png|jpe?g|txt)$""", RegexOption.IGNORE_CASE), "").trim()

/** "2026-09-15T03:00:00Z" → "2026.09.15" */
fun displayDate(iso: String): String = iso.take(10).replace('-', '.')

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "-"
    val units = listOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format(java.util.Locale.ROOT, "%.1f %s", v, units[i])
}

internal fun String.normalizedForSearch(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFC)

private fun String.compact(): String = lowercase().replace(Regex("""\s+"""), "")

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { i -> opt(i)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" } }

private fun JSONObject?.str(key: String): String? {
    if (this == null || !has(key) || isNull(key)) return null
    return opt(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JSONObject?.intOrZero(key: String): Int = str(key)?.toDoubleOrNull()?.toInt() ?: 0
