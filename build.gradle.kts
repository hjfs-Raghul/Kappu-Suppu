// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied to :app (AGP 9 has built-in Kotlin); declared here so the Kotlin Gradle plugin on
    // the build classpath has the same version as the Compose compiler plugin.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
