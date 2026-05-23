rootProject.name = "market-signal-engine"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":application")
include(":infrastructure:app")
include(":infrastructure:event-adapter")
