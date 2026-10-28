plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.galaxymedia.tvsetup"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.galaxymedia.tvsetup"
        minSdk = 25          // Fire OS 6+ (Fire TV Stick 4K 1st gen and newer)
        targetSdk = 34
        versionCode = 16
        versionName = "0.4.0"
    }

    // One fixed signing key, so every build installs over the last one without an uninstall.
    signingConfigs {
        getByName("debug") {
            storeFile = file("galaxy-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
            "META-INF/versions/**", "META-INF/*.kotlin_module"
        )
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
