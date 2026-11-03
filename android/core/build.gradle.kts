import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Keepr's records and business logic, with no Android dependency: the Kotlin
// translation of the iOS app's Models/ and Domain/, tested on the plain JVM.
// Keeping it platform-free is also what lets it be shared later (sync, or a
// Kotlin Multiplatform build) without untangling it from the UI.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
