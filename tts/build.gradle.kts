plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
    namespace = "labs.dx.tts"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField(
            "String",
            "RUMIK_API_KEY",
            "\"${readSecretValue("RUMIK_API_KEY", "SILK_API_KEY").replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    ksp(libs.hilt.compiler)
}
