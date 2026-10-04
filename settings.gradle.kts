rootProject.name = "jormungandr"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}

// Submodules declaration
include(
    ":modules:extension-core",
    ":modules:platform-shell",
    ":modules:jupyter-integration",
    ":modules:database-suite",
    ":modules:dataframe-viewer"
)
