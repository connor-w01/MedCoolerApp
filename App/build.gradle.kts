// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Configure Kotlin compiler options for all modules
subprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin> {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
            jvmToolchain(8)
        }
    }
}

// Standard Android configuration
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}