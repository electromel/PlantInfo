# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Vue d'ensemble

Application Android native d'identification de **plantes, arbres et champignons** par photo, avec
diagnostic de santé, informations, carte de répartition, comestibilité/toxicité, questions à l'IA et
historique **100 % local**. Aucune donnée personnelle ne quitte l'appareil, hormis les appels
ponctuels aux API externes (Pl@ntNet, IA, GBIF, tuiles OSM).

## Emplacement du projet — important

Le code vit sous **`C:\DEV\PlantInfo`**, hors de OneDrive. La synchronisation OneDrive verrouille
`app/build` pendant les builds Gradle et provoque des erreurs de suppression de répertoire. **Ne pas
déplacer le projet dans un dossier synchronisé.** (Le dossier ouvert par défaut peut être
`C:\Users\reyna\OneDrive\Documents\Claude\PlantInfo`, qui ne contient que `.idea`/`.gradle` ; le vrai
projet est `C:\DEV\PlantInfo`.)

## Build & test

Le **wrapper jar n'est pas versionné** et il n'y a pas de `gradle` sur le PATH. Le projet exige un
JDK **JetBrains 21** pour exécuter le daemon (voir `gradle/gradle-daemon-jvm.properties` :
`toolchainVendor=jetbrains`, `toolchainVersion=21`). Le JBR d'Android Studio convient :
`C:\Program Files\Android\Android Studio\jbr`.

En ligne de commande (PowerShell), passer ce JDK à Gradle, sinon Gradle tente de télécharger un
toolchain JBR et échoue :

```powershell
$jbr = "C:\Program Files\Android\Android Studio\jbr"
# Compilation + KSP (Room/Hilt) + tests unitaires
& C:\DEV\PlantInfo\gradlew.bat -p C:\DEV\PlantInfo "-Dorg.gradle.java.installations.paths=$jbr" `
    :app:compileDebugKotlin :app:testDebugUnitTest --console=plain

# APK debug complet
& C:\DEV\PlantInfo\gradlew.bat -p C:\DEV\PlantInfo "-Dorg.gradle.java.installations.paths=$jbr" assembleDebug

# Un seul test / une seule classe
& C:\DEV\PlantInfo\gradlew.bat -p C:\DEV\PlantInfo "-Dorg.gradle.java.installations.paths=$jbr" `
    :app:testDebugUnitTest --tests "com.plantinfo.domain.ConfidenceEngineTest"
```

Si `gradlew.bat`/`gradle-wrapper.jar` manquent, les régénérer avec une distribution Gradle en cache
(`~/.gradle/wrapper/dists/gradle-9.x/.../bin/gradle.bat`) :
`gradle.bat -p C:\DEV\PlantInfo "-Dorg.gradle.java.installations.paths=$jbr" wrapper --gradle-version 9.1.0 --distribution-type bin`.

Il n'y a **pas de lint configuré** au-delà des warnings du compilateur Kotlin et d'AGP. Les tests
sont des tests JVM (`src/test`, JUnit4 + MockK) — pas de tests instrumentés significatifs.

### Versions de build (compatibilité sensible)

AGP 8.13.2 / **Gradle 9.1.0** / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / minSdk 29 / targetSdk & compileSdk 35.
**Ne pas** accepter l'auto-montée vers AGP 9 proposée par Android Studio sans migration dédiée : elle
casse KSP. Les versions sont centralisées dans `gradle/libs.versions.toml` (version catalog `libs`).

## Clés API

Renseignées par l'utilisateur dans l'écran **Paramètres**, chiffrées via `EncryptedSharedPreferences`
(Android Keystore) — voir `data/keys/ApiKeyStore.kt`. Pl@ntNet est la seule clé quasi obligatoire.
Pour le développement, copier `dev-keys.properties.example` → `dev-keys.properties` (racine, non
versionné) : les valeurs sont injectées dans `BuildConfig` (`app/build.gradle.kts`) et **pré-remplissent**
uniquement les clés absentes du stockage — une clé saisie manuellement n'est jamais écrasée.

### Accompagnement de l'utilisateur (à ne pas contourner)

- **Premier lancement** : `ui/startup/StartupViewModel` + `WelcomeDialog` invitent à renseigner les
  paramètres. Le drapeau « accueil vu » vit dans `data/prefs/OnboardingStore`.
- **Aide novice** : `data/keys/ApiKeyGuide.kt` porte, par fournisseur, le rôle, le coût réel et la
  marche à suivre numérotée. C'est du **contenu**, pas du code : le mettre à jour quand l'interface
  web d'un fournisseur change, sans toucher à `ApiProvider`.
- **Écran Paramètres** : n'affiche que les clés **renseignées** ; les autres se choisissent derrière
  le bouton « + » (`SettingsUiState.addable`).
- **Clé devenue invalide** : `data/keys/KeyHealthMonitor` retente les clés stockées **une fois par
  24 h** (`CHECK_INTERVAL_MS`) et persiste le verdict, ce qui permet de le rappeler à chaque
  lancement sans rappeler les API. Deux règles à respecter :
  - un échec **réseau/serveur** donne `UNVERIFIABLE` et ne dégrade jamais un verdict précédent ;
  - tout test payé ailleurs (saisie d'une clé dans les Paramètres) est reversé au moniteur via
    `record()` plutôt que refait.

  Chaque vérification consomme une vraie requête chez le fournisseur — le palier gratuit de Gemini
  se compte en dizaines de requêtes par jour : ne pas raccourcir l'intervalle.

## Architecture (le fil conducteur)

MVVM + Repository, module unique `app`, DI **Hilt**, UI **Jetpack Compose (Material 3)**. Navigation
Compose avec 3 onglets (`Capture`, `Historique`, `Paramètres`) + écrans `result/{id}` et `detail/{id}`
(voir `ui/navigation/Destinations.kt`).

### Pipeline d'identification (le cœur)

Point d'entrée : `data/repo/IdentificationRepository.identifyAndSave(request)`. Étapes :

1. **Pl@ntNet** (`data/remote/plantnet`) → candidats taxonomiques (plantes/arbres ; peu fiable ou
   absent pour les champignons).
2. **IA générative** via `data/remote/ai/AiOrchestrator` : applique l'**ordre de repli** configuré
   (défaut Claude → Gemini → GPT) en ne retenant que les fournisseurs ayant une clé, et bascule au
   suivant à chaque `AiException`. Chaque client (`ClaudeClient`/`GeminiClient`/`OpenAiClient`)
   implémente `AiProvider` et partage **le même prompt** et le même schéma JSON défini dans
   `AiPrompt.kt` — modifier le prompt/schéma là, pas dans les clients.
3. **Fusion des scores** : `domain/ConfidenceEngine` combine IA (prioritaire) + Pl@ntNet en un
   `IdentificationResult` avec un `scoreFinal /100` (bonus si accord, pénalité + `sourcesDisagree` si
   désaccord ; suit l'IA seule pour les champignons).
4. **Persistance** : `Mappers.kt` convertit `IdentificationResult` ↔ `IdentificationEntity` (Room).
   `ProtectedSpeciesChecker` renforce (jamais ne désactive) le drapeau « espèce protégée ».

Cas dégradés gérés par le repository et exposés via `IdentificationOutcome` (typé) : aucune clé IA →
résultat Pl@ntNet brut ; toutes les IA en échec réseau → mise en file `work/IdentificationQueue`
(WorkManager, contrainte réseau) qui rejoue via `IdentificationWorker` et notifie
(`util/NotificationHelper`).

### Jetons consommés et coût affiché

Chaque appel IA rapporte sa consommation ; l'app l'affiche après l'identification (carte « Coût de
l'identification ») et sous chaque réponse du Q&A.

- Chaque client IA extrait le bloc d'usage de **sa** réponse (`usage` chez Anthropic/OpenAI,
  `usageMetadata` chez Gemini — dont `thoughtsTokenCount`, facturé comme de la sortie) et le pose
  dans `AiAnalysis.usage` / `AiAnswer.usage`.
- `domain/model/TokenUsage.kt` porte le modèle (**pas** le fournisseur : les tarifs sont par modèle)
  et la table `AiPricing`. **Le coût n'est jamais persisté** : il est recalculé à l'affichage, si
  bien qu'une mise à jour des tarifs corrige aussi l'historique.
- Les tarifs sont relevés à la main (aucune API de tarification n'existe) : les mettre à jour dans
  `AiPricing.rates` **en même temps** que tout changement de modèle dans un client, sinon un modèle
  inconnu s'affiche sans montant.
- Un fournisseur muet sur l'usage donne `null` : on affiche alors les jetons sans montant, jamais un
  zéro qui se lirait comme la gratuité.

### Ajouter un champ au résultat d'identification (checklist)

Un champ qui traverse tout le pipeline doit être ajouté de façon cohérente à **tous** ces endroits
(sinon échec de compilation ou perte de donnée). Exemple récent : `edible`/`toxic`/`edibilityNote`.

1. `domain/model/Models.kt` → `IdentificationResult`
2. `data/remote/ai/AiModels.kt` → `AiAnalysis`
3. `data/remote/ai/AiPrompt.kt` → schéma JSON du prompt + `AiResponseDto` + `toDomain()`
4. `domain/ConfidenceEngine.kt` → `combineWithAi()` **et** `plantNetOnly()`
5. `data/db/IdentificationEntity.kt` → colonne
6. `data/repo/Mappers.kt` → `toEntity()` **et** `toResult()`
7. `data/db/PlantInfoDatabase.kt` → incrémenter `version` + ajouter une `Migration` additive
   (`ALTER TABLE … ADD COLUMN`, non destructive), puis l'enregistrer dans `di/DatabaseModule.kt`
8. Affichage dans `ui/result/IdentificationContent.kt` (partagé Résultat + Détail)
9. Le cas échéant : `util/PdfExporter.kt` et `util/ShareHelper.kt`
10. Mettre à jour le builder de test `domain/ConfidenceEngineTest.kt` si `AiAnalysis` change

La migration Room est le point le plus facile à oublier : sans elle l'app crashe au démarrage sur un
appareil ayant l'ancienne base.

### Avertissements de sécurité (comestibilité)

Trois garde-fous cumulatifs, tous **renforçants et jamais désactivants** :

- `domain/FungusChecker` et `domain/ToxicSpeciesChecker` complètent le jugement de l'IA par des
  listes locales — un candidat Pl@ntNet arrive toujours avec `toxic == null`, sans la liste locale
  l'hypothèse concurrente ne déclencherait jamais d'alerte.
- `IdentificationResult.toxicConfusionWarningText()` produit **le** texte de l'avertissement, utilisé
  identiquement par `ui/result/ToxicConfusionBanner`, `util/PdfExporter` et `util/ShareHelper` : une
  fiche partagée ne doit jamais être plus rassurante que la fiche à l'écran.
- Les deux seuils de déclenchement sont réglables dans les Paramètres et persistés en clair par
  `data/prefs/SafetySettingsStore` (bornés à l'écriture **et** à la lecture).

### UI partagée Résultat / Détail

`ui/result/IdentificationContent.kt` est le composable central réutilisé par `ResultScreen`
(juste après l'ID) et `DetailScreen` (depuis l'historique, avec en-tête « notes » et actions
partage/PDF/suppression). Toute évolution d'affichage de fiche se fait ici une seule fois.

### Carte & aire de répartition

`ui/map/SpeciesMap.kt` (osmdroid) accepte des coordonnées de capture **nullables** : avec un point
GPS, marqueur + rayon d'incertitude ; sans (photo importée sans EXIF), la carte se recentre sur la
boîte englobante des occurrences **GBIF** (`data/remote/gbif`, cache Room via `RangeRepository` +
`ui/map/MapViewModel`). `util/GeoUtils.convexHull` trace l'enveloppe.

### Questions à l'IA (Q&A)

`ui/qa/PlantQaSection.kt` (saisie clavier + dictée vocale via `RecognizerIntent`) → `PlantQaViewModel`
→ `data/repo/PlantQaRepository`, qui construit un prompt **avec le contexte de la plante et le lieu de
prise de vue** puis appelle `AiOrchestrator.ask()` (même ordre de repli que l'identification, mais
sans mise en file). Ajouter une capacité IA « texte seul » = étendre l'interface `AiProvider` et les
trois clients.

### Localisation & EXIF

`util/LocationProvider` fournit le GPS courant ; `util/ImageStorage.readExifLocation()` lit le géotag
d'une photo importée **avant compression** (la compression supprime l'EXIF). Pour une photo importée,
le lieu du fichier prime sur la position courante (voir `ui/capture/CaptureViewModel`).

## Journal de développement

Le dossier `log/` contient un fichier Markdown daté par lot de travail (demandes reçues, ce qui a été
fait, vérification). Y ajouter une entrée pour tout changement fonctionnel notable garde la trace des
décisions et des migrations.
