plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vcam.sdk.playback"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":virtual-camera-sdk:api"))
    api(project(":virtual-camera-sdk:format"))
    api(project(":virtual-camera-sdk:frames"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
