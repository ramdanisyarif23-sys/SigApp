plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" // <-- Versi mutlak untuk Kotlin 2.2.10
}

android {
    namespace = "com.example.sigapp"
    compileSdk = 35 // Gunakan standar angka, bukan fungsi release() agar tidak error

    defaultConfig {
        applicationId = "com.example.sigapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Penulisan DSL yang benar
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-nearby:19.0.0")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Mesin Database Lokal (Room)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1") // KSP menggantikan KAPT

    // Sensor Lokasi GPS (Google Fused Location)
    implementation("com.google.android.gms:play-services-location:21.2.0")
}