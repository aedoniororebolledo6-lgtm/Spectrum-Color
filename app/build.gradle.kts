plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
// Minutos desde 1970: siempre crece, así cada compilación se instala encima de la anterior
// aunque cambie el repositorio o se reinicie el contador de GitHub Actions.
val buildMinutes = (System.currentTimeMillis() / 60000L).toInt()

android {
    namespace = "com.nioro.spectrumcolor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nioro.spectrumcolor"
        minSdk = 26
        targetSdk = 34
        versionCode = buildMinutes
        versionName = "2.0.$runNumber"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../signing/glassswitch.jks")
            storePassword = "glassswitch"
            keyAlias = "glassswitch"
            keyPassword = "glassswitch"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // "normal" = versión pública; "dev" = versión de desarrollador (con el botón para abrir todos los niveles).
    flavorDimensions += "modo"
    productFlavors {
        create("normal") {
            dimension = "modo"
            buildConfigField("boolean", "DEV", "false")
        }
        create("dev") {
            dimension = "modo"
            buildConfigField("boolean", "DEV", "true")
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
