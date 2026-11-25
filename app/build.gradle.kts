import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

// CI writes keystore.properties for a release (NineTailsFlip/.github's android.yml).
val keystoreProperties = Properties()
val keystorePropertiesFile = project.file("keystore.properties")
val hasKeystore = keystorePropertiesFile.exists()
if (hasKeystore) keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }

// Setup — the phone's first-time setup on NineTailsFlip's LineageOS build, in the Nine suite's
// look: language, Wi-Fi, date and time, a PIN, and the look of every Nine app. It replaces
// LineageSetupWizard in the image and is signed there with the platform key (it sets the
// system's locale, time, Wi-Fi and lock), so it's never installed or updated on its own.
android {
    namespace = "io.github.ninetailsflip.setup"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.ninetailsflip.setup"
        minSdk = 34
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
    }

    if (hasKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The image re-signs it with the platform key either way.
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            vcsInfo { include = false }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // Like every Nine app: English-only, screens built in code from string literals.
        disable += "SetTextI18n"
        disable += "OldTargetApi"
        // A setup wizard holds system permissions by design: the image grants them (platform key).
        disable += "ProtectedPermissions"
        warningsAsErrors = true
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":update"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
