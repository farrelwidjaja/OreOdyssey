import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.oreodyssey.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.oreodyssey.game"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Baca Gemini API Key dari local.properties secara aman
        val propFile = rootProject.file("local.properties")
        val geminiKey = if (propFile.exists()) {
            val props = Properties()
            props.load(propFile.inputStream())
            props.getProperty("GEMINI_API_KEY", "")
        } else ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Google Sign-In via Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Keamanan Kredensial
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // AI Companion SDK (Google Gemini)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Animasi & Visual Loading (Community Libraries)
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("com.daimajia.androidanimations:library:2.4@aar")
}
