# GCFL 아카이브 Android 앱

`gcflarchive.co.kr` 서비스를 감싸는 Kotlin 네이티브 앱입니다. 아카이브 페이지는 WebView로 보여 주고,
자주 쓰는 기능(급식, 극플드라이브 업로드, 알림)은 네이티브로 구현했습니다.

## 주요 기능

| 기능 | 구현 |
|---|---|
| 기출/PDF/사진/영상 아카이브 | 웹 페이지(`past-exams` / `documents` / `photo` / `video`)를 KRDS 스타일 그대로 **네이티브 화면**으로 다시 만들었습니다 (`library/`). 같은 API를 쓰고, 웹과 같은 필터·정렬 규칙(`LibraryModels.kt`)으로 기기에서 걸러 줍니다. **기출**: 연도·학년·학과·시험 구분·과목 복수 선택 필터, ★즐겨찾기, 정답지, 등급컷 표, 극플드라이브에 담기, 정답지 포함 여부 선택. **PDF 자료실**: 분류 탭(건수 표시), 5가지 정렬. **사진**: 앨범 그리드(로그인 필요), 앨범 열기. **영상**: 썸네일 카드, 유튜브 앱으로 재생. PDF는 서버의 워터마크 페이지 이미지 API(`/api/pdf-viewer`)를 쓰는 앱 내 뷰어로 열립니다(핀치 줌·더블탭). 런처 아이콘을 길게 누르면 `기출 아카이브`, `오늘 급식`, `극플드라이브` 바로가기도 나옵니다. |
| 통합검색 (학교 홈페이지 자료) | 하단 **통합검색** 탭. `/api/archive/search`를 직접 호출해 검색어·게시판·첨부파일 여부·관련도/최신순으로 검색하고, 스크롤하면 다음 페이지를 불러옵니다. 게시물을 누르면 본문, 첨부파일(바로 다운로드), 이전/다음 글, 원문 링크가 나옵니다. 로그인 없이 쓸 수 있습니다. |
| 극플드라이브 업로드 | 다른 앱의 **공유** 버튼 → "극플드라이브에 올리기" 선택 → `ShareReceiverActivity`가 `/api/share/upload`로 바로 올리고 `/s/<코드>` 링크를 보여 줍니다. 앱 안에서는 홈 카드나 드라이브 탭의 "올리기" 버튼으로 파일을 고를 수 있습니다. |
| 급식 조회 | 급식 탭에서 NEIS 오픈 API를 직접 호출합니다 (`meal.html`과 같은 키·학교 코드). 날짜 이동, 달력 선택, 당겨서 새로고침. |
| 급식 푸시 알림 | `WorkManager`가 매일 설정한 시간(기본 07:30)에 오늘 메뉴를 확인해 로컬 알림을 띄웁니다. 급식이 없는 날(주말·방학)은 알리지 않습니다. 설정 탭에서 시간, 조식/중식/석식 선택, 테스트 알림. |

## 로그인 방식

서버는 Flask 세션 쿠키(`gcfl_session`)로 인증합니다. 앱은 WebView에서 `/verify`로 로그인하고
(앱에서는 "로그인 유지"가 기본으로 켜짐), 네이티브 업로드는 `CookieManager`에 저장된 같은 쿠키를 그대로 씁니다.
그래서 **서버 수정 없이** 동작합니다. 로그인이 안 된 상태로 공유하면 로그인 화면을 띄운 뒤 업로드를 이어서 진행합니다.

업로드 규칙은 서버(`routes/temp_share.py`)와 같습니다: PDF/HWP/HWPX/DOC(X)/PPT(X)/XLS(X)/TXT/ZIP, 합계 10MB,
5개를 넘으면 ZIP으로 묶임, 7일 보관. 허용 확장자를 바꾸면 `Config.DRIVE_ALLOWED_EXTENSIONS`도 같이 바꿔 주세요.

WebView 요청의 User-Agent 끝에는 `GCFLArchiveApp/<버전>`이 붙습니다. 서버에서 앱 트래픽을 구분할 때 쓰면 됩니다.

## 빌드

- Android Studio에서 이 저장소 폴더를 열고 실행하거나,
- 터미널에서 `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- GitHub Actions(`.github/workflows/android.yml`)가 푸시마다 테스트·빌드·lint를 돌리고 디버그 APK를 아티팩트로 올립니다.

요구 사항: JDK 17+, Android SDK 35. minSdk 26 (Android 8.0).

Play 스토어 배포용 릴리스 빌드는 서명 키(`keystore`)를 만든 뒤 `app/build.gradle.kts`에 `signingConfigs`를 추가해야 합니다.
키 파일은 저장소에 커밋하지 마세요 (`.gitignore`에 포함됨).

## 구조

```
app/src/main/java/kr/co/gcflarchive/app/
  Config.kt               서버 주소, NEIS 키, 업로드 규칙, 홈 바로가기 목록
  MainActivity.kt         하단 탭 (홈 / 통합검색 / 급식 / 극플드라이브 / 설정)
  ui/                     각 탭 Fragment
  web/                    WebView 공통 설정(쿠키, 파일 선택, 다운로드, 외부 링크), 웹 화면 Activity
  share/                  공유 시트 수신 화면, 업로드 클라이언트
  archive/                통합검색 API 클라이언트, 검색 결과 목록, 게시물 상세 화면
  library/                기출·PDF·사진·영상 네이티브 화면, KRDS 공통 틀(LibraryListActivity, DetailSheet), PDF 뷰어
  meal/                   NEIS 조회·파싱, 알림 예약(WorkManager)
  data/                   SharedPreferences, OkHttp 클라이언트
```

## 다음 단계로 고려할 것

- **서버 발송 푸시 (FCM)**: 지금 알림은 기기에서 예약하는 로컬 알림이라 서버가 필요 없습니다. 공지 등 관리자가 보내는 푸시가
  필요해지면 Firebase 프로젝트 + 서버에 토큰 등록 API를 추가해야 합니다.
- 사진(JPG/PNG) 업로드: 현재 서버가 문서 파일만 받습니다. 서버의 허용 확장자를 늘리면 앱의 공유 필터(`AndroidManifest.xml`의 `image/*`)도 추가하세요.

## 디자인 (KRDS)

색상·모서리·배지·필터 칩·버튼은 웹의 `theme-tokens.css` / `theme-board.css` 값을 그대로 옮겼습니다
(`res/values/krds_colors.xml`, `values-night/krds_colors.xml`, `themes.xml`의 `Krds` 스타일).
웹과 같은 다크 모드 토큰도 들어 있습니다.

KRDS 서체(Pretendard GOV)는 아직 연결 전입니다. `res/font/`에 TTF를 넣고 테마의 `fontFamily`를 지정하면 됩니다.
