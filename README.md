# PlantInfo

Application Android native d'identification de **plantes, arbres et champignons** par photo, avec
diagnostic de santé, informations, géolocalisation, questions à l'IA et historique **100 % local**.

> État : **Phase 4 terminée** — voir le journal de développement dans [`log/`](log/) et le découpage
> par phases plus bas.

## Fonctionnement

1. On photographie (caméra) ou importe (galerie) une plante/arbre/champignon.
2. La position GPS (latitude, longitude, altitude, précision) est récupérée comme critère
   complémentaire. Pour une photo importée, le géotag EXIF du fichier prime sur la position courante.
3. Pipeline hybride :
   - **Pl@ntNet** identifie les candidats taxonomiques (plantes/arbres).
   - Une **IA générative multimodale** (neuf fournisseurs au choix : Claude, Gemini, GPT, DeepSeek,
     Grok, Qwen, Kimi, Mistral, OpenRouter — repli automatique dans l'ordre réglé, seuls ceux dont une
     clé est saisie sont contactés) valide/corrige, identifie les champignons, évalue l'état de santé,
     enrichit et calcule un **score d'exactitude sur 100**.
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
| minSdk / targetSdk | 29 (Android 10) / 36 |

Toutes les données personnelles (photos, GPS, historique) **restent sur l'appareil** (LPD/CH). Seuls
les appels ponctuels aux API externes (Pl@ntNet, IA, GBIF, tuiles OSM) transmettent une photo ou un
lieu — ce dernier **arrondi à environ 1 km** et jamais à Pl@ntNet —, sans conservation côté
application. Le détail, en cinq langues, est dans [`docs/privacy-policy.html`](docs/privacy-policy.html).

## Clés API nécessaires

Au **premier lancement**, l'application invite à ouvrir les Paramètres et explique ce qu'est une clé
API. L'écran Paramètres n'affiche que les clés **déjà renseignées** ; les autres s'ajoutent avec le
bouton **« + »**. Chaque clé propose une **aide pas à pas** (rôle, coût réel, marche à suivre
numérotée, piège classique) et un test de validité immédiat.

| Fournisseur | Rôle | Coût | Obtention |
|---|---|---|---|
| **Pl@ntNet** (indispensable) | Identification taxonomique | Gratuit (quota quotidien) | https://my.plantnet.org/account/settings |
| **Gemini** (Google) | Description, santé, champignons, Q&A | Gratuit dans le palier gratuit | https://aistudio.google.com/apikey |
| Claude (Anthropic) | Même rôle, alternative | Payant à l'usage | https://console.anthropic.com/settings/keys |
| GPT (OpenAI) | Même rôle, alternative | Payant à l'usage | https://platform.openai.com/api-keys |
| DeepSeek, Grok, Qwen, Kimi, Mistral, OpenRouter | Même rôle, alternatives | Selon le fournisseur | Assistant de configuration (« + » dans les Paramètres) |

Un abonnement Claude Pro ou ChatGPT Plus **n'inclut aucun crédit API** : le compte développeur se
crédite séparément. Le mode **« IA gratuite (Gemini seul) »**, actif par défaut, ignore les autres IA
payantes pour éviter de consommer des crédits par inadvertance — mais seulement **si une clé Gemini
est saisie** : sans elle, la clé d'un autre fournisseur est utilisée.

Sans clé IA, l'app affiche le résultat **Pl@ntNet brut** avec un message explicite.

### Clé devenue invalide

Les clés enregistrées sont retestées **une fois par jour** au lancement. Une clé refusée, un compte
sans crédit ou un quota épuisé sont signalés au démarrage, avec le motif réel, et l'écran Paramètres
permet de forcer une revérification. Un simple échec réseau ne fait jamais passer une clé valide pour
invalide.

### Clés par défaut pour le développement

Copier `dev-keys.properties.example` en `dev-keys.properties` (racine, **non versionné**) et y mettre
ses clés. Elles sont injectées dans `BuildConfig` et servent à **pré-remplir** les paramètres au
premier lancement (sans jamais écraser une clé saisie manuellement). Elles ne sont injectées que dans
le build `debug` : l'application publiée ne contient aucune clé.

## Build & installation

Prérequis : **Android Studio** (Ladybug ou plus récent). Le daemon Gradle exige un **JDK JetBrains
21** (`gradle/gradle-daemon-jvm.properties` : `toolchainVendor=jetbrains`, `toolchainVersion=21`) ;
le JBR livré avec Android Studio convient — `C:\Program Files\Android\Android Studio\jbr`.

1. Ouvrir le dossier du projet dans Android Studio → il génère le wrapper Gradle et synchronise.
   - Le wrapper Gradle (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`) est versionné.
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
   APK produit dans `app/build/outputs/apk/debug/`.
5. **Installation** sur un appareil Android 10+ : APK debug en side-loading (identifiant
   `ch.electromel.plantinfo.debug`, il cohabite avec la version du Play Store), ou publication via la
   Google Play Console — voir [`docs/play-store-inscription.md`](docs/play-store-inscription.md).

**Qualité** : les tests JVM (`src/test`, JUnit4 + MockK) et le lint (`:app:lintDebug`) tournent en CI
(`.github/workflows/ci.yml`). Les tests de migration Room (`src/androidTest`) se lancent à la main sur
un appareil — voir `CLAUDE.md`, section « Tests ».

## Structure

```
app/src/main/java/ch/electromel/plantinfo/
├── data/
│   ├── db/       Room (entité, DAO, base, migrations, convertisseurs)
│   ├── keys/     ApiKeyStore chiffré, guides d'obtention, surveillance de validité
│   ├── prefs/    Réglages en clair (seuils de sécurité, accueil vu)
│   ├── remote/   Pl@ntNet + clients IA (Claude, Gemini, compatibles OpenAI) + orchestrateur + GBIF
│   └── repo/     IdentificationRepository, HistoryRepository, PlantQaRepository, mappers
├── domain/       Modèles, ConfidenceEngine (fusion des scores), listes de sécurité, tarifs IA
├── di/           Modules Hilt (réseau, base)
├── ui/           Écrans Compose (capture, result, detail, history, settings, startup, qa, map)
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
  des clés API (premier lancement, aide novice, ajout par « + », alerte de clé invalide) et
  affichage des jetons et du coût de chaque appel IA.

## Limites connues

- La **notification** de résultat différé ouvre l'app, sans deep-link direct vers la fiche.
- L'**alerte espèce protégée** combine le jugement de l'IA et une **liste de référence Suisse
  indicative et non exhaustive** (`ProtectedSpeciesChecker`) — la réglementation cantonale/fédérale
  fait foi.
- L'**aire de répartition** est une enveloppe convexe approximative de 300 occurrences GBIF (observations
  et spécimens récoltés ; jardins, collections vivantes et fossiles exclus), pas une limite
  scientifique/légale. Un nom que GBIF ne connaît pas n'affiche pas l'aire de son genre. Un échantillon
  plus large demanderait de paginer GBIF en profondeur, ce qui peut dépasser la minute.
- Le **coût affiché** est une estimation au tarif public du modèle : les paliers gratuits, les
  remises de cache et les tarifs d'introduction ne sont pas modélisés. La table de tarifs
  (`domain/model/TokenUsage.kt`) est tenue à la main et doit être relue quand un fournisseur change
  ses prix ou quand on change de modèle.
- Modèles IA par défaut : voir `data/remote/ai/` (`ClaudeClient`, `GeminiClient`,
  `CompatibleProviderConfig`) ; chaque modèle doit avoir son tarif dans `AiPricing.rates`.
- Le stockage des clés repose sur `androidx.security:security-crypto`, déclaré obsolète par Google :
  encore fonctionnel, à remplacer le jour où il sera retiré (voir `CLAUDE.md`).
- Les dépendances de plateforme (Kotlin 2.0.21, Compose BoM 2024.12, Hilt, Room 2.6, OkHttp 4.12…) ont
  du retard sur leurs dernières versions : leur mise à jour est une migration à part entière (Kotlin,
  KSP, Hilt et Room doivent avancer ensemble ; AGP 9 casse KSP), pas un simple bump.

## Emplacement du projet

Le projet vit sous `C:\DEV\PlantInfo`, **hors de OneDrive**. La synchronisation OneDrive verrouillait
le dossier `app/build` pendant les builds Gradle (erreurs de suppression de répertoire). Garder le
projet hors d'un dossier synchronisé évite ces conflits.

## Versions de build

AGP 8.13.2 / Gradle 9.1.0 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / JBR 21 (Android Studio). Ne pas
accepter l'auto-montée vers AGP 9 proposée par Android Studio sans migration dédiée (elle casse KSP).
Les versions sont centralisées dans `gradle/libs.versions.toml`.
