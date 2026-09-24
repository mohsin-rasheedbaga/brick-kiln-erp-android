// Top-level build file
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Compose Compiler plugin is shipped inside kotlin-android plugin since Kotlin 2.0
    // For Kotlin 1.9.24 we use the older "compose-compiler" plugin OR
    // skip the plugin entirely and configure via composeOptions in app/build.gradle.kts.
    // Here we use the latter approach (no separate plugin needed).
}
