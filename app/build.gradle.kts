import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val envFile = rootProject.file(".env").takeIf { it.exists() }?.readLines().orEmpty()
    .filter { it.contains('=') && !it.trimStart().startsWith("#") }
    .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

val envNames = mapOf(
    "azure.endpoint" to "AZURE_OPENAI_ENDPOINT",
    "azure.deployment" to "AZURE_OPENAI_DEPLOYMENT",
    "azure.apiVersion" to "AZURE_OPENAI_API_VERSION",
    "azure.apiKey" to "AZURE_OPENAI_API_KEY",
    "sarvam.apiKey" to "SARVAM_API_KEY",
)

/** Prefers local.properties, then the git-ignored .env. */
fun localProp(key: String): String {
    val value = localProps.getProperty(key).orEmpty().ifBlank { envFile[envNames[key]].orEmpty() }
    return "\"$value\""
}

/** Release signing details, from the git-ignored keystore.properties. Missing means an unsigned release build. */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.ishaara.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ishaara.mobile"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "AZURE_ENDPOINT", localProp("azure.endpoint"))
            buildConfigField("String", "AZURE_DEPLOYMENT", localProp("azure.deployment"))
            buildConfigField("String", "AZURE_API_VERSION", localProp("azure.apiVersion"))
            buildConfigField("String", "AZURE_API_KEY", localProp("azure.apiKey"))
            buildConfigField("String", "SARVAM_API_KEY", localProp("sarvam.apiKey"))
        }
        release {
            // A release build carries no keys: whoever installs it enters their own in Settings.
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("String", "AZURE_ENDPOINT", "\"\"")
            buildConfigField("String", "AZURE_DEPLOYMENT", "\"\"")
            buildConfigField("String", "AZURE_API_VERSION", "\"\"")
            buildConfigField("String", "AZURE_API_KEY", "\"\"")
            buildConfigField("String", "SARVAM_API_KEY", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
