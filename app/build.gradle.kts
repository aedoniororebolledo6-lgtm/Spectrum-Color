plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.nioro.glassswitch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nioro.glassswitch"
        minSdk = 26
        targetSdk = 34
        // Cada compilación en GitHub sube el número, así se instala encima de la anterior.
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../signing/glassswitch.jks")
            storePassword = "glassswitch"
            keyAlias = "glassswitch"
            keyPassword = "glassswitch"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
