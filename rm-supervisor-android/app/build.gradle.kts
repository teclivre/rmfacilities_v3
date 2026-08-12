import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionPropsFile = file("version.properties")
if (!versionPropsFile.exists()) {
    versionPropsFile.writeText(
        """
        VERSION_CODE=1
        VERSION_MAJOR=1
        VERSION_MINOR=0
        VERSION_PATCH=0
        """.trimIndent()
    )
}

val versionProps = Properties().apply {
    FileInputStream(versionPropsFile).use { load(it) }
}

var verCode = (versionProps.getProperty("VERSION_CODE") ?: "1").toInt()
var verMajor = (versionProps.getProperty("VERSION_MAJOR") ?: "1").toInt()
var verMinor = (versionProps.getProperty("VERSION_MINOR") ?: "0").toInt()
var verPatch = (versionProps.getProperty("VERSION_PATCH") ?: "0").toInt()

val requestedTasks = gradle.startParameter.taskNames.joinToString(" ")
val isBuildTask = Regex("(?s).*(assemble|bundle).*").matches(requestedTasks)
if (isBuildTask) {
    verCode += 1
    verPatch += 1
    if (verPatch >= 100) {
        verPatch = 0
        verMinor += 1
    }
    if (verMinor >= 100) {
        verMinor = 0
        verMajor += 1
    }

    versionProps["VERSION_CODE"] = verCode.toString()
    versionProps["VERSION_MAJOR"] = verMajor.toString()
    versionProps["VERSION_MINOR"] = verMinor.toString()
    versionProps["VERSION_PATCH"] = verPatch.toString()
    versionPropsFile.outputStream().use { versionProps.store(it, null) }
    println("▶ Version bumped to $verMajor.$verMinor.$verPatch (code $verCode)")
}

val buildVersionName = "$verMajor.$verMinor.$verPatch"

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}
val hasReleaseSigning =
    keystorePropertiesFile.exists() &&
        keystoreProperties["storeFile"] != null &&
        keystoreProperties["storePassword"] != null &&
        keystoreProperties["keyAlias"] != null &&
        keystoreProperties["keyPassword"] != null

android {
    namespace = "com.rmfacilities.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rmfacilities.app"
        minSdk = 26
        targetSdk = 35
        versionCode = verCode
        versionName = buildVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "API_BASE_URL", "\"https://api.exemplo.rmfacilities.com\"")
        buildConfigField("boolean", "USE_MOCK_DATA", "true")
    }

    signingConfigs {
        if (hasReleaseSigning) {
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
