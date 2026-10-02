# Inscription Play Console — PlantInfo (réponses prêtes)

Ce fichier rassemble ce que la Console demande **en dehors des textes de la fiche** (ceux-ci vivent
dans [`play-store-listing.md`](play-store-listing.md)). Chaque réponse est justifiée : quand Google
resserre une règle, c'est la justification qu'il faut relire, pas la réponse.

Tout ce qui est marqué **(à décider)** dépend de vous et n'est pas déductible du code.

---

## 1. Identité de l'application

| Champ de la Console | Valeur |
| --- | --- |
| Nom de l'application | `PlantInfo` — **et pas** le nom de package. C'est ce champ qui affichait « com.plantinfo » sur l'ancienne entrée. |
| Nom du package | `ch.electromel.plantinfo` — définitif, il ne se change plus. |
| Type | Application (pas un jeu) |
| Gratuite ou payante | Gratuite — **irréversible** : une app gratuite ne peut jamais devenir payante. |
| Catégorie | Éducation (2ᵉ choix : Style de vie) |
| Tags | plantes, jardinage, nature, identification, botanique |
| E-mail de contact | reynard.michel@gmail.com (le même que dans la politique de confidentialité) |
| Site web | https://github.com/electromel/PlantInfo **(à décider :** un dépôt public comme site officiel convient, mais il montre le code à l'utilisateur final.**)** |
| Politique de confidentialité | https://electromel.github.io/PlantInfo/privacy-policy.html |
| Pays de diffusion | **(à décider)** — au minimum les pays des cinq langues : France, Suisse, Belgique, Royaume-Uni, Irlande, Allemagne, Autriche, Italie, Espagne. |

## 2. Visuels

| Élément | Fichier | Format exigé |
| --- | --- | --- |
| Icône | `app/src/main/ic_launcher-playstore.png` | 512 × 512, PNG 32 bits avec alpha ✔ |
| Bandeau (feature graphic) | `store/feature-graphic/<langue>.png` | 1024 × 500, PNG 24 bits ✔ |
| Captures téléphone | `store/screenshots/<langue>/01…06` | 1080 × 1920 (9:16), 6 par langue ✔ |

Langues fournies : `fr`, `en`, `de`, `it`, `es`. La Console accepte un jeu de visuels **par langue** :
téléversez le dossier correspondant sous chaque fiche traduite.

Ni tablette ni Android TV ne sont fournis : la fiche reste valable, l'app apparaîtra simplement
comme « non optimisée pour les grands écrans ».

Regénération : `python tools/render_store_assets.py` (voir l'en-tête du script pour la provenance
des captures brutes) et `python tools/render_playstore_icon.py` pour l'icône.

## 3. Classification du contenu (questionnaire IARC)

Catégorie choisie : **Utilitaire, productivité, communication ou autre**.

| Question | Réponse | Pourquoi |
| --- | --- | --- |
| Violence, sexualité, langage grossier, drogues | Non | Aucun contenu de ce type. |
| Jeux d'argent / simulation de jeu | Non | — |
| Achats numériques | Non | Aucun achat intégré. |
| L'app permet-elle d'échanger avec d'autres utilisateurs ? | Non | Aucun réseau social, aucun partage vers un service de l'éditeur. |
| L'app partage-t-elle la position de l'utilisateur avec d'autres utilisateurs ? | Non | La position sert de critère d'identification et reste dans la fiche locale. |
| Contenu généré par les utilisateurs diffusé à d'autres | Non | L'historique est local. |

Attendu : **PEGI 3 / ESRB Everyone**. L'avertissement de toxicité n'élève pas la classification : ce
n'est pas un contenu choquant mais une consigne de sécurité.

## 4. Public cible et contenu

| Question | Réponse | Pourquoi |
| --- | --- | --- |
| Tranches d'âge visées | **18 ans et plus** uniquement | L'app affiche des informations de comestibilité. Cocher une tranche < 13 ans déclencherait les règles « Familles » (consentement parental, publicité restreinte, audit du contenu tiers) alors que les fiches sont rédigées par une IA et donc non vérifiables a priori. |
| L'app attire-t-elle involontairement les enfants ? | Non | Interface sobre, pas de personnages ni de récompenses. |
| Application gouvernementale | Non | |
| Fonctionnalités financières | Non | Aucun paiement ; la ligne « coût de l'identification » n'est qu'une estimation en jetons. |
| Application de santé | Non | Aucun diagnostic médical, aucune donnée de santé. Le texte de la fiche renvoie explicitement à un expert. |
| Publicités | **L'app ne contient pas de publicité** | Aucun SDK publicitaire. |
| Achats intégrés | Non | |
| Application VPN, prêt d'argent, suivi (stalkerware) | Non | |

## 5. Sécurité des données

Rappel de ce que fait vraiment l'app (c'est la seule base honnête pour ce formulaire) :

- l'historique, les photos et les clés API restent sur l'appareil (Room + stockage privé +
  `EncryptedSharedPreferences`) ;
- **aucun serveur de l'éditeur** : rien n'est envoyé à Electromel, il n'y a pas de compte ;
- à chaque identification, la photo part chez **Pl@ntNet** (sans aucune position) puis chez le
  **fournisseur d'IA** que l'utilisateur a configuré (Gemini par défaut ; neuf possibles, dont certains
  hors de Suisse et de l'UE) ; le lieu de prise de vue part avec elle chez le fournisseur d'IA seul,
  **arrondi à environ 1 km** (deux décimales). **GBIF** ne reçoit que le nom de l'espèce, et les tuiles
  **OpenStreetMap** révèlent la zone de carte affichée ;
- une fois par jour au plus, une requête minimale (« ping », sans photo ni lieu) vérifie chaque clé
  saisie auprès de son fournisseur ;
- ces envois ne surviennent **que sur action de l'utilisateur** (appui sur « Identifier » ou sur
  « Demander ») — hors la vérification quotidienne des clés — et ne sont pas conservés côté éditeur ;
- le partage et l'export PDF d'une fiche, déclenchés par l'utilisateur vers le destinataire de son choix,
  contiennent le lieu à pleine précision (arrondi pour une espèce protégée) : ce n'est pas un partage
  par l'éditeur.

Réponses recommandées :

| Question | Réponse |
| --- | --- |
| Votre app collecte-t-elle ou partage-t-elle des données utilisateur ? | **Oui** |
| Toutes les données sont-elles chiffrées en transit ? | **Oui** (HTTPS pour toutes les API ; les tuiles OSM le sont aussi) |
| Proposez-vous un moyen de supprimer les données ? | **Oui** — suppression d'une fiche ou de tout l'historique dans l'app ; aucune donnée côté éditeur, donc aucun formulaire de suppression de compte à fournir. |

Types de données à déclarer :

| Type | Collectée | Partagée | Obligatoire | Finalité |
| --- | --- | --- | --- | --- |
| **Photos** | Oui | Oui (Pl@ntNet, fournisseur d'IA) | Oui | Fonctionnalité de l'app |
| **Position approximative** | Oui | Oui (fournisseur d'IA seul : lieu arrondi à ~1 km, soit moins de 3 km²) | Non (l'app fonctionne sans) | Fonctionnalité de l'app |
| **Position précise** | Oui (GPS, EXIF) | **Non** : elle reste sur l'appareil ; seule sa version arrondie part | Non | Fonctionnalité de l'app |
| **Autres actions utilisateur** (la question libre posée à l'IA) | Oui | Oui (fournisseur d'IA) | Non | Fonctionnalité de l'app |

Pour chacun, cocher **« Traitement éphémère »** : les données transitent pour produire la réponse et
ne sont pas stockées par l'éditeur. Ne rien déclarer d'autre — pas d'identifiants, pas de données
personnelles, pas de diagnostics, pas d'historique de navigation, aucun SDK analytique.

> Google admet une exception de déclaration pour un transfert « déclenché par une action explicite de
> l'utilisateur ». Elle s'appliquerait ici, mais **déclarer reste le bon choix** : une sous-déclaration
> est un motif de suspension, une sur-déclaration n'en est pas un.

## 6. Autorisations

Aucune autorisation « sensible » au sens de la Console : ni `MANAGE_EXTERNAL_STORAGE`, ni
`QUERY_ALL_PACKAGES`, ni SMS/journal d'appels, ni localisation en arrière-plan, ni accessibilité.
**Aucun formulaire de déclaration d'autorisation n'est donc à remplir.**

Ce que le manifeste demande, et la justification à donner si la Console la réclame :

| Autorisation | Justification |
| --- | --- |
| `CAMERA` | Photographier la plante à identifier. |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Critère d'identification complémentaire et centrage de la carte. Au premier plan uniquement. |
| `ACCESS_MEDIA_LOCATION` + `READ_MEDIA_IMAGES` + `READ_MEDIA_VISUAL_USER_SELECTED` | Lire le géotag EXIF d'une photo importée, avant compression. Demandées au bouton Galerie seulement ; refusées, le sélecteur de documents s'ouvre quand même (vérifié sur un Pixel 7a, Android 17) — la lecture du géotag, elle, n'a pas été testée sans permission. `READ_MEDIA_VISUAL_USER_SELECTED` permet l'accès partiel d'Android 14+. |

Chaque autorisation est demandée **au moment où elle sert** (caméra et position à l'ouverture de
l'appareil photo, photos au bouton Galerie, notifications au lancement d'une identification), jamais
à l'ouverture de l'application.

> **Point de vigilance.** La politique Play sur les autorisations photo et vidéo réserve
> `READ_MEDIA_IMAGES` aux apps dont la fonction centrale est l'accès aux médias ; pour un import
> ponctuel, Google attend le sélecteur de photos. L'app importe déjà par le sélecteur de documents, et
> la permission ne sert qu'à lire le géotag. Si la Console demande une déclaration, la justification
> ci-dessus est la bonne ; si elle la refuse, la permission peut être retirée : l'import lui-même
> n'en dépend pas, mais le lieu des photos importées serait probablement perdu. **À valider sur un
> appareil avec une photo géotaggée avant de la retirer** — ce n'est pas vérifié.
| `POST_NOTIFICATIONS` | Prévenir quand une identification mise en file hors-ligne a abouti. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Appels aux API d'identification. |

## 7. Piste de publication

Le compte développeur est **personnel et récent** : Google exige un **test fermé d'au moins
12 testeurs pendant 14 jours consécutifs** avant d'autoriser la demande d'accès à la production.

1. Test interne (jusqu'à 100 testeurs, immédiat) — sert à valider le bundle.
2. Test fermé ≥ 12 testeurs, 14 jours — le compteur ne tourne que si les testeurs restent inscrits.
3. Demande d'accès à la production, puis examen (quelques jours).

Chaque release demande : le bundle `.aab`, les **notes de version par langue**
(`play-store-listing.md`), et un `versionCode` strictement supérieur au précédent.

## 8. Avant de téléverser — vérifications

```powershell
$jbr = "C:\Program Files\Android\Android Studio\jbr"
& C:\DEV\PlantInfo\gradlew.bat -p C:\DEV\PlantInfo "-Dorg.gradle.java.installations.paths=$jbr" bundleRelease
```

- [ ] `versionCode` incrémenté dans `app/build.gradle.kts` (6 pour 0.5.0 — déjà publié).
- [ ] `targetSdk` ≥ 36, sinon la Console refuse le bundle au téléversement.
- [ ] Bundle signé avec `keystore.jks` (clé d'**upload** ; Google détient la clé de signature).
- [ ] Aucune clé API dans le binaire : les `buildConfigField` de clés ne sont surchargés qu'en debug.
- [ ] Textes de la fiche à jour dans les cinq langues.
- [ ] Visuels téléversés pour chaque langue traduite.
