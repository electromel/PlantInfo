import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    namespace = "ch.electromel.plantinfo"
    compileSdk = 36

    defaultConfig {
        applicationId = "ch.electromel.plantinfo"
        minSdk = 29
        // 36 (Android 16) est le minimum exigé par la Play Console depuis 2026 : un bundle qui cible
        // 35 y est refusé au téléversement. Voir les changements de comportement associés dans
        // CLAUDE.md (bord à bord imposé, orientation libre sur grand écran).
        targetSdk = 36
        // versionCode : strictement croissant, un numéro ne peut jamais être réutilisé sur la Console.
        // 1 = première release de test interne (0.1.0, publiée le 2026.07.13).
        // 2 = bundle 0.2.0 construit le 2026.08.14 mais jamais téléversé (numéro consommé localement).
        // 3 = bundle 0.3.0 construit le 2026.08.24 (jetons, coût, aide aux clés).
        // 4 = bundle 0.4.0 publié le 2026.08.26 (assistant de configuration).
        // 5 = bundle 0.5.0 du 2026.09.21, refusé par la Console : ciblait encore l'API 35.
        // 6 = bundle 0.5.0 reconstruit le 2026.09.21 en ciblant l'API 36.
        versionCode = 6
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Valeurs par défaut vides : le build release ne contient AUCUNE clé API en dur.
        // Le build debug les surcharge ci-dessous avec les clés de dev-keys.properties.
        buildConfigField("String", "DEFAULT_PLANTNET_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_CLAUDE_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_GEMINI_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_OPENAI_API_KEY", "\"\"")

        // Horodatage du build, affiché par le filigrane de la version de test. Vide en release :
        // rien n'est estampillé dans l'app publiée.
        buildConfigField("String", "BUILD_STAMP", "\"\"")
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
            // Identifiant distinct : la version publiée est signée par Google (Play App Signing) et
            // refuse toute mise à jour signée localement. Le build de développement s'installe donc
            // à côté d'elle, sans toucher à son historique ni à ses clés.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // Date et heure du build : c'est ce qui distingue deux installations de test du même
            // jour. Recalculé à chaque configuration Gradle, donc le BuildConfig debug se
            // recompile à chaque build — sans intérêt en release, où le champ reste vide.
            buildConfigField(
                "String",
                "BUILD_STAMP",
                "\"" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\"",
            )

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
    androidResources {
        // Seules ces langues sont embarquées (cf. res/xml/locales_config.xml et util/AppLocale.kt).
        // values/ porte l'anglais (langue de repli), values-fr/ le français d'origine.
        localeFilters += listOf("en", "fr", "de", "it", "es")
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
