import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.bento.calendar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bento.calendar"
        minSdk = 27
        targetSdk = 35
        // CI overrides these from the release tag (VERSION_NAME / VERSION_CODE
        // env) so the built APK's version always matches the tag it ships under
        // — a mismatch would make the app see itself as perpetually outdated.
        versionCode = (System.getenv("VERSION_CODE") ?: "20600").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "2.6.0"
    }

    signingConfigs {
        create("release") {
            // Local builds: untracked keystore.properties next to the repo root.
            // CI: KEYSTORE_* environment variables fed from GitHub secrets.
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply { propsFile.inputStream().use { load(it) } }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else {
                val ksPath = System.getenv("KEYSTORE_PATH")
                if (ksPath != null) {
                    storeFile = File(ksPath)
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            // Deliberately unshrunk: a small personal app where install size is
            // irrelevant but R8-stripped reflection/serialization paths are a
            // stability risk we don't need to take.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Two distribution channels: "github" self-updates from GitHub releases;
    // "play" ships without the self-updater (Play policy forbids apps
    // installing APKs) and updates through the store instead.
    flavorDimensions += "dist"
    productFlavors {
        create("github") {
            dimension = "dist"
            buildConfigField("boolean", "SELF_UPDATER", "true")
        }
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "SELF_UPDATER", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.text.ExperimentalTextApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.biometric)
    // biometric 1.1.0 transitively pins fragment 1.2.x, which predates the
    // ActivityResult APIs MainActivity uses — force a current fragment.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
