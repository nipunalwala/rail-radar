import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val releaseStoreFile: String? = localProperties.getProperty("RELEASE_STORE_FILE")
// -PPROXY_BASE_URL=... on the command line overrides local.properties.
val proxyBaseUrl: String? =
    (findProperty("PROXY_BASE_URL") as String?) ?: localProperties.getProperty("PROXY_BASE_URL")

// A release build without a proxy would have no way to reach train data.
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { it.project == project && it.name.contains("Release") }
    if (buildsRelease && (proxyBaseUrl == null || !proxyBaseUrl.startsWith("https://") || !proxyBaseUrl.endsWith("/"))) {
        throw GradleException(
            "Release builds need PROXY_BASE_URL in local.properties: an https URL ending in '/'. See server/proxy/README.md.",
        )
    }
}

android {
    namespace = "com.trainnearme"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trainnearme"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // Created only when local.properties names a keystore, so the project
        // still builds on a machine without one.
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug talks to RailRadar directly with the developer's own key.
        debug {
            buildConfigField("String", "TRAIN_API_BASE_URL", "\"https://api.railradar.in/\"")
            buildConfigField(
                "String",
                "RAILRADAR_API_KEY",
                "\"${localProperties.getProperty("RAILRADAR_API_KEY", "")}\"",
            )
        }
        // Release carries no key: it talks to the proxy in server/proxy, which holds it.
        release {
            buildConfigField("String", "TRAIN_API_BASE_URL", "\"${proxyBaseUrl.orEmpty()}\"")
            buildConfigField("String", "RAILRADAR_API_KEY", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.location)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.hilt.work.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
