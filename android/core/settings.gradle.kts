// Lets the core module build and test on its own, without the Android SDK:
//   gradle -p android/core test
// Inside the full Android build, ../settings.gradle.kts is used instead.
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Maven Central, via Google's mirror first (no rate limiting from
        // shared cloud IPs), then its own hosts.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "core"
