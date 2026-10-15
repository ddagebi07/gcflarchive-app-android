import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// FCM needs the Firebase config file; without it the app builds as before and checks the
// 학교생활 inbox every 15 minutes instead (PUSH_SETUP.md on the server repo).
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

val gcflVersion = providers.gradleProperty("gcflVersion").get()
val gcflVersionCode = gcflVersion.split(".").map(String::toInt).let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "kr.co.gcflarchive.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.co.gcflarchive.app"
        minSdk = 26
        targetSdk = 35
        // gradle.properties의 gcflVersion 하나만 바꾸면 돼요.
        versionName = gcflVersion
        versionCode = gcflVersionCode
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("KEYSTORE_FILE", "gcfl-release-key.jks"))
                storePassword = keystoreProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // KRDS tokens, font and component styles shared with the admin app.
    sourceSets["main"].res.srcDirs("src/main/res", "../shared/res")

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    // 등급컷 익명 접근 코드는 지문 인증 후에만 보여 줍니다.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Native rendering of rich HTML bodies (school posts, 공지사항) instead of a WebView.
    implementation("org.jsoup:jsoup:1.18.3")
    // Map tiles for 지도 (OpenStreetMap; no API key needed).
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 학교생활 (시간표·연락·질문): the shared Compose screens also used on iPhone/iPad.
    implementation(project(":student"))
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is a stub in local unit tests; use the real implementation.
    testImplementation("org.json:json:20240303")
}
