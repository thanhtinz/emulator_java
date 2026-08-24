pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MobiCore"

include(":app")
include(":core")

// The emulator core is shared verbatim with the iOS build, so it stays where
// it is at the repository root instead of being copied into the Android tree.
project(":core").projectDir = file("core")
