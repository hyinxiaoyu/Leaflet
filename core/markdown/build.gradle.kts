// T06: Markwon 4.6.2 chain (audit 2026-09-18, ADR-0004, research markdown-renderers §4).
// Single seam: only MarkdownRenderer touches io.noties.markwon.* (plus commonmark AST
// for block splitting — accepted transitive 0.13.0, never overridden). No image-glide/coil/
// picasso, no okhttp, no WebView. TablePlugin chosen; recycler-table depended for the T09
// TableEntry evolution path but its plugin is NEVER mixed in (TableEntryPlugin doc warning).
// Full implementation lands in T06. TODO(T06): Markwon-backed parser adapter,
// title/TOC extraction, render blocks, position map.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.leaflet.markdown"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        // Needed for src/androidTest (10MB large-file instrumented pass, T06).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(libs.markwon.core)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.tasklist)
    implementation(libs.markwon.recycler)
    implementation(libs.markwon.recycler.table)
    implementation(libs.recyclerview)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Instrumented-only: 10MB large-file pass (T06). Same pinned build pair as :core:storage.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
}
