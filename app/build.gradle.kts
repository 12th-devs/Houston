import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use(::load)
    }
}
val releaseStoreFile = providers.environmentVariable("COMMANDER_STORE_FILE").orNull
    ?: releaseKeystoreProperties.getProperty("storeFile")
val releaseStorePassword = providers.environmentVariable("COMMANDER_STORE_PASSWORD").orNull
    ?: releaseKeystoreProperties.getProperty("storePassword")
val releaseKeyAlias = providers.environmentVariable("COMMANDER_KEY_ALIAS").orNull
    ?: releaseKeystoreProperties.getProperty("keyAlias")
val releaseKeyPassword = providers.environmentVariable("COMMANDER_KEY_PASSWORD").orNull
    ?: releaseKeystoreProperties.getProperty("keyPassword")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.astroboii47.commander"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.astroboii47.commander"
        minSdk = 29
        targetSdk = 36
        versionCode = 10
        versionName = "0.2.0-alpha.1"
        // Keep only English strings from libraries; translations add bulk for little benefit in alpha.
        resourceConfigurations += "en"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 + resource shrinking is the main lever on top of the removed keyboard bundle.
            // Debug stays unminified for build speed.
            isMinifyEnabled = true
            isShrinkResources = true
            // LiteRT ships arm64 + x86_64 only; phones are ARM, so drop the 26 MB
            // x86_64 .so from release. Debug keeps all ABIs for the emulator.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    lint {
        // This reflection runs only inside the explicitly authorised Shizuku
        // shell user service used for system task switching and snapshots.
        disable += "BlockedPrivateApi"
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.markwon.core)
    implementation(libs.litertlm)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
