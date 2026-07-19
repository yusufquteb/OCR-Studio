plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val enablePaddleOcr: Boolean =
    (project.findProperty("ocrstudio.enablePaddleOcr") as String?)?.toBoolean() ?: true

android {
    namespace = "com.ocrstudio.engine.ocr"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        buildConfigField("boolean", "PADDLE_OCR_AVAILABLE", enablePaddleOcr.toString())
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

    // The Paddle/ONNX implementation is isolated in its own source set so that
    // disabling ocrstudio.enablePaddleOcr (e.g. if the onnxruntime-android
    // artifact cannot be resolved) removes it from compilation entirely.
    // Tesseract (src/main) remains the guaranteed OCR path regardless.
    sourceSets {
        getByName("main") {
            kotlin.srcDir(if (enablePaddleOcr) "src/paddleEnabled/kotlin" else "src/paddleDisabled/kotlin")
        }
    }
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.core.ktx)
    implementation(libs.tesseract4android)
    implementation(libs.coroutines.core)
    implementation("javax.inject:javax.inject:1")

    if (enablePaddleOcr) {
        implementation(libs.onnxruntime.android)
        // Used only by the Paddle detection post-processing (contour finding on the DB
        // probability map, perspective crop for recognition). Gated behind the same flag
        // as the rest of the Paddle pipeline.
        implementation(libs.opencv)
    }

    testImplementation(libs.junit)
}
