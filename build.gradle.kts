// Top-level build file where configuration options common to all sub-projects/modules are defined.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}

// Forward Android CLI `generate*Apks` tasks from the root container project (':') to ':app'
tasks.matching { it.name.startsWith("generate") && it.name.endsWith("Apks") }.configureEach {
    dependsOn(":app:$name")
}

gradle.taskGraph.whenReady {
    rootProject.tasks
        .matching { it.name.startsWith("generate") && it.name.endsWith("Apks") }
        .forEach { task ->
            task.actions.clear()
        }
}
