# OkHttp ships its own consumer rules; nothing app-specific needs keeping.

# jsoup can optionally use re2j for regex; it is not bundled.
-dontwarn com.google.re2j.**

# osmdroid has optional references that are not on the classpath.
-dontwarn org.osmdroid.**
