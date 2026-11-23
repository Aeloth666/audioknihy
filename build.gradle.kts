plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Projekt leží ve OneDrive: build výstupy držíme mimo synchronizovanou složku,
// jinak OneDrive zamyká soubory a build padá.
allprojects {
    layout.buildDirectory.set(
        file("${System.getenv("LOCALAPPDATA")}/AudioknihyBuild/${project.name}")
    )
}
