plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "hr.divanguz.homepanel"
    compileSdk = 36

    defaultConfig {
        applicationId = "hr.divanguz.homepanel"
        minSdk = 31
        targetSdk = 36
        // Every commit is a new version, so the in-app updater can tell a newer GitHub release apart.
        // "1.N" with code 100 + N (series 1 started after the history was squashed; 0.x went to 0.11).
        val commits = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
            .standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)
        versionCode = 100 + commits
        versionName = "1.$commits"

        // MapLibre ships native code; arm64 only keeps the APK small enough to send to the phone.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // The signing key lives outside the project (path and passwords in ~/.gradle/gradle.properties), so
    // every build installs over the previous one even after Android Studio's own debug key changes.
    // Without those properties the default debug key is used.
    val keystore = providers.gradleProperty("HOMEPANEL_KEYSTORE").orNull
    signingConfigs {
        if (keystore != null) {
            create("homepanel") {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("HOMEPANEL_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("HOMEPANEL_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("HOMEPANEL_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        // Debug is shrunk too so the APK stays small enough to send to the phone (Remote Control caps
        // files at 30 MB; unshrunk Compose debug builds are ~40 MB). Still debuggable and debug-signed.
        debug {
            if (keystore != null) signingConfig = signingConfigs.getByName("homepanel")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("homepanel")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // android.util.Log and friends return defaults in JVM unit tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // A real XmlPullParser on the JVM (Android's lives only on the device).
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.work:work-runtime:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.maplibre.gl:android-sdk:13.7.1")

    // Settings screen only; the widget itself is Glance.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
}
