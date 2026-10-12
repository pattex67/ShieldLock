# ShieldLock

**Un code parental à 4 chiffres pour les applis de la NVIDIA Shield et d'Android TV.**

Android TV ne permet pas de mettre un code sur YouTube, Netflix ou le Play Store.
ShieldLock le fait : choisissez les applis à protéger, elles demanderont le code à chaque ouverture.
Pensée pour la télécommande, elle fonctionne hors ligne, sans pub, sans pistage et sans accès à Internet.

[English version](README.md)

<p align="center">
  <img src="docs/pin-youtube.png" width="720" alt="Écran du code devant YouTube">
</p>

## Fonctions

- **Verrou par appli** avec un code à 4 chiffres, saisi avec les flèches de la télécommande ou les touches chiffres.
- **Assistant au premier lancement** : choix du code, choix des applis, activation du service.
- **Pré-sélection automatique** : environ 75 applis connues de streaming, IPTV, navigateurs, magasins et sideload sont cochées d'office (YouTube, Netflix, Disney+, Prime Video, Crunchyroll, Twitch, Plex, Kodi…), ainsi que les Paramètres et l'installateur d'applis de l'appareil.
- **Anti-contournement** : les Paramètres peuvent être verrouillés pour qu'on ne puisse pas couper le service ; verrouiller le Play Store et l'installateur empêche d'installer de nouvelles applis.
- **Reverrouillage automatique** au retour à l'accueil, à l'ouverture d'une autre appli ou à la mise en veille.
- **Anti-essais en série** : après 5 mauvais codes, la saisie est bloquée 30 s (jusqu'à 10 min).
- **Réglages protégés par le même code** : verrouiller ou libérer chaque appli installée, changer le code.
- En français et en anglais.

## Compatibilité

- NVIDIA Shield TV (2015, 2017, 2019 tube et Pro), Android TV 8.0 ou plus récent.
- Devrait fonctionner sur les autres boîtiers Android TV / Google TV (Chromecast avec Google TV, Mi Box, Sony, Philips…).
  Les retours sont bienvenus dans les issues.

## Installation

### Option 1 : depuis un ordinateur (recommandé)

Le script active aussi le service d'accessibilité, ce que certains appareils refusent depuis la TV pour une appli installée hors Play Store.

1. Sur la TV : **Paramètres › Préférences de l'appareil › À propos**, cliquez 7 fois sur **Build** pour débloquer les options pour les développeurs,
   puis activez **Options pour les développeurs › Débogage réseau** (ou débogage USB). Notez l'adresse IP.
2. Sur l'ordinateur : installez les [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`)
   et téléchargez `ShieldLock-x.y.z.apk` depuis la page [Releases](../../releases).
3. Lancez le script d'installation (acceptez la demande de débogage sur la TV la première fois) :

   ```powershell
   # Windows
   .\scripts\install.ps1 -Ip 192.168.1.42 -Apk .\ShieldLock-1.0.0.apk
   ```
   ```bash
   # macOS / Linux
   ./scripts/install.sh 192.168.1.42 ./ShieldLock-1.0.0.apk
   ```

4. Terminez l'assistant sur la TV, puis vous pouvez désactiver le débogage réseau.

### Option 2 : uniquement sur la TV

Installez l'APK (par exemple avec l'appli *Downloader*), ouvrez **ShieldLock** et suivez l'assistant.
À l'étape 3, activez **ShieldLock** dans **Paramètres › Préférences de l'appareil › Accessibilité**.

## Fonctionnement

ShieldLock est un [service d'accessibilité](https://developer.android.com/guide/topics/ui/accessibility/service) :
il est prévenu quand une appli passe au premier plan et, si elle est verrouillée, affiche l'écran du code par-dessus.
Le bon code déverrouille cette appli jusqu'à ce qu'on la quitte. Le code est stocké sous forme d'empreinte SHA-256 salée, uniquement sur l'appareil.

L'appli ne demande pas l'accès à Internet et n'envoie rien nulle part.

## Limites

ShieldLock est un garde-fou pour les enfants, pas un produit de sécurité :

- Quelqu'un qui a un **accès ADB** peut le désactiver. Coupez le débogage réseau/USB après l'installation.
- Si les **Paramètres** ne sont pas verrouillés, on peut y couper le service ou désinstaller l'appli.
- Les applis installées **après** la configuration ne sont pas verrouillées d'office : cochez-les dans les réglages de ShieldLock
  (verrouiller le Play Store et l'installateur empêche les autres d'installer des applis).
- Les aperçus vidéo lancés automatiquement sur l'écran d'accueil ne sont pas bloqués, seules les applis le sont.

## Compiler

Voir la section [Build from source](README.md#build-from-source) du README anglais.

## Licence

[MIT](LICENSE).

ShieldLock est un projet indépendant, sans lien avec NVIDIA ou Google.
NVIDIA et SHIELD sont des marques de NVIDIA Corporation ; Android TV est une marque de Google LLC.
