package com.meteojules.ui

import android.Manifest.permission
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meteojules.BuildConfig
import com.meteojules.R
import com.meteojules.WeatherViewModel
import com.meteojules.model.CityResult
import com.meteojules.model.ClothingAdvice
import com.meteojules.model.WeatherIcons
import com.meteojules.notifications.DayNotification
import com.meteojules.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Catégorie de conseil en cours de sélection dans le dialogue (0..3)
    var pickingAdviceCategory by remember { mutableStateOf<Int?>(null) }
    // Jour de notification en attente de permission (1..7)
    var pendingNotificationDay by remember { mutableStateOf<Int?>(null) }
    // Jour dont on modifie l'horaire de notification (1..7) ou null
    var timePickerDay by remember { mutableStateOf<Int?>(null) }
    // Sélecteur de couleur libre : "wallpaper" | "text" | "card" ou null
    var colorPickerTarget by remember { mutableStateOf<String?>(null) }

    // Lanceur de sélection de photo de fond (document persistant)
    val pickWallpaperLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Le fournisseur n'a pas accordé de permission persistante : l'URI reste utilisable cette session
            }
            viewModel.setWallpaperImageUri(uri.toString())
        }
    }

    // Lanceur de sélection de photo pour un conseil vestimentaire
    val pickAdviceLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            val category = pickingAdviceCategory
            if (category != null) {
                viewModel.setAdviceImageUri(category, uri.toString())
            }
        }
        pickingAdviceCategory = null
    }

    // Lanceur de permission de notification (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        val day = pendingNotificationDay
        if (granted && day != null) {
            viewModel.setNotificationDayEnabled(day, true)
        }
        pendingNotificationDay = null
    }

    // Lanceur de demande de permission de géolocalisation
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        RequestMultiplePermissions()
    ) { granted ->
        val fineGranted = granted[permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            viewModel.loadWeatherWithLocation()
        } else {
            viewModel.loadWeather() // fallback Paris
        }
    }

    // Vérifie la permission et lance le chargement après l'initialisation
    var permissionChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!permissionChecked) {
            permissionChecked = true
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                viewModel.loadWeatherWithLocation()
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(
                        permission.ACCESS_FINE_LOCATION,
                        permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    // Charge le fond d'écran et les photos de conseils sauvegardés au lancement
    LaunchedEffect(Unit) {
        viewModel.loadSavedWallpaperImage()
        viewModel.loadSavedAdviceImages()
    }

    // État du dialogue de changement de ville (ouvert via le crayon ✏️)
    var showCityDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // État du dialogue de réglages
    var showAppearanceDialog by remember { mutableStateOf(false) }

    val state by viewModel.weatherState.collectAsState()
    val loading by viewModel.isLoadingState.collectAsState()
    val error by viewModel.errorState.collectAsState()
    val cityName by viewModel.cityName.collectAsState()
    val isUsingGps by viewModel.isUsingGps.collectAsState()
    val lat by viewModel.lat.collectAsState()
    val lon by viewModel.lon.collectAsState()
    val wallpaperImageUri by viewModel.wallpaperImageUri.collectAsState()
    val adviceImages by viewModel.adviceImageUris.collectAsState()
    val textColorHex by viewModel.textColor.collectAsState()
    val wallpaperColorHex by viewModel.wallpaperColor.collectAsState()
    val cardColorHex by viewModel.cardColor.collectAsState()
    val cardOpacity by viewModel.cardOpacity.collectAsState()
    val notificationSettings by viewModel.notificationSettings.collectAsState()

    val textColor = hexToColor(textColorHex)

    // Date du jour : nom du jour + mois (ex: "Lundi 26 octobre")
    val todayDate = remember {
        ZonedDateTime.now(ZoneId.of("Europe/Paris"))
            .format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE))
            .capitalize()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fond d'écran : couleur unie (toujours dessinée, sert de fallback si la photo échoue)
        Box(modifier = Modifier.fillMaxSize().background(hexToColor(wallpaperColorHex)))
        // Photo de galerie par-dessus si définie
        if (wallpaperImageUri != null) {
            UriPhoto(uri = wallpaperImageUri!!, modifier = Modifier.fillMaxSize())
        }

        // Pull-to-refresh : glisser vers le bas pour rafraîchir
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { viewModel.loadWeatherWithLocation() },
            modifier = Modifier.fillMaxSize()
        ) {
            // Un seul LazyColumn pour que le geste fonctionne partout sur l'écran
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // En-tête : engrenage à gauche + ville (avec crayon ✏️) / date / coordonnées
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAppearanceDialog = true }) {
                            Text(
                                text = "⚙️",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayLocation(cityName, isUsingGps),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = textColor,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                IconButton(onClick = { showCityDialog = true }) {
                                    Text(
                                        text = "✏️",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Text(
                                text = todayDate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.8f)
                            )
                            if (isUsingGps) {
                                Text(
                                    text = "%.4f, %.4f".format(lat ?: 0.0, lon ?: 0.0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                if (loading) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (error != null) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val currentState = state
                    if (currentState == null) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(text = stringResource(R.string.loading), color = textColor)
                        }
                    } else {
                        val current = currentState.current_weather
                        if (current != null) {
                            item {
                                // Icône du temps + température à gauche, photo de la tenue à droite
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = WeatherIcons.iconFor(current.weathercode),
                                                fontSize = 40.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${current.temperature}°C",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = textColor
                                            )
                                        }
                                        Text(
                                            text = ClothingAdvice.getAdvice(current.temperature),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = textColor
                                        )
                                        Text(
                                            text = ClothingAdvice.getDetails(current.temperature),
                                            textAlign = TextAlign.Center,
                                            color = textColor.copy(alpha = 0.85f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val categoryIndex =
                                        ClothingAdvice.getCategoryIndex(current.temperature)
                                    val photoUri = adviceImages[categoryIndex]
                                    if (photoUri != null) {
                                        UriPhoto(
                                            uri = photoUri,
                                            modifier = Modifier.size(176.dp),
                                            cornerRadius = 20
                                        )
                                    } else {
                                        // Placeholder : emoji de la catégorie tant qu'aucune photo n'est choisie
                                        Box(
                                            modifier = Modifier
                                                .size(176.dp)
                                                .background(
                                                    Color.White.copy(alpha = 0.25f),
                                                    RoundedCornerShape(20.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ADVICE_CATEGORIES[categoryIndex].emoji,
                                                fontSize = 60.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.week),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        val daily = currentState.daily
                        if (daily != null) {
                            items(daily.time.size) { i ->
                                val dateStr = daily.time[i]
                                val dayLabel = formatDayLabel(dateStr)
                                val dayCode = daily.weather_code?.getOrNull(i)
                                val rainProbability =
                                    daily.precipitation_probability_max?.getOrNull(i)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = hexToColor(cardColorHex)
                                            .copy(alpha = cardOpacity / 100f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = dayLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Icône du temps du jour
                                        Text(
                                            text = WeatherIcons.iconFor(dayCode),
                                            fontSize = 20.sp
                                        )
                                        // Indicateur de pluie (probabilité >= 40 %)
                                        if (rainProbability != null && rainProbability >= 40) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "💧$rainProbability%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = textColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${daily.temperature_2m_min[i]}° / ${daily.temperature_2m_max[i]}°",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialogue de recherche de ville
        if (showCityDialog) {
            CitySearchDialog(
                onDismissRequest = { showCityDialog = false },
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    isSearching = true
                    scope.launch {
                        val results = viewModel.searchCities(searchQuery)
                        searchResults = results
                        isSearching = false
                    }
                },
                results = searchResults,
                isSearching = isSearching,
                onCitySelected = { city ->
                    showCityDialog = false
                    searchQuery = ""
                    searchResults = emptyList()
                    viewModel.loadWeatherForCity(city)
                }
            )
        }

        // Dialogue de réglages
        if (showAppearanceDialog) {
            AppearanceDialog(
                onDismissRequest = { showAppearanceDialog = false },
                currentImageUri = wallpaperImageUri,
                wallpaperColor = wallpaperColorHex,
                textColor = textColorHex,
                cardColor = cardColorHex,
                cardOpacity = cardOpacity,
                currentAdviceImages = adviceImages,
                notificationSettings = notificationSettings,
                onPickWallpaper = { pickWallpaperLauncher.launch(arrayOf("image/*")) },
                onClearImage = { viewModel.clearWallpaperImage() },
                onAdviceImagePick = { category ->
                    pickingAdviceCategory = category
                    pickAdviceLauncher.launch(arrayOf("image/*"))
                },
                onAdviceImageClear = { category -> viewModel.setAdviceImageUri(category, null) },
                onWallpaperColorSelected = { hex -> viewModel.setWallpaperColor(hex) },
                onTextColorSelected = { hex -> viewModel.setTextColor(hex) },
                onCardColorSelected = { hex -> viewModel.setCardColor(hex) },
                onCardOpacityChange = { percent -> viewModel.setCardOpacity(percent) },
                onCustomColor = { target -> colorPickerTarget = target },
                onNotificationToggle = { day, checked ->
                    if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingNotificationDay = day
                        notificationPermissionLauncher.launch(permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setNotificationDayEnabled(day, checked)
                    }
                },
                onPickNotificationTime = { day -> timePickerDay = day }
            )
        }

        // Sélecteur de couleur libre (R/V/B)
        colorPickerTarget?.let { target ->
            ColorPickerDialog(
                title = when (target) {
                    "wallpaper" -> "Couleur du fond d'écran"
                    "text" -> "Couleur du texte"
                    else -> "Fond des prévisions"
                },
                initialHex = when (target) {
                    "wallpaper" -> wallpaperColorHex
                    "text" -> textColorHex
                    else -> cardColorHex
                },
                onConfirm = { hex ->
                    when (target) {
                        "wallpaper" -> viewModel.setWallpaperColor(hex)
                        "text" -> viewModel.setTextColor(hex)
                        else -> viewModel.setCardColor(hex)
                    }
                    colorPickerTarget = null
                },
                onDismiss = { colorPickerTarget = null }
            )
        }

        // Dialogue de sélection d'horaire de notification
        timePickerDay?.let { day ->
            val settings = notificationSettings.firstOrNull { it.day == day }
            TimePickerDialog(
                day = day,
                initialHour = settings?.hour ?: 8,
                initialMinute = settings?.minute ?: 0,
                onConfirm = { hour, minute ->
                    viewModel.setNotificationTime(day, hour, minute)
                    timePickerDay = null
                },
                onDismiss = { timePickerDay = null }
            )
        }
    }
}

/** Libellé de localisation selon l'état GPS / ville. */
@Composable
private fun displayLocation(cityName: String?, isUsingGps: Boolean): String {
    return when {
        isUsingGps && cityName != null -> cityName
        isUsingGps -> stringResource(R.string.location_current)
        else -> cityName ?: stringResource(R.string.location)
    }
}

/** Convertit une date "YYYY-MM-DD" en "lun 25 oct." */
private fun formatDayLabel(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        val formatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)
        date.format(formatter).capitalize()
    } catch (e: Exception) {
        dateStr
    }
}

/** Convertit une chaîne hex "#RRGGBB" en Color Compose. */
private fun hexToColor(hex: String): Color {
    val rgb = Integer.parseInt(hex.substring(1), 16)
    return Color((-16777216) or rgb)
}

/** Convertit une chaîne hex "#RRGGBB" en triplet (rouge, vert, bleu). */
private fun hexToRgb(hex: String): Triple<Int, Int, Int> {
    val rgb = Integer.parseInt(hex.substring(1), 16)
    return Triple((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
}

/** Catégorie de conseil vestimentaire (index = ClothingAdvice.getCategoryIndex). */
private data class AdviceCategory(
    val index: Int,
    val title: String,
    val range: String,
    val emoji: String
)

/** Les 4 catégories de conseil, avec placeholder emoji. */
private val ADVICE_CATEGORIES = listOf(
    AdviceCategory(0, "T-shirt ☀️", "20 °C et plus", "☀️"),
    AdviceCategory(1, "Pull léger 🧥", "10 à 19 °C", "🧥"),
    AdviceCategory(2, "Pull + veste 🧣", "0 à 9 °C", "🧣"),
    AdviceCategory(3, "Manteau ❄️", "moins de 0 °C", "❄️")
)

/** Noms des jours pour les notifications (index 1..7 = lundi..dimanche). */
private val DAY_NAMES =
    listOf("", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/** Palette de pastilles proposée pour les 3 réglages de couleur. */
private val SWATCH_COLORS = listOf(
    "#FFFFFF", "#000000", "#FFEB3B", "#80DEEA", "#F8BBD0", "#C5E1A5"
)

/** Couleurs disponibles pour le fond d'écran (libellés conservés pour le picker prédéfini). */
private val WALLPAPER_OPTIONS = listOf(
    Pair("Violet (par défaut)", "#6200EE"),
    Pair("Bleu teal", "#03DAC5"),
    Pair("Teal foncé", "#018786"),
    Pair("Lavande", "#BB86FC"),
    Pair("Noir", "#000000"),
    Pair("Blanc", "#FFFFFF")
)

/**
 * Affiche une image depuis une URI content:// (galerie).
 * Décodage en bitmap avec rééchantillonnage pour limiter la mémoire.
 * Rien n'est affiché si l'URI ne peut pas être décodée (permission perdue, image invalide…).
 */
@Composable
private fun UriPhoto(
    uri: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 0
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val source = Uri.parse(uri)
                // 1er passage : dimensions seulement
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(source)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                // Rééchantillonnage : viser ~1024 px max
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1024 ||
                    bounds.outHeight / (sample * 2) >= 1024
                ) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                // 2e passage : décodage réel
                context.contentResolver.openInputStream(source)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(cornerRadius.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

/** Rangée de pastilles de couleur + pastille arc-en-ciel pour une couleur libre. */
@Composable
private fun SwatchesRow(
    selectedHex: String,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SWATCH_COLORS.forEach { hex ->
            val isSelected = hex == selectedHex
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .background(hexToColor(hex))
                    .clickable { onSelect(hex) }
            )
        }
        // Pastille arc-en-ciel : ouvre le sélecteur de couleur libre
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                    )
                )
                .clickable { onCustom() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontSize = 16.sp,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun AppearanceDialog(
    onDismissRequest: () -> Unit,
    currentImageUri: String?,
    wallpaperColor: String,
    textColor: String,
    cardColor: String,
    cardOpacity: Int,
    currentAdviceImages: Map<Int, String>,
    notificationSettings: List<DayNotification>,
    onPickWallpaper: () -> Unit,
    onClearImage: () -> Unit,
    onAdviceImagePick: (Int) -> Unit,
    onAdviceImageClear: (Int) -> Unit,
    onWallpaperColorSelected: (String) -> Unit,
    onTextColorSelected: (String) -> Unit,
    onCardColorSelected: (String) -> Unit,
    onCardOpacityChange: (Int) -> Unit,
    onCustomColor: (String) -> Unit,
    onNotificationToggle: (Int, Boolean) -> Unit,
    onPickNotificationTime: (Int) -> Unit
) {
    // Date de build affichée en pied de dialogue
    val buildDate = remember {
        Instant.ofEpochMilli(BuildConfig.BUILD_TIME)
            .atZone(ZoneId.of("Europe/Paris"))
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE))
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Réglages") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // --- Fond d'écran ---
                Text(
                    text = "Fond d'écran :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onPickWallpaper,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Choisir une photo (galerie)")
                }
                if (currentImageUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onClearImage,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(text = "Supprimer la photo du fond")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SwatchesRow(
                    selectedHex = wallpaperColor,
                    onSelect = onWallpaperColorSelected,
                    onCustom = { onCustomColor("wallpaper") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Couleur du texte ---
                Text(
                    text = "Couleur du texte :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SwatchesRow(
                    selectedHex = textColor,
                    onSelect = onTextColorSelected,
                    onCustom = { onCustomColor("text") }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Prévisions (couleur du fond + transparence) ---
                Text(
                    text = "Prévisions :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Couleur du fond :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SwatchesRow(
                    selectedHex = cardColor,
                    onSelect = onCardColorSelected,
                    onCustom = { onCustomColor("card") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Transparence : $cardOpacity %",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = cardOpacity.toFloat(),
                    onValueChange = { onCardOpacityChange(it.toInt()) },
                    valueRange = 0f..100f
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Photos des conseils vestimentaires ---
                Text(
                    text = "Photos des conseils vestimentaires :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                ADVICE_CATEGORIES.forEach { category ->
                    val uri = currentAdviceImages[category.index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uri != null) {
                            UriPhoto(
                                uri = uri,
                                modifier = Modifier.size(40.dp),
                                cornerRadius = 8
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Text(
                                text = category.emoji,
                                fontSize = 20.sp,
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = category.range,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onAdviceImagePick(category.index) }) {
                            Text(text = if (uri != null) "Changer" else "Choisir")
                        }
                        if (uri != null) {
                            TextButton(onClick = { onAdviceImageClear(category.index) }) {
                                Text(text = "✕")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // --- Notifications quotidiennes ---
                Text(
                    text = "Notification quotidienne :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                notificationSettings.forEach { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DAY_NAMES[day.day],
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onPickNotificationTime(day.day) }) {
                            Text(text = "%02d:%02d".format(day.hour, day.minute))
                        }
                        Switch(
                            checked = day.enabled,
                            onCheckedChange = { checked ->
                                onNotificationToggle(day.day, checked)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                // --- Pied de dialogue ---
                Text(
                    text = "Créée par Jules Luthner\nBuild du $buildDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {},
        dismissButton = null
    )
}

/** Sélecteur de couleur libre : curseurs rouge / vert / bleu avec aperçu. */
@Composable
private fun ColorPickerDialog(
    title: String,
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val (initR, initG, initB) = hexToRgb(initialHex)
    var red by remember { mutableIntStateOf(initR) }
    var green by remember { mutableIntStateOf(initG) }
    var blue by remember { mutableIntStateOf(initB) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Aperçu de la couleur
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(red, green, blue))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Rouge : $red",
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = red.toFloat(),
                    onValueChange = { red = it.toInt() },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
                )
                Text(
                    text = "Vert : $green",
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = green.toFloat(),
                    onValueChange = { green = it.toInt() },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green)
                )
                Text(
                    text = "Bleu : $blue",
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = blue.toFloat(),
                    onValueChange = { blue = it.toInt() },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm("#%02X%02X%02X".format(red, green, blue)) }) {
                Text(text = "Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Annuler")
            }
        }
    )
}

/** Dialogue de sélection de l'horaire de notification d'un jour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    day: Int,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Notification — ${DAY_NAMES[day]}") },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Annuler")
            }
        }
    )
}

@Composable
private fun CitySearchDialog(
    onDismissRequest: () -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<CityResult>,
    isSearching: Boolean,
    onCitySelected: (CityResult) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Changer de ville") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    label = { Text(text = "Nom de la ville") },
                    placeholder = { Text(text = "Ex: Paris, Lyon, Marseille") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSearch,
                    enabled = searchQuery.isNotBlank() && !isSearching
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(text = "Rechercher")
                    }
                }
                if (results.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Résultats :",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(results) { city ->
                            TextButton(
                                onClick = { onCitySelected(city) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = city.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                            }
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Fermer")
            }
        },
        dismissButton = null
    )
}
