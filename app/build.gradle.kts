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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nioro.spectrumcolor"
        minSdk = 26
        targetSdk = 36
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
        // Llave de subida a Google Play: vive solo en los "Secrets" de GitHub (nunca en el repositorio).
        // play.yml la escribe en un archivo temporal y pasa la ruta y la contraseña por variables de entorno.
        val upFile = System.getenv("UPLOAD_KEYSTORE")
        if (upFile != null && file(upFile).exists()) {
            create("upload") {
                storeFile = file(upFile)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD") ?: System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // "normal" = versión pública (APK de GitHub, se actualiza sola); "dev" = versión de desarrollador
    // (editor, atajos, abrir todos los niveles); "play" = la de Google Play (la actualiza la tienda: sin botón
    // de actualizar ni descargas fuera de Play).
    flavorDimensions += "modo"
    productFlavors {
        create("normal") {
            dimension = "modo"
            buildConfigField("boolean", "DEV", "false")
            buildConfigField("boolean", "STORE", "false")
            signingConfig = signingConfigs.getByName("release")
        }
        create("dev") {
            dimension = "modo"
            buildConfigField("boolean", "DEV", "true")
            buildConfigField("boolean", "STORE", "false")
            signingConfig = signingConfigs.getByName("release")
        }
        create("play") {
            dimension = "modo"
            buildConfigField("boolean", "DEV", "false")
            buildConfigField("boolean", "STORE", "true")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("release")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
