package kr.co.gcflarchive.core

import kr.co.gcflarchive.core.archive.ArchiveRepository
import kr.co.gcflarchive.core.community.CommunityRepository
import kr.co.gcflarchive.core.auth.AuthApi
import kr.co.gcflarchive.core.drive.DriveRepository
import kr.co.gcflarchive.core.grade.GradeRepository
import kr.co.gcflarchive.core.library.LibraryRepository
import kr.co.gcflarchive.core.map.MapRepository
import kr.co.gcflarchive.core.meal.MealRepository
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.notice.NoticeRepository
import kr.co.gcflarchive.core.prefs.AppPrefs
import kr.co.gcflarchive.core.school.SchoolRepository
import kr.co.gcflarchive.core.survey.SurveyRepository

/** Everything the student app talks to, created once per process and shared by every screen. */
class Services(val site: SiteClient = SiteClient(), val prefs: AppPrefs = AppPrefs()) {
    val auth = AuthApi(site)
    val meals = MealRepository(site)
    val archive = ArchiveRepository(site)
    val library = LibraryRepository(site)
    val drive = DriveRepository(site)
    val grades = GradeRepository(site)
    val notices = NoticeRepository(site)
    val map = MapRepository(site)
    val community = CommunityRepository(site)
    val surveys = SurveyRepository(site)
    val school = SchoolRepository(site)
}
