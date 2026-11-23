import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Chiave di firma: deve restare sempre la stessa, altrimenti Android non accetta gli aggiornamenti.
val keyProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Chiave di Google Maps: dal file maps.properties oppure dalla variabile MAPS_API_KEY di GitHub.
val mapsProps = Properties().apply {
    val f = file("maps.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsKey: String = (System.getenv("MAPS_API_KEY") ?: "").ifBlank { mapsProps.getProperty("MAPS_API_KEY", "") }

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "it.giacomo.tachimetro"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.giacomo.tachimetro"
        minSdk = 31
        targetSdk = 35
        versionCode = runNumber
        versionName = "0.1.$runNumber"
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("boolean", "HAS_MAPS_KEY", mapsKey.isNotBlank().toString())
        // Samsung e MagicPad 4 sono a 64 bit: niente librerie per processori vecchi, l'APK è molto più leggero
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            // prima i segreti di GitHub, poi il file keystore.properties
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: keyProps.getProperty("storeFile", "tachimetro.keystore"))
            storePassword = System.getenv("KEYSTORE_PASSWORD")?.ifBlank { null } ?: keyProps.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS")?.ifBlank { null } ?: keyProps.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD")?.ifBlank { null } ?: keyProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.navigation:navigation:7.6.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")

    // Riconoscimento della parola chiave sul telefono, senza internet
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
}
