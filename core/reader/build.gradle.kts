// T06: basic Reader pipeline (session + host view + entry kinds). Tables reuse
// TablePlugin NORMAL path; TableEntryPlugin never mixed in. View host uses
// androidx.recyclerview directly (catalog 1.2.1); MarkwonAdapter assembly lives
// in T08/T09 — T06 binds block Spanned per entry via MarkdownRenderer only.
// TODO(T06): cancellable loading pipeline, View/RecyclerView host, search hooks (T11).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
}

android {
    // Kotlin package stays io.leaflet.reader; the Android namespace must not collide with :app.
    namespace = "io.leaflet.reader.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
    implementation(project(":core:markdown"))
    implementation(project(":core:eink"))
    implementation(libs.markwon.core)
    implementation(libs.markwon.recycler)
    implementation(libs.recyclerview)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
