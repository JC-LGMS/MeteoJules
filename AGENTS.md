# MétéoJules — notes pour les agents

Application Android météo (Kotlin + Jetpack Compose). Affiche la température du jour et de la semaine, et conseille une tenue (t-shirt, pull, manteau…) selon la température actuelle. Pull-to-refresh, changement de ville via crayon ✏️, réglages via engrenage ⚙️ : fond d'écran (couleur ou photo galerie) et photo personnalisée par tenue.

## Projet

- package / applicationId : `com.meteojules`
- activité launcher : `com.meteojules.MainActivity`
- compileSdk / targetSdk 34, minSdk 24
- AGP 8.5.2, Kotlin 2.0.20, Gradle 8.7, JDK 17
- Compose BOM 2024.10.00, Material3
- API météo : Open-Meteo (`https://api.open-meteo.com`), gratuit, **pas de clé API requise**
- Coordonnées par défaut : Paris (48.8566, 2.3522), fuseau `Europe/Paris`

## Chaîne d'outils (machine-specific)

`gradle` **n'est pas dans le PATH**. Utiliser les copies déjà présentes :

| Outil | Chemin |
|---|---|
| JDK 17 | `C:\Users\jc_lg\AppData\Local\Temp\opencode\jdk17` |
| Gradle 8.7 | `C:\Users\jc_lg\AppData\Local\Temp\opencode\tools\gradle-8.7\bin\gradle.bat` |
| Android SDK | `C:\Users\jc_lg\AppData\Local\Android\Sdk` (cf. `local.properties`) |
| adb | `C:\Users\jc_lg\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| emulator | `C:\Users\jc_lg\AppData\Local\Android\Sdk\emulator\emulator.exe` (AVD **Pixel_10**) |

Ces outils sont hors du dépôt : s'ils disparaissent, les retélécharger.

## Préambule PowerShell (avant tout appel Gradle)

```powershell
$env:JAVA_HOME = "C:\Users\jc_lg\AppData\Local\Temp\opencode\jdk17"
$env:PATH = "$env:JAVA_HOME\bin;$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:LOCALAPPDATA\Android\Sdk\emulator;$env:PATH"
$g = "C:\Users\jc_lg\AppData\Local\Temp\opencode\tools\gradle-8.7\bin\gradle.bat"
$adb = "C:\Users\jc_lg\AppData\Local\Android\Sdk\platform-tools\adb.exe"
```

## Compilation

```powershell
& $g :app:compileDebugKotlin --console=plain   # vérif rapide après une modif
& $g :app:assembleDebug --console=plain        # APK debug
```

APK produit : `app/build/outputs/apk/debug/app-debug.apk`.

## Release (APK signé)

- Build : `& $g :app:assembleRelease` → **`app/build/outputs/apk/release/app-release.apk`** (signé, installable).
- Signature : keystore **`meteojules-release.keystore`** (racine du projet), alias `meteojules` — mots de passe dans **`keystore.properties`** (racine), lu par `app/build.gradle.kts` (`rootProject.file`). Ne pas committer les mots de passe.
- ⚠️ **Sauvegarder `meteojules-release.keystore` + `keystore.properties`** : sans eux, impossible de publier une mise à jour (refus de signature).
- ⚠️ Installer la release sur un appareil où tourne la debug exige de **désinstaller** d'abord (signatures différentes) → les réglages de l'app (photos, notifications, couleurs) sont perdus.
- Vérifier la signature (JAVA_HOME doit être positionné) :
  `& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk`
- Certificat attendu : `CN=Jules Luthner, O=MeteoJules, C=FR`, SHA-256 `826ba5ca9ad671e32e37125947f6d77eaf32fe91a47acaf7f06d333ed5e6f475`.
- Le keystore a été généré avec le keytool du JBR d'Android Studio (la copie JDK de l'agent n'a pas `keytool.exe`) :
  `& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore meteojules-release.keystore -alias meteojules -keyalg RSA -keysize 2048 -validity 10000 -storepass ... -keypass ... -dname "CN=Jules Luthner, O=MeteoJules, C=FR"`
- Minification **activée** (`isMinifyEnabled = true`) : règles R8 dans `app/proguard-rules.pro` (Retrofit : annotations + méthodes d'interface ; Gson : champs de `com.meteojules.model.**` conservés).
- Fichier de mapping pour dé-obfusquer les crashes : `app/build/outputs/mapping/release/mapping.txt` (à sauvegarder avec chaque release).
- Vérif post-build (Gson lit les champs par réflexion) : les champs des modèles doivent garder leur nom dans le DEX :
  `dexdump.exe classes.dex` → champs `temperature`, `weather_code`… présents dans `Lcom/meteojules/model/*;` (les classes d'annotation Retrofit, elles, peuvent être renommées : c'est sûr, Retrofit les retrouve par objet Class).

## Émulateur et installation

L'émulateur `Pixel_10` est déjà configuré (AVD `Pixel_10`).

```powershell
# Démarrer l'émulateur (si besoin)
& "C:\Users\jc_lg\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd Pixel_10

# Vérifier les appareils
& $adb devices

# Installer l'APK sur l'émulateur Pixel_10
& $adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

# Lancer l'app
& $adb -s emulator-5554 shell am start -n com.meteojules/.MainActivity
```

## Conseil vestimentaire (dans `ClothingAdvice.kt`)

- >= 20 °C → T-shirt ☀️
- 10 à 19 °C → Pull léger / manches longues 🧥
- 0 à 9 °C → Pull + veste 🧣
- < 0 °C → Manteau, écharpe, bonnet ❄️

`ClothingAdvice.getCategoryIndex(temp: Double)` renvoie l'indice de catégorie 0..3 (même ordre). Il sert à associer la photo personnalisée à la tenue courante.

### Photos personnalisées par tenue

- Choix dans le dialogue Réglages (⚙️) → section « Photos des conseils vestimentaires » (4 catégories).
- Stockage : SharedPreferences `meteojules_settings`, clés `advice_image_uri_0` … `advice_image_uri_3` (`WallpaperSettings.kt`).
- État : `WeatherViewModel.adviceImageUris: StateFlow<Map<Int, String>>` (+ `setAdviceImageUri(category, uri)`).
- Affichage : à droite de la température et du conseil écrit (96 dp, coins arrondis) ; placeholder emoji tant qu'aucune photo n'est choisie.

## Fonctionnalités UI (`ui/WeatherScreen.kt`)

- **Pull-to-refresh** : `PullToRefreshBox` de material3 1.3.0 (inclus dans le BOM 2024.10.00). `Modifier.refreshable` / `androidx.compose.foundation.refreshable` **n'existent pas** — ne pas chercher ces API. Le contenu principal est un unique `LazyColumn` : le geste s'appuie sur le nested scroll et exige un contenu scrollable (un `LazyColumn` imbriqué dans une `Column` scrollable crashe ; une `Column` non scrollable ne déclenche jamais le geste).
- **Changement de ville** : crayon ✏️ à côté du nom de la ville (pas de bouton dédié) → dialogue de recherche Nominatim.
- **Réglages** : engrenage ⚙️ en haut à gauche → dialogue (fond d'écran couleur/photo, photos des 4 tenues, couleur du texte, notifications quotidiennes, couleurs du fond).
- **Fond d'écran** : couleur unie (fallback toujours dessinée) puis photo galerie en plein écran par-dessus via `UriPhoto` (`ContentScale.Crop`).
- **Images** : composable `UriPhoto(uri, modifier, cornerRadius)` — décodage `BitmapFactory` en 2 passes (bounds puis `inSampleSize`) sur `Dispatchers.IO` via `produceState`. Utiliser `Bitmap.asImageBitmap()` (pas `toImageBitmap` qui n'existe pas) et `androidx.compose.ui.layout.ContentScale`.
- **Permissions galerie** : `ActivityResultContracts.OpenDocument` + `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`. `GetContent` donne une permission non persistante (URI morte au redémarrage).
- **Icônes du temps** : `WeatherIcons.iconFor(code)` (emoji par code WMO). Affichées à côté de la température actuelle (plus de libellé « Actuellement : ») et sur chaque ligne de prévisions.
- **Pluie dans les prévisions** : `daily.precipitation_probability_max` (Open-Meteo) → indicateur `💧xx%` affiché si probabilité >= 40 %.
- **Cartes prévisions** : fond semi-transparent (`Color.White.copy(alpha = 0.35f)`) pour laisser voir le fond d'écran.
- **Couleur du texte** : réglage « Couleur du texte » (6 pastilles), appliquée à tous les textes de l'écran principal. Clé `text_color` (défaut `#FFFFFF`), état `WeatherViewModel.textColor`.
- **Photo du conseil** : 128 dp, coins arrondis, à droite de la température et du conseil écrit.

## Notification quotidienne (`notifications/`)

- Un horaire **par jour de la semaine** (7 interrupteurs + sélecteur d'heure material3 `TimePicker`), chacun activable indépendamment.
- Stockage : clés `notification_enabled_<1..7>`, `notification_hour_<1..7>`, `notification_minute_<1..7>` (1 = lundi, cf. `java.time.DayOfWeek.value`).
- Planification : `WeatherNotificationScheduler.scheduleNext()` programme toujours la **prochaine occurrence** parmi les jours activés (`AlarmManager.setExactAndAllowWhileIdle`, fallback inexact si `SCHEDULE_EXACT_ALARMS` refusée), puis se replanifie après chaque déclenchement (`NotificationReceiver`) et au boot (`BootReceiver`).
- Contenu : titre `« 15,7 °C ☁️ »`, texte = conseil vestimentaire (+ `🌧️ Pluie probable (xx %)` si >= 40 %), **grande icône = photo personnalisée de la tenue du jour** (décodée via `decodeSampled`), ouvre l'app au clic.
- La météo est récupérée au moment du déclenchement pour les **dernières coordonnées utilisées** (clés `last_lat`/`last_lon`, mises à jour à chaque chargement météo ; défaut Paris).
- Permissions manifeste : `POST_NOTIFICATIONS` (demandée à l'activation d'un jour, Android 13+), `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. Receivers déclarés non exportés.
- Reprogrammation au lancement de l'app via le `init` de `WeatherViewModel`.

## Structure

- `app/src/main/java/com/meteojules/` : MainActivity, WeatherViewModel (états, photos de conseils, couleur du texte, notifications), WeatherRepository, WeatherApi, LocationRepository (FusedLocation, timeout 15 s), GeocodingRepository, GeocodingApi (Nominatim)
- `app/src/main/java/com/meteojules/model/` : WeatherResponse (dont `weather_code`, `precipitation_probability_max`), ClothingAdvice (+ `getCategoryIndex`), WeatherIcons (codes WMO → emoji), GeocodingResponse
- `app/src/main/java/com/meteojules/ui/` : WeatherScreen (Compose : pull-to-refresh, en-tête, dialogues Ville/Réglages/Heure, `UriPhoto`)
- `app/src/main/java/com/meteojules/settings/` : WallpaperSettings (SharedPreferences : couleur, URI fond, photos des tenues, couleur du texte, notifications par jour, dernières coordonnées)
- `app/src/main/java/com/meteojules/notifications/` : WeatherNotifications (DayNotification + planificateur + notification), NotificationReceiver, BootReceiver
- `app/src/main/res/` : strings, colors, themes, manifeste
- `build.gradle.kts` (racine) : plugins AGP 8.5.2, Kotlin 2.0.20, Compose Compiler
- `app/build.gradle.kts` : Compose BOM 2024.10.00 (material3 1.3.0), Material Components, Retrofit, Coroutines
- `settings.gradle.kts` : configuration des repositories
- `local.properties` : chemin du SDK Android

## Notes

- **Imports Compose** : utiliser les wildcards `androidx.compose.foundation.*` et `androidx.compose.foundation.layout.*` — les imports individuels fragmentés provoquent des « Unresolved reference » récurrents sur `size`, `height`, `weight`, `background`, `clickable`, `border`.
- La copie JDK (`…\opencode\jdk17`) est incomplète : il y manque `keytool.exe` et il manquait `jmod.exe` (copié depuis `C:\Program Files\Android\Android Studio\jbr\bin`). Si Gradle échoue avec « jmod executable … does not exist », recopier les outils manquants depuis le JBR.
- Le rafraîchissement (`loadWeatherWithLocation`) attend d'abord le GPS (timeout 15 s) avant l'appel Open-Meteo : le pull-to-refresh peut donc prendre quelques secondes.
- L'émulateur résout sa position GPS par défaut sur Mountain View (37.42, -122.08) : afficher « Mountain View » est le comportement attendu, pas un bug.
- Les « Fatal signal 6 » de logcat proviennent des services système de l'émulateur (uwb, nfc, android.hardware…), pas de `com.meteojules`.
- Les icônes de lanceur (`mipmap/ic_launcher`) sont référencées dans le manifeste mais non générées dans ce dépôt. Les générer depuis Android Studio (Image Asset Studio) ou supprimer les références si non nécessaires.
- Aucune clé API n'est requise, mais l'appareil/émulateur doit avoir accès à Internet.
- Le `package="com.meteojules"` dans `AndroidManifest.xml` est déprécié (le `namespace` du build.gradle.kts est utilisé) — à nettoyer ultérieurement.
