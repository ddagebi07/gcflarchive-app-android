# OkHttp ships its own consumer rules; nothing app-specific needs keeping.

# jsoup can optionally use re2j for regex; it is not bundled.
-dontwarn com.google.re2j.**

# osmdroid has optional references that are not on the classpath.
-dontwarn org.osmdroid.**

# 학교생활: shared Kotlin Multiplatform code (ktor, kotlinx.serialization) used by SchoolActivity.
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class kr.co.gcflarchive.core.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
