import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.newrelic)
}

// OpenRouter API key from local.properties (gitignored) for local builds, or a CI secret env
// var — used as the default when the user hasn't entered their own key in Settings. Empty if
// absent from all three sources.
val openRouterApiKey: String = run {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    props.getProperty("OPENROUTER_API_KEY")
        ?: (project.findProperty("OPENROUTER_API_KEY") as? String)
        ?: System.getenv("OPENROUTER_API_KEY")
        ?: ""
}

// New Relic mobile app token from local.properties (gitignored) or a CI secret env var —
// no-op when absent from both.
val newRelicAppToken: String = run {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    props.getProperty("NEW_RELIC_APP_TOKEN")
        ?: (project.findProperty("NEW_RELIC_APP_TOKEN") as? String)
        ?: System.getenv("NEW_RELIC_APP_TOKEN")
        ?: ""
}

// Release signing credentials from local.properties (gitignored) for local builds, or from
// environment variables (CI secrets) when local.properties doesn't have them. Signing is
// skipped (release build stays unsigned) if neither source has a keystore configured.
val releaseKeystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun releaseSigningProp(key: String): String? =
    releaseKeystoreProps.getProperty(key) ?: System.getenv(key)
val releaseKeystorePath: String? = releaseSigningProp("RELEASE_KEYSTORE_PATH")

// CI (see .github/workflows/deploy-play.yml) passes a strictly increasing build number here so
// every Play Store upload has a unique versionCode. Local builds default to 1.
val releaseVersionCode: Int = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 1

android {
    namespace = "com.spendlens.app"
    compileSdk = 34

    if (releaseKeystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath)
                storePassword = releaseSigningProp("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningProp("RELEASE_KEYSTORE_ALIAS")
                keyPassword = releaseSigningProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.spendlens.app"
        minSdk = 26
        targetSdk = 34
        versionCode = releaseVersionCode
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterApiKey\"")
        buildConfigField("String", "NEW_RELIC_APP_TOKEN", "\"$newRelicAppToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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

// Token file consumed by the New Relic Gradle plugin for release ProGuard map upload.
// Generated from local.properties / -PNEW_RELIC_APP_TOKEN — never commit the file.
if (newRelicAppToken.isNotBlank()) {
    file("newrelic.properties").writeText(
        "com.newrelic.application_token=$newRelicAppToken\n",
    )
}

dependencies {
    implementation(libs.newrelic.android.agent)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Encrypted on-device storage
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.security.crypto)

    // Background SMS processing
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Runtime permissions in Compose
    implementation(libs.accompanist.permissions)

    // Biometric / device-credential app lock
    implementation(libs.androidx.biometric)
    // Override biometric's transitive fragment 1.2.5 (16-bit request-code crash)
    implementation(libs.androidx.fragment)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
}
