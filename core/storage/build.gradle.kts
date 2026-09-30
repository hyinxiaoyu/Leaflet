// T04: SAF + Room + DataStore foundation. Room artifacts pin to the frozen
// catalog versions (room 2.8.5); KSP plugin + coroutines-test likewise reuse
// catalog versions via literal coordinates so libs.versions.toml stays untouched.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.leaflet.storage"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        // Needed for src/androidTest (DAO + SAF instrumented tests, T04).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    // The 1->2 migration test opens the previously-exported schema (1.json)
    // through MigrationTestHelper, which reads it from the androidTest assets.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Room schemas are checked in under core/storage/schemas/<db-fqn>/ for review
// and as the baseline for tested migrations (see LeafletDatabase KDoc): 1.json
// is the shipped v1 baseline, 2.json the current v2 schema. Both stay checked
// in — the migration test needs 1.json to open a v1 database and 2.json to
// validate the migrated result.
// New columns MUST declare Kotlin defaults so auto-migrations stay possible.
//
// Toolchain quirk (verified 2026-09-18, reproduced for v2 on 2026-09-28):
// setting `room.schemaLocation` makes Room's compiler take the migration-bundle
// path (SchemaBundle.deserialize) during KSP, where `FieldBundle$$serializer`
// fails against the kotlinx-serialization on the KSP worker classpath:
// `AbstractMethodError ... typeParametersSerializers()`. That is why the
// argument is NOT applied to normal builds. Schema export is opt-in:
//
//     ./gradlew --stop && rm -rf core/storage/build
//     ./gradlew :core:storage:kspDebugKotlin -Proom.schemaExport=true
//
// then review and check in the regenerated `core/storage/schemas/**/N.json`.
// If the export run hits the AbstractMethodError, stop Gradle and clear the
// module build/ dir and retry (`--no-daemon` alone is not enough; the
// KSP1-vs-KSP2 flag was tested and is unrelated). Incremental builds that reuse
// a warm KSP invocation are unaffected.
ksp {
    if (providers.gradleProperty("room.schemaExport").isPresent) {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
    implementation("androidx.room:room-runtime:${libs.versions.room.get()}")
    implementation("androidx.room:room-ktx:${libs.versions.room.get()}")
    ksp("androidx.room:room-compiler:${libs.versions.room.get()}")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")

    // Instrumented-only: real in-memory Room + real ContentResolver behavior.
    // Versions are the pinned build pair (see THIRD_PARTY_LICENSES); kept as
    // literals so the global version catalog stays untouched.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
}
