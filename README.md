# PlantInfo

Application Android native d'identification de **plantes, arbres et champignons** par photo, avec
diagnostic de santé, informations, géolocalisation et historique **100 % local**.

> État : **Phase 2 terminée** (carte + aire de répartition) — voir le journal de développement dans
> [`log/`](log/) et le découpage par phases plus bas.

## Fonctionnement

1. On photographie (caméra) ou importe (galerie) une plante/arbre/champignon.
2. La position GPS (latitude, longitude, altitude, précision) est récupérée comme critère
   complémentaire.
3. Pipeline hybride :
   - **Pl@ntNet** identifie les candidats taxonomiques (plantes/arbres).
   - Une **IA générative multimodale** (Claude → Gemini → GPT, avec repli automatique) valide/corrige,
     identifie les champignons, évalue l'état de santé, enrichit et calcule un **score d'exactitude
     sur 100**.
4. Le résultat s'affiche (score, avertissements, habitat, santé, infos, alternatives) et est
   enregistré dans l'historique local.

## Choix techniques

| Domaine | Choix |
|---|---|
| Langage / UI | Kotlin + Jetpack Compose (Material 3) |
| Architecture | MVVM + Repository, module unique `app`, DI Hilt |
| Base locale | Room (SQLite), photos compressées en stockage interne |
| Réseau | OkHttp + kotlinx.serialization (appels directs, **aucun backend**) |
| Caméra | CameraX (`LifecycleCameraController`) |
| Localisation | FusedLocationProvider (lat/long + altitude + précision) |
| Clés API | `EncryptedSharedPreferences` (Android Keystore) — chiffrées au repos |
| Carte (Phase 2) | osmdroid (OpenStreetMap, libre) |
| File hors-ligne (Phase 3) | WorkManager |
| minSdk / targetSdk | 29 (Android 10) / 35 |

Toutes les données personnelles (photos, GPS, historique) **restent sur l'appareil** (LPD/CH). Seuls
les appels ponctuels aux API externes (Pl@ntNet, IA, GBIF, tuiles OSM) transmettent une photo et des
coordonnées, sans conservation côté application.

## Clés API nécessaires

Renseignées par l'utilisateur dans **Paramètres** (chiffrées localement). Boutons de raccourci vers
les pages de création + test immédiat de validité.

| Fournisseur | Rôle | Obtention |
|---|---|---|
| **Pl@ntNet** (obligatoire) | Identification taxonomique | https://my.plantnet.org/account/settings |
| Claude (Anthropic) | Synthèse, diagnostic, champignons | https://console.anthropic.com/settings/keys |
| Gemini (Google) | Repli | https://aistudio.google.com/apikey |
| GPT (OpenAI) | Repli | https://platform.openai.com/api-keys |

Sans clé IA, l'app affiche le résultat **Pl@ntNet brut** avec un message explicite.

### Clés par défaut pour le développement

Copier `dev-keys.properties.example` en `dev-keys.properties` (racine, **non versionné**) et y mettre
ses clés. Elles sont injectées dans `BuildConfig` et servent à **pré-remplir** les paramètres au
premier lancement (sans jamais écraser une clé saisie manuellement). Voir le commentaire en tête de
`app/build.gradle.kts` pour restreindre cela au build `debug` en cas de publication future.

## Build & installation

Prérequis : **Android Studio** (Ladybug ou plus récent). Utiliser le **JDK 17 embarqué**
d'Android Studio (Gradle 9.1 requiert au minimum JDK 17 pour s'exécuter).

1. Ouvrir le dossier du projet dans Android Studio → il génère le wrapper Gradle et synchronise.
   - En ligne de commande, générer le wrapper une fois avec un Gradle local : `gradle wrapper`
     (le binaire `gradle/wrapper/gradle-wrapper.jar` n'est pas versionné dans ce dépôt).
2. Créer `local.properties` avec le chemin du SDK (`sdk.dir=...`) — Android Studio le crée
   automatiquement.
3. (Optionnel) Renseigner `dev-keys.properties`.
4. Compiler l'APK debug :
   ```
   ./gradlew assembleDebug
   ```
   APK produit dans `app/build/outputs/apk/debug/`.
5. **Installation par side-loading** sur un appareil Android 10+ (APK direct), ou via une piste de
   test interne (Google Play Internal Testing / Firebase App Distribution). Pas de publication
   publique prévue.

## Structure

```
app/src/main/java/com/plantinfo/
├── data/
│   ├── db/       Room (entité, DAO, base, convertisseurs)
│   ├── keys/     ApiKeyStore chiffré + ordre de repli
│   ├── remote/   Pl@ntNet + clients IA (Claude/Gemini/GPT) + orchestrateur
│   └── repo/     IdentificationRepository, HistoryRepository, mappers
├── domain/       Modèles + ConfidenceEngine (fusion des scores)
├── di/           Modules Hilt (réseau, base)
├── ui/           Écrans Compose (capture, result, history, detail, settings) + thème
└── util/         Compression image, localisation
```

## Découpage par phases

- **Phase 1 (faite)** : capture + GPS + Pl@ntNet + IA (repli) + score + résultat + historique.
- **Phase 2 (faite)** : carte osmdroid + aire de répartition GBIF (cache par espèce) + rayon
  d'incertitude.
- **Phase 3 (faite)** : file d'attente hors-ligne (WorkManager) + notifications de résultat différé
  + alerte espèces protégées (liste Suisse indicative).
- **Phase 4 (faite)** : export PDF, partage natif (image + résumé), filtres historique avancés
  (période, localisés), finitions d'accessibilité.

## Limites connues (après Phase 4)

- La **notification** de résultat différé ouvre l'app, sans deep-link direct vers la fiche.
- Le **guidage des photos complémentaires** est indiqué sur la fiche mais le flux « ajouter la photo
  demandée et relancer » n'est pas encore intégré.
- L'**alerte espèce protégée** combine le jugement de l'IA et une **liste de référence Suisse
  indicative et non exhaustive** (`ProtectedSpeciesChecker`) — la réglementation cantonale/fédérale
  fait foi.
- L'**aire de répartition** est une enveloppe convexe approximative des occurrences GBIF (avertissement
  affiché), pas une limite scientifique/légale.
- Modèles IA par défaut : `claude-sonnet-5`, `gemini-2.0-flash`, `gpt-4o` (modifiables dans le code
  des clients `data/remote/ai/`).

## Emplacement du projet

Le projet vit sous `C:\DEV\PlantInfo`, **hors de OneDrive**. La synchronisation OneDrive verrouillait
le dossier `app/build` pendant les builds Gradle (erreurs de suppression de répertoire). Garder le
projet hors d'un dossier synchronisé évite ces conflits.

## Versions de build

AGP 8.13.2 / Gradle 9.1.0 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / JBR 17 (Android Studio). Ne pas
accepter l'auto-montée vers AGP 9 proposée par Android Studio sans migration dédiée (elle casse KSP).
Le wrapper vise Gradle 9.1.0 ; AGP 8.13.2 reste compatible avec Gradle 9.x.
