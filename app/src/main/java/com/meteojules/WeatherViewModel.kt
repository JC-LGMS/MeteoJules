package com.meteojules

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meteojules.model.CityResult
import com.meteojules.model.WeatherResponse
import com.meteojules.notifications.DayNotification
import com.meteojules.notifications.WeatherNotificationScheduler
import com.meteojules.settings.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = getApplication()

    private val repository = WeatherRepository()
    private val locationRepository = LocationRepository(appContext)
    private val geocodingRepository = GeocodingRepository()

    private val _weatherState = MutableStateFlow<WeatherResponse?>(null)
    val weatherState = _weatherState.asStateFlow()

    private val _isLoadingState = MutableStateFlow(false)
    val isLoadingState = _isLoadingState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState = _errorState.asStateFlow()

    private val _cityName = MutableStateFlow<String?>(null)
    val cityName = _cityName.asStateFlow()

    /** Indique si la position affichée vient du GPS (true) ou du fallback Paris (false). */
    private val _isUsingGps = MutableStateFlow(false)
    val isUsingGps = _isUsingGps.asStateFlow()

    /** Latitude affichée (pour débogage / transparence). */
    private val _lat = MutableStateFlow<Double?>(null)
    val lat = _lat.asStateFlow()

    /** Longitude affichée (pour débogage / transparence). */
    private val _lon = MutableStateFlow<Double?>(null)
    val lon = _lon.asStateFlow()

    /** URI de l'image de fond (galerie) ou null si couleur unie. */
    private val _wallpaperImageUri = MutableStateFlow<String?>(null)
    val wallpaperImageUri = _wallpaperImageUri.asStateFlow()

    /** Charge l'URI de fond sauvegardée depuis les préférences. */
    fun loadSavedWallpaperImage() {
        _wallpaperImageUri.value = appContext.getWallpaperImageUri()
    }

    /**
     * Photos personnalisées par catégorie de conseil vestimentaire (0..3).
     * Clé = indice de catégorie (ClothingAdvice.getCategoryIndex), valeur = URI galerie.
     */
    private val _adviceImageUris = MutableStateFlow<Map<Int, String>>(emptyMap())
    val adviceImageUris = _adviceImageUris.asStateFlow()

    /** Charge les photos de conseils sauvegardées depuis les préférences. */
    fun loadSavedAdviceImages() {
        val map = (0..3)
            .mapNotNull { category -> appContext.getAdviceImageUri(category)?.let { category to it } }
            .toMap()
        _adviceImageUris.value = map
    }

    /** Définit (ou supprime si uri == null) la photo d'une catégorie de conseil. */
    fun setAdviceImageUri(category: Int, uri: String?) {
        appContext.saveAdviceImageUri(category, uri)
        val updated = _adviceImageUris.value.toMutableMap()
        if (uri == null) {
            updated.remove(category)
        } else {
            updated[category] = uri
        }
        _adviceImageUris.value = updated
    }

    /**
     * Charge la météo pour des coordonnées données (défaut : Paris).
     */
    fun loadWeather(lat: Double = 48.8566, lon: Double = 2.3522) {
        viewModelScope.launch {
            _isLoadingState.value = true
            _errorState.value = null
            _isUsingGps.value = false
            _lat.value = lat
            _lon.value = lon
            appContext.saveLastLocation(lat, lon)
            try {
                _weatherState.value = repository.fetchWeather(lat, lon)
            } catch (e: Exception) {
                _errorState.value = e.message
            } finally {
                _isLoadingState.value = false
            }
        }
    }

    /**
     * Charge la météo selon la position actuelle de l'appareil.
     * Récupère aussi le nom de la ville via géocodage inverse.
     * Si la géolocalisation est indisponible, fallback sur Paris.
     */
    fun loadWeatherWithLocation() {
        viewModelScope.launch {
            _isLoadingState.value = true
            _errorState.value = null
            try {
                val location = locationRepository.getCurrentLocation()
                val lat = location?.latitude ?: 48.8566
                val lon = location?.longitude ?: 2.3522
                _isUsingGps.value = location != null
                _lat.value = lat
                _lon.value = lon
                appContext.saveLastLocation(lat, lon)

                // Nom de la ville (géocodage inverse)
                val city = geocodingRepository.getCityName(lat, lon)
                _cityName.value = city

                _weatherState.value = repository.fetchWeather(lat, lon)
            } catch (e: Exception) {
                _errorState.value = e.message
            } finally {
                _isLoadingState.value = false
            }
        }
    }

    /**
     * Recherche des villes correspondant à un nom.
     */
    suspend fun searchCities(query: String): List<CityResult> =
        geocodingRepository.searchCities(query)

    /**
     * Charge la météo pour une ville sélectionnée (par nom + coordonnées).
     */
    fun loadWeatherForCity(city: CityResult) {
        viewModelScope.launch {
            _isLoadingState.value = true
            _errorState.value = null
            _isUsingGps.value = false
            _lat.value = city.lat
            _lon.value = city.lon
            _cityName.value = city.name
            appContext.saveLastLocation(city.lat, city.lon)
            try {
                _weatherState.value = repository.fetchWeather(city.lat, city.lon)
            } catch (e: Exception) {
                _errorState.value = e.message
            } finally {
                _isLoadingState.value = false
            }
        }
    }

    /**
     * Définit l'image de fond (URI de la galerie) et la sauvegarde.
     */
    fun setWallpaperImageUri(uri: String?) {
        appContext.saveWallpaperImageUri(uri)
        _wallpaperImageUri.value = uri
    }

    /** Supprime l'image de fond pour revenir à la couleur unie. */
    fun clearWallpaperImage() {
        appContext.clearWallpaperImage()
        _wallpaperImageUri.value = null
    }

    /**
     * Couleur du texte de tous les textes de l'écran principal (hex "#RRGGBB").
     */
    private val _textColor = MutableStateFlow("#FFFFFF")
    val textColor = _textColor.asStateFlow()

    /** Change la couleur du texte et la sauvegarde. */
    fun setTextColor(hex: String) {
        appContext.saveTextColor(hex)
        _textColor.value = hex
    }

    /**
     * Couleur du fond d'écran (appliquée quand aucune photo n'est définie).
     */
    private val _wallpaperColor = MutableStateFlow("#6200EE")
    val wallpaperColor = _wallpaperColor.asStateFlow()

    /** Change la couleur du fond d'écran (et retire la photo) et la sauvegarde. */
    fun setWallpaperColor(hex: String) {
        appContext.saveWallpaperColor(hex)
        setWallpaperImageUri(null)
        _wallpaperColor.value = hex
    }

    /**
     * Couleur du fond des cartes de prévisions de la semaine.
     */
    private val _cardColor = MutableStateFlow("#FFFFFF")
    val cardColor = _cardColor.asStateFlow()

    /** Change la couleur du fond des prévisions et la sauvegarde. */
    fun setCardColor(hex: String) {
        appContext.saveCardColor(hex)
        _cardColor.value = hex
    }

    /**
     * Opacité du fond des cartes de prévisions (0 = invisible, 100 = opaque).
     */
    private val _cardOpacity = MutableStateFlow(35)
    val cardOpacity = _cardOpacity.asStateFlow()

    /** Change l'opacité du fond des prévisions et la sauvegarde. */
    fun setCardOpacity(percent: Int) {
        appContext.saveCardOpacity(percent)
        _cardOpacity.value = percent
    }

    /**
     * Notifications quotidiennes : un réglage par jour de la semaine (1 = lundi … 7 = dimanche).
     */
    private val _notificationSettings =
        MutableStateFlow<List<DayNotification>>(emptyList())
    val notificationSettings = _notificationSettings.asStateFlow()

    /** Active/désactive la notification d'un jour et replanifie. */
    fun setNotificationDayEnabled(day: Int, enabled: Boolean) {
        appContext.setNotificationDayEnabled(day, enabled)
        refreshNotificationSettings()
    }

    /** Définit l'horaire de notification d'un jour et replanifie. */
    fun setNotificationTime(day: Int, hour: Int, minute: Int) {
        appContext.setNotificationTime(day, hour, minute)
        refreshNotificationSettings()
    }

    private fun refreshNotificationSettings() {
        _notificationSettings.value = loadNotificationSettings()
        if (appContext.hasAnyNotificationDay()) {
            WeatherNotificationScheduler.scheduleNext(appContext)
        } else {
            WeatherNotificationScheduler.cancel(appContext)
        }
    }

    private fun loadNotificationSettings(): List<DayNotification> =
        (1..7).map { day ->
            DayNotification(
                day = day,
                enabled = appContext.getNotificationDayEnabled(day),
                hour = appContext.getNotificationHour(day),
                minute = appContext.getNotificationMinute(day)
            )
        }

    init {
        _textColor.value = appContext.getTextColor()
        _wallpaperColor.value = appContext.getWallpaperColor()
        _cardColor.value = appContext.getCardColor()
        _cardOpacity.value = appContext.getCardOpacity()
        _notificationSettings.value = loadNotificationSettings()
        if (appContext.hasAnyNotificationDay()) {
            WeatherNotificationScheduler.scheduleNext(appContext)
        }
    }
}
