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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "OCR Studio"

include(":app")
include(":core:common")
include(":core:database")
include(":core:ui")
include(":engine:pdf")
include(":engine:image")
include(":engine:ocr")
include(":engine:parser")
include(":engine:correction")
include(":engine:export")
include(":worker")
