plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val enableLiteRtLm: Boolean =
    (project.findProperty("ocrstudio.enableLiteRtLm") as String?)?.toBoolean() ?: true

android {
    namespace = "com.ocrstudio.engine.correction"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        buildConfigField("boolean", "LITERT_LM_AVAILABLE", enableLiteRtLm.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // LiteRT-LM's exact Maven coordinates are not guaranteed to resolve in
    // every build environment. The LiteRtCorrector implementation lives in
    // its own source set gated by this flag; NoOpCorrector (src/main) is
    // always compiled and is the default binding, so rule-based correction
    // never depends on this artifact resolving.
    sourceSets {
        getByName("main") {
            kotlin.srcDir(if (enableLiteRtLm) "src/liteRtEnabled/kotlin" else "src/liteRtDisabled/kotlin")
        }
    }
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.core.ktx)
    implementation(libs.coroutines.core)
    implementation("javax.inject:javax.inject:1")

    if (enableLiteRtLm) {
        // NOTE: com.google.ai.edge.litert:litert-lm coordinates per the spec.
        // If this artifact is unavailable in a given environment, set
        // ocrstudio.enableLiteRtLm=false in gradle.properties; NoOpCorrector
        // remains the guaranteed fallback in all cases.
        implementation("com.google.ai.edge.litert:litert-lm:1.0.0")
    }

    testImplementation(libs.junit)
}
