import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName = "0.10.2"

fun versionCodeFromName(versionName: String): Int {
    val parts = versionName.split(".")
    require(parts.size == 3 || parts.size == 4) {
        "versionName must use major.minor.patch or major.minor.patch.build format: $versionName"
    }

    val numbers = parts.mapIndexed { index, part ->
        require(part.isNotBlank()) {
            "versionName segment ${index + 1} is blank: $versionName"
        }
        part.toIntOrNull() ?: error(
            "versionName segment ${index + 1} must be an integer: $versionName"
        )
    }

    val major = numbers[0]
    val minor = numbers[1]
    val patch = numbers[2]
    val build = numbers.getOrElse(3) { 0 }

    require(major >= 0) {
        "versionName major must be >= 0: $versionName"
    }
    require(minor in 0..99) {
        "versionName minor must be in 0..99: $versionName"
    }
    require(patch in 0..99) {
        "versionName patch must be in 0..99: $versionName"
    }
    require(build in 0..99) {
        "versionName build must be in 0..99: $versionName"
    }

    return major * 1_000_000 + minor * 10_000 + patch * 100 + build
}

val localKeystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun localKeystoreProperty(name: String): String? {
    return localKeystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() }
}

fun releaseSigningValue(environmentName: String, localName: String): String? {
    return System.getenv(environmentName)?.takeIf { it.isNotBlank() }
        ?: localKeystoreProperty(localName)
}

val releaseKeystoreFile = releaseSigningValue(
    environmentName = "FLOWTONE_RELEASE_KEYSTORE_FILE",
    localName = "storeFile"
)
val releaseKeystorePassword = releaseSigningValue(
    environmentName = "FLOWTONE_RELEASE_KEYSTORE_PASSWORD",
    localName = "storePassword"
)
val releaseKeyAlias = releaseSigningValue(
    environmentName = "FLOWTONE_RELEASE_KEY_ALIAS",
    localName = "keyAlias"
)
val releaseKeyPassword = releaseSigningValue(
    environmentName = "FLOWTONE_RELEASE_KEY_PASSWORD",
    localName = "keyPassword"
)
val hasReleaseSigningConfig = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "ink.tenqui.flowtone"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ink.tenqui.flowtone"
        minSdk = 28
        targetSdk = 36
        versionCode = versionCodeFromName(appVersionName)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }

            optimization {
                enable = false
            }
        }

        create("benchmark") {
            initWith(getByName("release"))

            signingConfig = signingConfigs.getByName("debug")

            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false

            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.coil.compose)
    implementation(libs.google.material)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
