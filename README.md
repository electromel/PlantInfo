# PlantInfo

Application Android native d'identification de **plantes, arbres et champignons** par photo, avec
diagnostic de santé, informations, géolocalisation, questions à l'IA et historique **100 % local**.

> État : **publiée sur Google Play**. Le journal de développement est dans
> [`log/`](log/) ; les instructions détaillées pour travailler sur le code sont dans
> [`CLAUDE.md`](CLAUDE.md), qui fait référence en cas d'écart avec ce fichier.

## Fonctionnement

1. On photographie (caméra) ou importe (galerie) une plante/arbre/champignon — ou on l'ajoute
   **par son nom**, une photo étant alors cherchée sur Wikipédia.
2. La position GPS (latitude, longitude, altitude, précision) est récupérée comme critère
   complémentaire. Pour une photo importée, le géotag EXIF du fichier prime sur la position courante.
3. Pipeline hybride :
   - **Pl@ntNet** identifie les candidats taxonomiques (plantes/arbres).
   - Une **IA générative multimodale** (neuf fournisseurs possibles, avec repli automatique dans
     l'ordre réglé ; par défaut seul Gemini, gratuit, est utilisé) valide/corrige,
     identifie les champignons, évalue l'état de santé, enrichit et calcule un **score d'exactitude
     sur 100**.
4. Le résultat s'affiche (score, avertissements, comestibilité, habitat, santé, dimensions à
   maturité, calendrier d'entretien, usages, symbolique, alternatives, carte) et est enregistré dans
   l'historique local.
5. On peut poser des **questions libres à l'IA** sur la plante identifiée, au clavier ou à la voix.
6. Après chaque identification et chaque question, l'app affiche les **jetons consommés et le coût
   estimé** de l'appel.

## Choix techniques

| Domaine | Choix |
|---|---|
| Langage / UI | Kotlin + Jetpack Compose (Material 3) |
| Architecture | MVVM + Repository, module unique `app`, DI Hilt |
| Base locale | Room (SQLite), photos compressées en stockage interne |
| Réseau | OkHttp + kotlinx.serialization (appels directs, **aucun backend**) |
| Caméra | CameraX (`LifecycleCameraController`) |
| Localisation | FusedLocationProvider (lat/long + altitude + précision) + EXIF des photos importées |
| Clés API | `EncryptedSharedPreferences` (Android Keystore) — chiffrées au repos |
| Carte | osmdroid (OpenStreetMap, libre) + occurrences GBIF |
| File hors-ligne | WorkManager |
| Langues | Français, anglais, allemand, italien, espagnol |
| minSdk / targetSdk | 29 (Android 10) / 36 |

Toutes les données personnelles (photos, GPS, historique) **restent sur l'appareil** (LPD/CH). Seuls
les appels ponctuels aux API externes (Pl@ntNet, IA, GBIF, tuiles OSM, Wikipédia) transmettent ce qui
leur est nécessaire, sans conservation côté application. Le détail est dans la
[politique de confidentialité](docs/privacy-policy.html).

## Clés API nécessaires

Au **premier lancement**, un **assistant de configuration** plein écran explique ce que fait
l'application, ce qui sort de l'appareil et pour qui, puis guide la saisie des clés (rôle, coût réel,
marche à suivre numérotée, piège classique, test de validité immédiat). Il revient tant qu'il n'a pas
été mené à son terme. L'écran Paramètres ne gère ensuite que l'**état** des clés ; le bouton **« + »**
et « Relancer l'assistant » renvoient vers ce même parcours.

| Fournisseur | Rôle | Coût | Obtention |
|---|---|---|---|
| **Pl@ntNet** (indispensable) | Identification taxonomique | Gratuit (quota quotidien) | https://my.plantnet.org/account/settings |
| **Gemini** (Google) | Description, santé, champignons, Q&A | Gratuit dans le palier gratuit | https://aistudio.google.com/apikey |
| Claude (Anthropic) | Même rôle, alternative | Payant à l'usage | https://console.anthropic.com/settings/keys |
| GPT (OpenAI) | Même rôle, alternative | Payant à l'usage | https://platform.openai.com/api-keys |
| DeepSeek, Grok (xAI), Qwen (Alibaba), Kimi (Moonshot), Mistral, OpenRouter | Même rôle, alternatives | Payant à l'usage | Lien et marche à suivre dans l'assistant |

Un abonnement Claude Pro ou ChatGPT Plus **n'inclut aucun crédit API** : le compte développeur se
crédite séparément. Le mode **« IA gratuite (Gemini seul) »**, actif par défaut, ignore tous les
autres fournisseurs pour éviter de consommer des crédits par inadvertance.

Sans clé IA, l'app affiche le résultat **Pl@ntNet brut** avec un message explicite.

### Clé devenue invalide

Les clés enregistrées sont retestées **une fois par jour** au lancement. Une clé refusée, un compte
sans crédit ou un quota épuisé sont signalés au démarrage, avec le motif réel, et l'écran Paramètres
permet de forcer une revérification. Un simple échec réseau ne fait jamais passer une clé valide pour
invalide. Une clé mise de côté par le mode « Gemini seul » n'est ni testée d'office ni signalée.

### Clés par défaut pour le développement

Copier `dev-keys.properties.example` en `dev-keys.properties` (racine, **non versionné**) et y mettre
ses clés. Elles sont injectées dans `BuildConfig` et servent à **pré-remplir** les paramètres au
premier lancement (sans jamais écraser une clé saisie manuellement). Seuls les builds `debug` et
`minified` les reçoivent : le build `release` publié n'en contient **aucune**.

## Build & installation

Prérequis : **Android Studio** (Ladybug ou plus récent). Le daemon Gradle exige un **JDK JetBrains
21** (`gradle/gradle-daemon-jvm.properties` : `toolchainVendor=jetbrains`, `toolchainVersion=21`) ;
le JBR livré avec Android Studio convient — `C:\Program Files\Android\Android Studio\jbr`.

1. Ouvrir le dossier du projet dans Android Studio → il génère le wrapper Gradle et synchronise.
   - Le binaire `gradle/wrapper/gradle-wrapper.jar` n'est pas versionné ; en ligne de commande,
     le régénérer une fois avec un Gradle local (`gradle wrapper --gradle-version 9.1.0`).
2. Créer `local.properties` avec le chemin du SDK (`sdk.dir=...`) — Android Studio le crée
   automatiquement.
3. (Optionnel) Renseigner `dev-keys.properties`.
4. Compiler et tester en ligne de commande (PowerShell) — passer explicitement le JBR, sinon Gradle
   tente de télécharger un toolchain et échoue :
   ```powershell
   $jbr = "C:\Program Files\Android\Android Studio\jbr"
   & .\gradlew.bat "-Dorg.gradle.java.installations.paths=$jbr" `
       :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
   & .\gradlew.bat "-Dorg.gradle.java.installations.paths=$jbr" assembleDebug
   ```
   APK produit dans `app/build/outputs/apk/debug/`. Ce build a son propre identifiant (`.debug`),
   une icône au bandeau **DEV** et un filigrane : il s'installe à côté de la version du Play Store.
5. **Vérification complète** avant de livrer : tests, lint et traductions.
   ```powershell
   & .\gradlew.bat "-Dorg.gradle.java.installations.paths=$jbr" :app:testDebugUnitTest :app:lintDebug
   python tools/check_translations.py
   ```
6. **Publication** : `bundleRelease` produit le bundle signé (`keystore.properties` à la racine, non
   versionné). Le build release passe par **R8** ; il s'essaie sur un téléphone avec la variante
   `minified` (« PlantInfo (R8) »), voir `CLAUDE.md`. Les textes de la fiche Play Store et les notes
   de version sont dans [`docs/`](docs/).

Les tests sont des tests JVM (`src/test`, JUnit4 + MockK). **Lint** (`lintDebug`) s'appuie sur une base
de référence (`app/lint-baseline.xml`) : il ne signale que les problèmes nouveaux, et une erreur fait
échouer le build.

## Structure

```
app/src/main/java/ch/electromel/plantinfo/
├── data/
│   ├── db/       Room (entité, DAO, base, migrations, convertisseurs)
│   ├── keys/     ApiKeyStore chiffré, guides d'obtention, surveillance de validité
│   ├── prefs/    Réglages en clair (seuils de sécurité, assistant terminé, langue)
│   ├── remote/   Pl@ntNet + clients IA (Claude, Gemini, compatibles OpenAI) + orchestrateur + GBIF + Wikipédia
│   └── repo/     IdentificationRepository, HistoryRepository, PlantQaRepository, mappers
├── domain/       Modèles, ConfidenceEngine (fusion des scores), listes de sécurité, tarifs IA
├── di/           Modules Hilt (réseau, base)
├── ui/           Écrans Compose (capture, result, detail, history, settings, setup, startup, qa, map)
├── util/         Compression image, EXIF, localisation, export PDF, partage, notifications
└── work/         File d'attente hors-ligne (WorkManager)
```

## Découpage par phases

- **Phase 1 (faite)** : capture + GPS + Pl@ntNet + IA (repli) + score + résultat + historique.
- **Phase 2 (faite)** : carte osmdroid + aire de répartition GBIF (cache par espèce) + rayon
  d'incertitude.
- **Phase 3 (faite)** : file d'attente hors-ligne (WorkManager) + notifications de résultat différé
  + alerte espèces protégées (liste Suisse indicative).
- **Phase 4 (faite)** : export PDF, partage natif (image + résumé), filtres historique avancés
  (période, localisés), finitions d'accessibilité.
- **Depuis** : comestibilité/toxicité, dimensions à maturité, calendrier d'entretien, usages et
  symbolique, questions libres à l'IA, avertissement de confusion toxique réglable, accompagnement
  des clés API (assistant de configuration, ajout par « + », alerte de clé invalide), affichage des
  jetons et du coût de chaque appel IA, cinq langues, six fournisseurs d'IA supplémentaires, photos
  complémentaires demandées quand l'identification est incertaine, multiplication, ajout d'une
  plante par son nom, build release minifié (R8).

## Limites connues

- La **notification** de résultat différé ouvre l'app, sans deep-link direct vers la fiche.
- Le **lieu d'une photo importée** n'est lu que si l'accès demandé au premier import est accordé :
  sans lui, Android masque les coordonnées du fichier.
- Les **réponses aux questions** ne sont pas conservées : elles disparaissent en quittant la fiche.
- L'**alerte espèce protégée** combine le jugement de l'IA et une **liste de référence Suisse
  indicative et non exhaustive** (`ProtectedSpeciesChecker`) — la réglementation cantonale/fédérale
  fait foi.
- L'**aire de répartition** affichée est celle des observations GBIF (hexagones de densité, avertissement
  affiché), pas une limite scientifique/légale : elle inclut les régions où l'espèce est naturalisée.
- Le **coût affiché** est une estimation au tarif public du modèle : les paliers gratuits, les
  remises de cache et les tarifs d'introduction ne sont pas modélisés. La table de tarifs
  (`domain/model/TokenUsage.kt`) est tenue à la main et doit être relue quand un fournisseur change
  ses prix ou quand on change de modèle.
- Les **modèles IA** de chaque fournisseur sont fixés dans le code des clients (`data/remote/ai/`),
  avec leur tarif dans `AiPricing.rates` : les deux se modifient ensemble.

## Emplacement du projet

Le projet vit sous `C:\DEV\PlantInfo`, **hors de OneDrive**. La synchronisation OneDrive verrouillait
le dossier `app/build` pendant les builds Gradle (erreurs de suppression de répertoire). Garder le
projet hors d'un dossier synchronisé évite ces conflits.

## Versions de build

AGP 8.13.2 / Gradle 9.1.0 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / JBR 21 (Android Studio). Ne pas
accepter l'auto-montée vers AGP 9 proposée par Android Studio sans migration dédiée (elle casse KSP).
Les versions sont centralisées dans `gradle/libs.versions.toml`.
