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
// Date et heure du build, pour le filigrane des builds de test (jamais en release).
val buildStamp: String =
    "\"" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\""

val hasReleaseSigning = keystoreProps.getProperty("storeFile")?.isNotBlank() == true

// Schémas Room exportés (app/schemas, versionnés) : chaque version de la base y laisse sa
// description, ce qui permet de tester une migration contre le schéma réellement publié.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

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
        // 7 = bundle 0.6.0 du 2026.09.28 (multiplication, photos pour trancher, six IA de plus).
        // 8 = bundle 0.6.1 du 2026.10.02 (carte en hexagones, audit de robustesse). Refuse : code deja pris sur la Console.
        // 9 = bundle 0.6.1 reconstruit le 2026.10.02.
        // 10 = bundle 0.6.1 sans READ_MEDIA_IMAGES (politique Photos et videos de Google Play).
        // 11 = bundle 0.7.0 du 2026.11.10 (ajout d'une plante par son nom).
        // 12 = bundle 0.7.1 du 2026.11.11 (R8 : la Console signalait 0 % d'obscurcissement ; audit v2).
        versionCode = 12
        versionName = "0.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Valeurs par défaut vides : le build release ne contient AUCUNE clé API en dur.
        // Le build debug les surcharge ci-dessous avec les clés de dev-keys.properties.
        buildConfigField("String", "DEFAULT_PLANTNET_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_CLAUDE_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_GEMINI_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_OPENAI_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_DEEPSEEK_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_GROK_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_QWEN_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_KIMI_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_MISTRAL_API_KEY", "\"\"")
        buildConfigField("String", "DEFAULT_OPENROUTER_API_KEY", "\"\"")

        // Horodatage du build, affiché par le filigrane de la version de test. Vide en release :
        // rien n'est estampillé dans l'app publiée.
        buildConfigField("String", "BUILD_STAMP", "\"\"")

        // Étiquette des builds de test, affichée par le filigrane. Vide en release : c'est ce
        // vide qui garantit qu'aucun filigrane ne part dans l'application publiée.
        buildConfigField("String", "TEST_BUILD_LABEL", "\"\"")
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
            buildConfigField("String", "BUILD_STAMP", buildStamp)
            buildConfigField("String", "TEST_BUILD_LABEL", "\"DEBUG\"")

            // Clés de dev pré-remplies uniquement en debug (jamais dans l'app publiée).
            buildConfigField("String", "DEFAULT_PLANTNET_API_KEY", devKey("PLANTNET_API_KEY"))
            buildConfigField("String", "DEFAULT_CLAUDE_API_KEY", devKey("CLAUDE_API_KEY"))
            buildConfigField("String", "DEFAULT_GEMINI_API_KEY", devKey("GEMINI_API_KEY"))
            buildConfigField("String", "DEFAULT_OPENAI_API_KEY", devKey("OPENAI_API_KEY"))
            buildConfigField("String", "DEFAULT_DEEPSEEK_API_KEY", devKey("DEEPSEEK_API_KEY"))
            buildConfigField("String", "DEFAULT_GROK_API_KEY", devKey("GROK_API_KEY"))
            buildConfigField("String", "DEFAULT_QWEN_API_KEY", devKey("QWEN_API_KEY"))
            buildConfigField("String", "DEFAULT_KIMI_API_KEY", devKey("KIMI_API_KEY"))
            buildConfigField("String", "DEFAULT_MISTRAL_API_KEY", devKey("MISTRAL_API_KEY"))
            buildConfigField("String", "DEFAULT_OPENROUTER_API_KEY", devKey("OPENROUTER_API_KEY"))
        }
        release {
            // R8 : réduction, optimisation et obscurcissement du code, puis retrait des ressources
            // devenues inutiles. Google Play mesure l'obscurcissement du bundle et pénalise un
            // taux nul. Le fichier de correspondance (mapping.txt) est embarqué dans le bundle :
            // la Console désobscurcit donc les rapports de plantage sans téléversement séparé.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Le build release tel qu'il sera publié (R8 compris), mais installable sur le téléphone
        // de développement : identifiant distinct et signature de debug, puisque la version du
        // Play Store refuse toute mise à jour signée localement. Un défaut dû à R8 (classe
        // retirée ou renommée à tort) ne se voit que dans un build minifié : c'est celui-ci
        // qu'il faut essayer avant chaque publication. Jamais publié.
        create("minified") {
            initWith(getByName("release"))
            applicationIdSuffix = ".minified"
            versionNameSuffix = "-minified"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"

            // Reconnaissable comme le build debug : nom « PlantInfo (R8) » (src/minified/res) et
            // filigrane dans l'application. Sans cela elle se confond avec la version du Play
            // Store, dont elle a l'icône.
            buildConfigField("String", "BUILD_STAMP", buildStamp)
            buildConfigField("String", "TEST_BUILD_LABEL", "\"R8\"")

            // Mêmes clés de développement que le build debug, pour pouvoir identifier une plante.
            buildConfigField("String", "DEFAULT_PLANTNET_API_KEY", devKey("PLANTNET_API_KEY"))
            buildConfigField("String", "DEFAULT_CLAUDE_API_KEY", devKey("CLAUDE_API_KEY"))
            buildConfigField("String", "DEFAULT_GEMINI_API_KEY", devKey("GEMINI_API_KEY"))
            buildConfigField("String", "DEFAULT_OPENAI_API_KEY", devKey("OPENAI_API_KEY"))
            buildConfigField("String", "DEFAULT_DEEPSEEK_API_KEY", devKey("DEEPSEEK_API_KEY"))
            buildConfigField("String", "DEFAULT_GROK_API_KEY", devKey("GROK_API_KEY"))
            buildConfigField("String", "DEFAULT_QWEN_API_KEY", devKey("QWEN_API_KEY"))
            buildConfigField("String", "DEFAULT_KIMI_API_KEY", devKey("KIMI_API_KEY"))
            buildConfigField("String", "DEFAULT_MISTRAL_API_KEY", devKey("MISTRAL_API_KEY"))
            buildConfigField("String", "DEFAULT_OPENROUTER_API_KEY", devKey("OPENROUTER_API_KEY"))
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
