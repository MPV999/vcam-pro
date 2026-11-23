plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vcam.pro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vcam.pro"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // ARM64 only, as specified for the delivered APK.
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        // Release signing is supplied by CI through environment variables.
        // No key material is ever committed to this repository.
        create("ci") {
            val storePath = System.getenv("VCAM_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("VCAM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VCAM_KEY_ALIAS")
                keyPassword = System.getenv("VCAM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (System.getenv("VCAM_KEYSTORE_PATH").isNullOrBlank()) {
                // Unsigned release build (CI produces an unsigned APK plus SHA-256).
                signingConfig = null
            } else {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE*",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:storage"))
    implementation(project(":core:security"))
    implementation(project(":core:media"))
    implementation(project(":core:camera"))

    implementation(project(":features:dashboard"))
    implementation(project(":features:camera"))
    implementation(project(":features:media_library"))
    implementation(project(":features:virtual_camera"))
    implementation(project(":features:playback"))
    implementation(project(":features:settings"))
    implementation(project(":features:device_center"))

    implementation(project(":virtual-camera-sdk:source"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
