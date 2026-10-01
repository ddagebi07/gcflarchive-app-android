package kr.co.gcflarchive.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelsTest {

    private val docsJson = """
        {"documents":[
          {"filename":"25-212-영어독해와작문(영어과).pdf","displayName":"25-212-영어독해와작문(영어과).pdf","size":1048576,
           "modified":"2026-07-01T00:00:00Z","category":"기출","tags":["영어"],"visibility":"public","hasAnswerSheet":true},
          {"filename":"a1b2.pdf","displayName":"2024 수학 기말.pdf","size":10,"modified":"2026-01-01T00:00:00Z","category":"기출",
           "examMeta":{"year":"2024","grade":1,"period":"1학기 기말","subject":"공통수학1","cutoffs":"95, 88.5, 80"}},
          {"filename":"rule.pdf","displayName":"학생 생활규정.pdf","size":5,"modified":"2026-03-01T00:00:00Z","category":"학교 규정",
           "viewCount":10,"downloadCount":5},
          {"filename":"form.pdf","displayName":"현장체험학습 신청서.hwp","size":7,"modified":"2026-02-01T00:00:00Z","category":"행정서식"}
        ],"isAdmin":false}
    """.trimIndent()

    @Test
    fun examMetaFromFilenameConvention() {
        val doc = LibraryParser.documents(docsJson)[0]
        val m = doc.examMeta
        assertEquals(2025, m.year)
        assertEquals(2, m.grade)
        assertEquals("1학기 기말", m.period)
        assertEquals("영어독해와작문", m.subject)
        assertEquals("영어과", m.department)
        assertTrue(doc.hasAnswerSheet)
        assertEquals("25-212-영어독해와작문(영어과)", doc.title)
    }

    @Test
    fun examMetaOverridesWin() {
        val m = LibraryParser.documents(docsJson)[1].examMeta
        assertEquals(2024, m.year)
        assertEquals(1, m.grade)
        assertEquals("공통", m.department)
        assertEquals("공통수학1", m.subject)
    }

    @Test
    fun examFiltersMatchLikeTheWebsite() {
        val docs = LibraryParser.documents(docsJson).filter { it.isPastExam }
        assertEquals(2, docs.size)
        val f = ExamFilters(grades = setOf(2), departments = setOf("영어과"))
        assertEquals(listOf("25-212-영어독해와작문(영어과).pdf"), docs.filter { f.matches(it, emptySet(), "") }.map { it.filename })
        // Subject filter matches ignoring spaces.
        assertEquals(1, docs.count { ExamFilters(subjects = setOf("공통 수학1")).matches(it, emptySet(), "") })
        // Text search, favorites.
        assertEquals(1, docs.count { ExamFilters().matches(it, emptySet(), "독해") })
        assertEquals(0, docs.count { ExamFilters(favoritesOnly = true).matches(it, emptySet(), "") })
        assertEquals(listOf(2025, 2024), docs.sortedWith(ExamFilters.ORDER).map { it.examMeta.year })
    }

    @Test
    fun documentCategoriesAndSort() {
        val docs = LibraryParser.documents(docsJson).filterNot { it.isPastExam }
        assertEquals(listOf("rule.pdf"), docs.filter { DocCategory.RULE.matches(it.category) }.map { it.filename })
        assertEquals(listOf("form.pdf"), docs.filter { DocCategory.ADMIN.matches(it.category) }.map { it.filename })
        assertEquals("rule.pdf", docs.sortedWith(DocSort.DATE_DESC.comparator).first().filename)
        assertEquals("rule.pdf", docs.sortedWith(DocSort.VIEWS_DESC.comparator).first().filename)
        assertEquals("현장체험학습 신청서", docs[1].title)
    }

    @Test
    fun cutoffFormats() {
        assertEquals(listOf("1등급" to "95.0", "2등급" to "88.5", "3등급" to "80.0"), parseCutoffs("95, 88.5, 80"))
        assertEquals(listOf("1등급" to "92", "2등급" to "85"), parseCutoffs("1등급:92; 2등급=85"))
        assertEquals(listOf("A" to "90"), parseCutoffs("""[{"grade":"A","score":90}]"""))
        assertTrue(parseCutoffs("  ").isEmpty())
    }

    @Test
    fun subjectsVideosAndPhotos() {
        val cfg = LibraryParser.subjects("""{"groups":[{"label":"국어","subjects":["문학","독서"]},{"label":"빈","subjects":[]}]}""")
        assertEquals(listOf(SubjectGroup("국어", listOf("문학", "독서"))), cfg.groups)
        assertEquals(ExamFilters.DEFAULT_DEPARTMENTS, cfg.departments)

        val videos = LibraryParser.videos(
            """{"videos":[{"youtubeUrl":"https://youtu.be/dQw4w9WgXcQ","collectionName":"2026 축제","tags":["축제"]},{"youtubeUrl":""}]}""",
        )
        assertEquals(1, videos.size)
        assertEquals("dQw4w9WgXcQ", videos[0].youtubeId)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", videos[0].thumbnailUrl)
        assertEquals("행사", videos[0].category)
        assertEquals("abcdefghijk", youtubeIdOf("https://www.youtube.com/watch?v=abcdefghijk&t=3"))
        assertNull(youtubeIdOf("https://example.com"))

        val albums = LibraryParser.photoAlbums(
            """{"albums":[{"id":"1","title":"","collectionName":"체육대회","albumUrl":"https://photos.app.goo.gl/x","resolvedThumbnailUrl":"https://t/1.jpg","performers":["2-3반"]}]}""",
        )
        assertEquals("체육대회", albums[0].title)
        assertEquals("https://t/1.jpg", albums[0].thumbnailUrl)
        assertTrue(albums[0].searchHaystack().contains("2-3반"))
    }

    @Test
    fun formatting() {
        assertEquals("1.0 MB", formatBytes(1048576))
        assertEquals("-", formatBytes(0))
        assertEquals("2026.07.01", displayDate("2026-07-01T00:00:00Z"))
    }
}
