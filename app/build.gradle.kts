plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    // Temporary IDs, centrally configured (ADR-0003). Full = io.leaflet.reader,
    // eink = io.leaflet.reader.eink via applicationIdSuffix. NOT final release IDs.
    namespace = "io.leaflet.reader"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.leaflet.reader"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        // R2-07: production-page suite lives in `src/androidTest` (12 tests
        // per flavor); connected app tasks are real coverage, not NO-SOURCE.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
        }
        create("eink") {
            dimension = "distribution"
            applicationIdSuffix = ".eink"
            versionNameSuffix = "-eink"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Compose screen functions are uppercase by convention; ktlint's function-naming
// rule would flag every @Composable, so it is disabled via additionalEditorconfig
// (equivalent to .editorconfig key ktlint_standard_function-naming=disabled).
ktlint {
    additionalEditorconfig.set(mapOf("ktlint_standard_function-naming" to "disabled"))
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":core:markdown"))
    implementation(project(":core:reader"))
    implementation(project(":core:eink"))

    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    // T05: AppContainer builds the Room database + production tree lister.
    // Versions reuse the frozen catalog (room 2.8.5) via literal coordinates
    // so libs.versions.toml stays untouched (same pattern as :core:storage).
    implementation("androidx.room:room-runtime:${libs.versions.room.get()}")
    implementation("androidx.room:room-ktx:${libs.versions.room.get()}")
    // R2-03: EntryBindings mapping is pure Kotlin (+ compose-ui text types,
    // JVM-runnable) — asserted as local unit tests, no device needed.
    // R2-07 page scenarios compose the production ReaderScreen with
    // createComposeRule (content-only): createAndroidComposeRule is
    // infra-blocked on this emulator image (TEST-process host activity hits
    // startActivitySync cross-process failure — verified 2026-09-18), so no
    // host activity is declared and none is launched; the composed tree is
    // still the production screen + session/parser path (see PageHarness).
    testImplementation(libs.junit)
    testImplementation(libs.compose.ui)
    testImplementation(libs.coroutines.test)
    // R2-07 page-test deps (both flavors): compose UI-test + runner pinned
    // in the catalog (1.7.5/1.6.2/1.1.5 — same pair as
    // :core:storage/:core:markdown instrumented suites).
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
