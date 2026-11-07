plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tz.pesaflow.sms"
    compileSdk = 34

    defaultConfig {
        applicationId = "tz.pesaflow.sms"
        minSdk = 24
        targetSdk = 34
        // Codemagic inatoa BUILD_NUMBER; kila build mpya ina namba kubwa zaidi ili isakinishwe juu ya ya zamani
        versionCode = 1 + (System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "1.1.${System.getenv("BUILD_NUMBER") ?: "0"}"
    }

    // Ufunguo wa kudumu wa kutia sahihi: unatoka kwenye siri za Codemagic (PF_KEYSTORE_PATH).
    // Bila ufunguo huu, kila build ingetiwa sahihi tofauti na simu ingekataa kusasisha app.
    val keystorePath = System.getenv("PF_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("pesaflow") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PF_KEYSTORE_PASSWORD")
                keyAlias = "pesaflow"
                keyPassword = System.getenv("PF_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("pesaflow")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
