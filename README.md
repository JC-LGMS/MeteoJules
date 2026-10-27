# MétéoJules 🌤️

Application météo Android simple et personnalisable, conçue pour Jules.

![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.00-4285F4?logo=android&logoColor=white)
![Android](https://img.shields.io/badge/Android%20API-24%2B-3DDC84?logo=android&logoColor=white)

## ✨ Fonctionnalités

- 🌍 **Météo géolocalisée** — température actuelle, conseil vestimentaire et prévisions de la semaine (Open-Meteo, sans clé API)
- 🏙️ **Changement de ville** — recherche manuelle via le crayon ✏️ (Nominatim / OpenStreetMap)
- 🔄 **Pull-to-refresh** — glissez vers le bas pour rafraîchir
- 🖼️ **Fond d'écran personnalisable** — couleur unie ou photo de la galerie
- 👕 **Photo par tenue vestimentaire** — associez une image à chaque conseil (t-shirt, pull, manteau…), affichée à côté de la température
- 🎨 **Couleurs personnalisables** — couleur du texte, couleur du fond des prévisions (+ pastille arc-en-ciel pour n'importe quelle couleur) et slider de transparence
- 🔔 **Notification quotidienne** — un horaire réglable pour chaque jour de la semaine, avec la météo, le conseil vestimentaire et la photo de la tenue
- ⛅ **Icônes du temps** (soleil, nuage, pluie, neige, brouillard, orage…) sur la météo actuelle et chaque prévision + indicateur de pluie 💧

## 🛠️ Technologies

| | |
|---|---|
| Langage | Kotlin 2.0.20 |
| Interface | Jetpack Compose (BOM 2024.10.00), Material 3 |
| SDK | min 24 / target 34 |
| Réseau | Retrofit 2 + OkHttp, Gson |
| Géolocalisation | Google Play Services (FusedLocationProvider) |
| Notifications | AlarmManager + NotificationCompat |
| Persistance | SharedPreferences |
| Minification | R8 activé en release |

## 🚀 Compilation

Prérequis : JDK 17, Android SDK 34.

```bash
# Debug
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release signée et minifiée
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

> ⚠️ Le keystore (`meteojules-release.keystore`) et `keystore.properties` sont **volontairement hors du dépôt** : sauvegardez-les séparément, ils sont indispensables pour signer les mises à jour.

## 🗂️ Structure

```
app/src/main/java/com/meteojules/
├── MainActivity.kt          # Point d'entrée
├── WeatherViewModel.kt      # États UI, réglages (couleurs, photos, notifications)
├── WeatherRepository.kt     # API Open-Meteo
├── LocationRepository.kt    # Géolocalisation (FusedLocation, timeout 15 s)
├── GeocodingRepository.kt   # Nominatim (recherche + géocodage inverse)
├── model/                   # WeatherResponse, ClothingAdvice, WeatherIcons…
├── notifications/           # Alarmes quotidiennes + notification + BootReceiver
├── settings/                # SharedPreferences (fond, couleurs, photos, horaires)
└── ui/                      # WeatherScreen (Compose)
```

## 🔑 Configuration

- **Aucune clé API requise** : Open-Meteo et Nominatim sont gratuits et ouverts.
- L'appareil doit avoir accès à Internet et, pour la géolocalisation, la permission de localisation accordée.
- Sans géolocalisation, repli sur Paris (48.8566, 2.3522).

## 👤 Auteur

**Jules Luthner** — [JC-LGMS](https://github.com/JC-LGMS)
