import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Properties

plugins {
    id("com.android.application")
}

// La chiave di firma sta FUORI dal progetto (TapiTranscriber Segreti).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val signingProps = Properties().apply {
    localProps.getProperty("tapitranscriber.signingProperties")?.let { path ->
        val f = file(path)
        if (f.exists()) f.inputStream().use { load(it) }
    }
}
val hasSigning = !signingProps.isEmpty

// versionCode = minuti dal 1/1/2026: ogni build è più nuova della precedente.
val buildMinutes: Int = Duration.between(
    ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
    ZonedDateTime.now(ZoneOffset.UTC),
).toMinutes().toInt()

android {
    namespace = "app.tapitranscriber"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tapitranscriber"
        minSdk = 26
        targetSdk = 36
        versionCode = buildMinutes
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasSigning) {
            create("tapi") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigning) signingConfig = signingConfigs.getByName("tapi")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (hasSigning) signingConfigs.getByName("tapi") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }
}
