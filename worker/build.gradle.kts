plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.ocrstudio.worker"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":engine:pdf"))
    implementation(project(":engine:image"))
    implementation(project(":engine:ocr"))
    implementation(project(":engine:parser"))
    implementation(project(":engine:correction"))
    implementation(project(":engine:export"))

    implementation(libs.core.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    // Encrypts the user's own online-correction API key at rest; everything else this app
    // stores (settings, job config) is non-sensitive and stays in plain DataStore.
    implementation(libs.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
