plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    // Kotlin Multiplatform modules shared with iPhone / iPad (core, student).
    id("org.jetbrains.kotlin.multiplatform") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
    // 학교생활 푸시 (FCM): applied by :app only when app/google-services.json exists (PUSH_SETUP.md).
    id("com.google.gms.google-services") version "4.4.2" apply false
}
