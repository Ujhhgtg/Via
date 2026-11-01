plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "dev.ujhhgtg.via"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.ujhhgtg.via"
        minSdk = 29
        targetSdk = 37
        versionCode = 20261101
        versionName = "7.3.3-r7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    var foundKeystore = false

    signingConfigs {
        val storeFilePath = System.getenv("ANDROID_KEYSTORE_FILE")
            ?: runCatching { project.property("ANDROID_KEYSTORE_FILE") }.getOrNull() as? String
        val storePasswordValue = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            ?: runCatching { project.property("ANDROID_KEYSTORE_PASSWORD") }.getOrNull() as? String
        val keyAliasValue = System.getenv("ANDROID_KEY_ALIAS")
            ?: runCatching { project.property("ANDROID_KEY_ALIAS") }.getOrNull() as? String
        val keyPasswordValue = System.getenv("ANDROID_KEY_PASSWORD")
            ?: runCatching { project.property("ANDROID_KEY_PASSWORD") }.getOrNull() as? String

        val keystoreFile = storeFilePath?.let(::file)
        if (keystoreFile?.isFile == true &&
            !storePasswordValue.isNullOrEmpty() &&
            !keyAliasValue.isNullOrEmpty() &&
            !keyPasswordValue.isNullOrEmpty()
        ) {
            create("release") {
                foundKeystore = true
                storeFile = keystoreFile
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    // compress gecko variant's native libs to make size not so horrifying, no performance impact anyways
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (foundKeystore) "release" else "debug")
            optimization.enable = true
        }
    }

    // The browser engine is chosen at build time. Both flavors share applicationId and signing,
    // so installing one APK over the other keeps all Via-owned data (history, bookmarks, settings).
    flavorDimensions += "engine"
    productFlavors {
        create("webview") {
            dimension = "engine"
            isDefault = true
        }
        create("gecko") {
            dimension = "engine"
            versionNameSuffix = "-gecko"
            // noinspection ChromeOsAbiSupport
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":engine-api"))
    "webviewImplementation"(project(":engine-webview"))
    "geckoImplementation"(project(":engine-gecko"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.biometric)

    implementation(libs.okhttp)
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(libs.tinypinyin)

    implementation(libs.aboutlibraries.core)

    testImplementation(libs.junit)
}

aboutLibraries {
    collect {
        // Custom definitions for bundled web assets that are not Gradle dependencies
        configPath = file("config")
    }
}
