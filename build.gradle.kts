plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}

// Optional: keep build output outside the project folder (e.g. when it lives in a synced folder
// like OneDrive). Set HOMEPANEL_BUILD_DIR in ~/.gradle/gradle.properties; unset = the usual build/.
providers.gradleProperty("HOMEPANEL_BUILD_DIR").orNull?.let { dir ->
    allprojects { layout.buildDirectory.set(file("$dir/${project.name}")) }
}
