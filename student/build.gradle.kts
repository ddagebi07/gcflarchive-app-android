import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * 학생용 앱 UI, written once in Compose Multiplatform:
 *  - iPhone / iPad: framework "StudentKit" used by iosApp (Xcode)
 *  - Android: library that the app module can host
 *  - desktop: quick preview window (./gradlew :student:run)
 */
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "StudentKit"
            isStatic = true
            export(project(":core"))
        }
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation("org.osmdroid:osmdroid-android:6.1.20")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

android {
    namespace = "kr.co.gcflarchive.student"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "kr.co.gcflarchive.student.res"
}

compose.desktop {
    application {
        mainClass = "kr.co.gcflarchive.student.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "GCFL Archive"
        }
    }
}
