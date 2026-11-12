# 🎯 Target

Application Android qui chasse les meilleures affaires sur Vinted à partir de tes références (photos, styles, marques, pièces).

## Fonctionnalités

| Onglet | Ce qu'il fait |
|---|---|
| **Réfs** | Plusieurs profils (« Moi », « Ma sœur »…). Pour chacun : photos de réf analysées par une IA embarquée (type de pièce, couleurs, marque lue sur l'étiquette, empreinte visuelle), mots-clés de style (*Old money*, *Streetwear*…), de marque (*Ralph Lauren*…) ou de pièce (*Knit*…), filtres prix / tailles. |
| **Recherche** | « Scanner mes réfs » ou barre de recherche libre. Chaque recherche est déclinée en dizaines de variantes (synonymes, fautes corrigées, **18 langues Vinted**), lancées en parallèle, fusionnées, dédoublonnées puis triées par attractivité : rapport valeur / prix, fraîcheur, état, popularité et ressemblance visuelle avec tes photos. |
| **Deals** | Scan en arrière-plan de tous les profils. Notification « Deal » quand un article est vraiment sous sa valeur (vraie marque déclarée, bon état, prix anormalement bas mais pas « trop beau pour être vrai »). Désactivable dans les réglages. |
| **Réglages** | Compte Vinted, pays, sensibilité et fréquence des deals, retours haptiques, mises à jour, version de l'app. |

Fiche produit : photos, prix avec protection acheteur, valeur estimée, jauge prix/valeur, description, et bouton **« Aller sur Vinted »** qui ouvre l'app Vinted directement sur l'annonce.

## Mises à jour automatiques

À chaque démarrage, Target interroge en arrière-plan la dernière *release* GitHub de ce dépôt. Si une version plus récente existe, elle est téléchargée, vérifiée puis installée :

- téléchargement HTTPS depuis `github.com` uniquement ;
- vérification de l'empreinte SHA-256 publiée avec l'APK ;
- vérification que l'APK a **le même nom de paquet et le même certificat de signature** que l'app installée (Android l'impose aussi) ;
- installation via `PackageInstaller` (sans confirmation sur Android 12+ une fois que Target s'est mise à jour elle-même une première fois).

La première fois, Android demande d'autoriser Target à installer des applications : une bannière dans l'app y emmène directement.

### Configuration (une seule fois)

La CI (`.github/workflows/android.yml`) teste le moteur, compile l'APK signé et publie une release à chaque push sur la branche par défaut. Elle a besoin de deux secrets (*Settings → Secrets and variables → Actions → New repository secret*) :

| Nom | Valeur |
|---|---|
| `TARGET_KEYSTORE_B64` | le keystore de signature encodé en base64 |
| `TARGET_KEYSTORE_PASSWORD` | son mot de passe (alias de clé : `target`) |

⚠️ Toutes les versions doivent être signées avec **la même clé**, sinon Android refuse la mise à jour. Ne la perds pas et ne la commite jamais.

## Sécurité

- `targetSdk 35`, trafic en clair interdit (`network_security_config`), sauvegardes cloud désactivées ;
- seul l'écran principal est exporté ; `PendingIntent` immuables (sauf celui exigé par `PackageInstaller`, explicite et non exporté) ;
- les photos de réf sont analysées **sur le téléphone** (ML Kit + MediaPipe) et ne sont jamais envoyées ;
- la connexion Vinted se fait sur le site de Vinted dans une WebView limitée aux domaines Vinted ; Target ne voit jamais le mot de passe ;
- release minifiée (R8), signée v2 + v3.

## Architecture

```
engine/   Kotlin pur, testé (JUnit) : dictionnaire 18 langues, marques, styles, analyse de requête
          (avec correction des fautes), génération des variantes, scoring, détection de deals,
          parsing JSON Vinted, comparaison de versions.
app/      Android / Jetpack Compose : client Vinted (OkHttp + session WebView partagée),
          analyse d'image, WorkManager (deals), mise à jour, interface.
```

Le seuil de deal est **adaptatif** : valeur attendue = mélange de la médiane des annonces comparables (même marque, même type de pièce, prix normalisés par l'état) et de la cote de la marque, pondéré par le nombre de comparables. Un deal exige un prix ≤ ~42 % de cette valeur (selon la gamme et la sensibilité), une économie d'au moins 8 €, un z-score robuste (log-prix, médiane/MAD) ≥ 2,4 sur le marché observé, une vraie marque déclarée, un bon état et une annonce sans « style / inspiré ». En simulation (`DealSimulationTest`) : 100 % des vraies affaires injectées détectées, 0,3 à 0,7 % de fausses alertes.

```bash
./gradlew :engine:test            # tests du moteur
./gradlew :app:testDebugUnitTest  # captures d'écran Robolectric -> app/build/screens
./gradlew :app:assembleRelease    # APK (signé si TARGET_KEYSTORE* sont définis)
```

*Application indépendante, non affiliée à Vinted.*
