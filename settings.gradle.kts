pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GCFLArchive"
include(":app")
include(":admin")
// Shared with iPhone / iPad (Kotlin Multiplatform): see README "iPhone · iPad".
include(":core")
include(":student")
