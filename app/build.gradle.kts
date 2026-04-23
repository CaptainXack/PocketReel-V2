plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val embeddedTmdbApiKey = "d084c7c91a11854990ade7a7e371a4ea"
val embeddedTmdbReadAccessToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJkMDg0YzdjOTFhMTE4NTQ5OTBhZGU3YTdlMzcxYTRlYSIsIm5iZiI6MTY0MTI1ODA1OC40NDYsInN1YiI6IjYxZDM5YzRhZTE5NGIwMDA4Y2YzYzc5YSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.bIZ33k72zZ_NPkhGcw5Z0cxHTNe7yMHRY-KZpv5a85Q"
val tmdbApiKey = providers.gradleProperty("TMDB_API_KEY").orElse(embeddedTmdbApiKey).get().replace("\"", "\\\"")
val tmdbReadAccessToken = providers.gradleProperty("TMDB_READ_ACCESS_TOKEN").orElse(embeddedTmdbReadAccessToken).get().replace("\"", "\\\"")

android {
    namespace = "com.captainxack.pocketreel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.captainxack.pocketreel"
        minSdk = 22
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"$tmdbReadAccessToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}