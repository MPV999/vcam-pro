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

rootProject.name = "VCAM Pro"

// Application
include(":app")

// Core
include(":core:common")
include(":core:database")
include(":core:storage")
include(":core:security")
include(":core:media")
include(":core:camera")

// Features
include(":features:dashboard")
include(":features:camera")
include(":features:media_library")
include(":features:virtual_camera")
include(":features:playback")
include(":features:settings")
include(":features:device_center")

// Virtual Camera SDK
include(":virtual-camera-sdk:api")
include(":virtual-camera-sdk:format")
include(":virtual-camera-sdk:frames")
include(":virtual-camera-sdk:playback")
include(":virtual-camera-sdk:source")

// Integration sample (separate APK, consumes the SDK)
include(":integration-sample")
