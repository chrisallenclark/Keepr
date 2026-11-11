plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Release signing, versioning and the test build type. See docs/RELEASE.md.
// ---------------------------------------------------------------------------

/** An environment variable, or null when it is unset or blank. */
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

// Release signing comes only from the environment (CI secrets, or a throwaway
// key the CI verification job makes for itself), never from files in the
// repository. Without it, release builds are produced unsigned. The release
// workflow passes -Pkeepr.requireReleaseSigning=true so a missing secret
// fails the build instead of quietly producing an unsigned APK.
val releaseKeystorePath: String? = env("KEEPR_KEYSTORE_PATH")
val requireReleaseSigning: Boolean = providers.gradleProperty("keepr.requireReleaseSigning").orNull == "true"
if (releaseKeystorePath != null) {
    val missing = listOf("KEEPR_KEYSTORE_PASSWORD", "KEEPR_KEY_ALIAS", "KEEPR_KEY_PASSWORD").filter { env(it) == null }
    if (missing.isNotEmpty()) {
        throw GradleException("KEEPR_KEYSTORE_PATH is set but ${missing.joinToString()} is not. Release signing needs all four.")
    }
    if (!file(releaseKeystorePath).isFile) {
        throw GradleException("KEEPR_KEYSTORE_PATH does not point to a file.")
    }
} else if (requireReleaseSigning) {
    throw GradleException("Release signing is required (keepr.requireReleaseSigning=true) but KEEPR_KEYSTORE_PATH is not set.")
}

// The version people see comes from one place: keepr.versionName in
// android/gradle.properties. The version code, which Android uses to decide
// whether an APK is an update, comes from CI (KEEPR_VERSION_CODE); local and
// debug builds are 1, below every release.
val keeprVersionName: String = providers.gradleProperty("keepr.versionName").orNull
    ?: throw GradleException("keepr.versionName is missing from android/gradle.properties.")
val keeprVersionCode: Int = env("KEEPR_VERSION_CODE")?.let { raw ->
    raw.trim().toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
        ?: throw GradleException("KEEPR_VERSION_CODE must be a whole number from 1 to 2100000000.")
} ?: 1

// Which build type the on-device tests run against. Debug everywhere except
// the CI release check, which passes -Pkeepr.testBuildType=release to
// instrument the minified, non-debuggable release build itself.
val keeprTestBuildType: String = providers.gradleProperty("keepr.testBuildType").orNull ?: "debug"
if (keeprTestBuildType != "debug" && keeprTestBuildType != "release") {
    throw GradleException("keepr.testBuildType must be debug or release.")
}

android {
    namespace = "com.chrisallenclark.keepr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chrisallenclark.keepr"
        // Android 8.0. Every library in use supports it.
        minSdk = 26
        targetSdk = 36
        versionCode = keeprVersionCode
        versionName = keeprVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testBuildType = keeprTestBuildType

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = env("KEEPR_KEYSTORE_PASSWORD")
                keyAlias = env("KEEPR_KEY_ALIAS")
                keyPassword = env("KEEPR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // What testers install: never debuggable, minified and shrunk by R8.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keeprTestBuildType == "release") {
                // CI release check only: keeps the classes the on-device test
                // calls directly. The production-configured APK is checked
                // separately, without this file. See docs/RELEASE.md.
                proguardFile("proguard-release-test.pro")
            }
            // The test APK is processed with the app's mapping; these rules
            // stop R8 from pruning the tests themselves.
            testProguardFiles("proguard-test-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Library update nags aren't build failures; real problems still are.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Keeps a JSON copy of each schema version, for writing migrations later.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    // Only for menus and sheets' gesture handling; every surface is restyled to iOS.
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // GrantPermissionRule, for ContactsTest.
    androidTestImplementation(libs.androidx.test.rules)
    // Stands in for the system file picker in the Settings backup test.
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
