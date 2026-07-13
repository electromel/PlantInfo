import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// --- Lecture des clés API par défaut pour le développement (§3.2) ---
// Le fichier dev-keys.properties se trouve à la racine du projet, à côté de local.properties,
// et n'est jamais versionné (voir .gitignore). Chaque clé présente est injectée dans BuildConfig
// UNIQUEMENT pour le build `debug` (voir buildTypes ci-dessous) : le build `release` publié ne
// contient JAMAIS ces clés. L'application ne s'en sert que pour PRÉ-REMPLIR une clé absente du
// stockage chiffré ; une clé saisie par l'utilisateur n'est jamais écrasée (voir ApiKeyStore).
val devKeys = Properties().apply {
    val f = rootProject.file("dev-keys.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun devKey(name: String): String = "\"${devKeys.getProperty(name, "")}\""

// --- Signature de publication (release) ---
// Les identifiants du keystore sont lus depuis keystore.properties à la racine (NON versionné).
// Absent (autre poste, CI, build debug) → la signature release n'est pas configurée et
// assembleRelease/bundleRelease échouera avec un message explicite ; les builds debug sont intacts.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace = "com.plantinfo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.plantinfo"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Valeurs par défaut vides : le build release ne contient AUCUNE clé API en dur.
        // Le build debug les surcharge ci-dessous avec les clés de dev-keys.properties.
        buildConfigField("String", "DEFAULT_PLANTNET_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_CLAUDE_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_GEMINI_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_OPENAI_API_KEY", "\"\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Clés de dev pré-remplies uniquement en debug (jamais dans l'app publiée).
            buildConfigField("String", "DEFAULT_PLANTNET_API_KEY", devKey("PLANTNET_API_KEY"))
            buildConfigField("String", "DEFAULT_CLAUDE_API_KEY", devKey("CLAUDE_API_KEY"))
            buildConfigField("String", "DEFAULT_GEMINI_API_KEY", devKey("GEMINI_API_KEY"))
            buildConfigField("String", "DEFAULT_OPENAI_API_KEY", devKey("OPENAI_API_KEY"))
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Persistance
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // Réseau
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.exifinterface)

    // Caméra
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Localisation + travaux différés + carte
    implementation(libs.play.services.location)
    implementation(libs.work.runtime.ktx)
    implementation(libs.osmdroid.android)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
