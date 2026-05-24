plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

fun readSecretValue(vararg keys: String): String {
    val environmentValue = keys.firstNotNullOfOrNull { key ->
        System.getenv(key)?.takeIf { it.isNotBlank() }
    }
    if (environmentValue != null) return environmentValue

    val files = listOf("local.properties", ".env", ",env")
    val values = files
        .map(rootProject::file)
        .filter { it.exists() }
        .flatMap { it.readLines() }
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                null
            } else {
                val key = trimmed.substringBefore("=").trim()
                val value = trimmed.substringAfter("=").trim().trim('"', '\'')
                key to value
            }
        }
        .toMap()
    return keys.firstNotNullOfOrNull { key -> values[key]?.takeIf { it.isNotBlank() } }.orEmpty()
}

android {
    namespace = "labs.dx.readr"
    compileSdk = 36

    defaultConfig {
        applicationId = "labs.dx.readr"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${readSecretValue("OPENROUTER_API_KEY", "OPEN_ROUTER_API_KEY").replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
    implementation(project(":pdf"))
    implementation(project(":tts"))
    implementation(project(":playback"))
    implementation(project(":storage"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.util)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
