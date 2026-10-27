package com.meteojules.settings

import android.content.Context
import android.net.Uri

private const val PREFS_NAME = "meteojules_settings"
private const val KEY_WALLPAPER_COLOR = "wallpaper_color"
private const val KEY_WALLPAPER_IMAGE_URI = "wallpaper_image_uri"
private const val KEY_ADVICE_IMAGE_PREFIX = "advice_image_uri_"
private const val KEY_TEXT_COLOR = "text_color"
private const val KEY_CARD_COLOR = "card_color"
private const val KEY_CARD_OPACITY = "card_opacity"
private const val KEY_NOTIFICATION_ENABLED_PREFIX = "notification_enabled_"
private const val KEY_NOTIFICATION_HOUR_PREFIX = "notification_hour_"
private const val KEY_NOTIFICATION_MINUTE_PREFIX = "notification_minute_"
private const val KEY_LAST_LAT = "last_lat"
private const val KEY_LAST_LON = "last_lon"
private const val DEFAULT_WALLPAPER_COLOR = "#6200EE"
private const val DEFAULT_TEXT_COLOR = "#FFFFFF"
private const val DEFAULT_CARD_COLOR = "#FFFFFF"
private const val DEFAULT_CARD_OPACITY = 35
private const val DEFAULT_NOTIFICATION_HOUR = 8
private const val DEFAULT_NOTIFICATION_MINUTE = 0

fun Context.saveWallpaperColor(color: String) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_WALLPAPER_COLOR, color)
        .apply()
}

fun Context.getWallpaperColor(): String {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_WALLPAPER_COLOR, DEFAULT_WALLPAPER_COLOR)
        ?: DEFAULT_WALLPAPER_COLOR
}

fun Context.saveWallpaperImageUri(uri: String?) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_WALLPAPER_IMAGE_URI, uri)
        .apply()
}

fun Context.getWallpaperImageUri(): String? {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_WALLPAPER_IMAGE_URI, null)
}

fun Context.clearWallpaperImage() {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_WALLPAPER_IMAGE_URI)
        .apply()
}

/** Clé SharedPreferences de la photo d'une catégorie de conseil (0..3). */
private fun adviceImageKey(category: Int): String = "$KEY_ADVICE_IMAGE_PREFIX$category"

/**
 * Sauvegarde la photo personnalisée d'une catégorie de conseil vestimentaire.
 * uri == null supprime l'entrée.
 */
fun Context.saveAdviceImageUri(category: Int, uri: String?) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(adviceImageKey(category), uri)
        .apply()
}

/** Lit la photo sauvegardée d'une catégorie de conseil (ou null si aucune). */
fun Context.getAdviceImageUri(category: Int): String? {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(adviceImageKey(category), null)
}

/** Sauvegarde la couleur du texte de tous les textes de l'écran principal. */
fun Context.saveTextColor(color: String) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_TEXT_COLOR, color)
        .apply()
}

/** Lit la couleur du texte (défaut : blanc). */
fun Context.getTextColor(): String {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR)
        ?: DEFAULT_TEXT_COLOR
}

/** Sauvegarde la couleur du fond des prévisions de la semaine. */
fun Context.saveCardColor(color: String) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CARD_COLOR, color)
        .apply()
}

/** Lit la couleur du fond des prévisions (défaut : blanc). */
fun Context.getCardColor(): String {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CARD_COLOR, DEFAULT_CARD_COLOR)
        ?: DEFAULT_CARD_COLOR
}

/** Sauvegarde l'opacité du fond des prévisions (0 = invisible, 100 = opaque). */
fun Context.saveCardOpacity(percent: Int) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_CARD_OPACITY, percent)
        .apply()
}

/** Lit l'opacité du fond des prévisions (défaut : 35 %). */
fun Context.getCardOpacity(): Int {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_CARD_OPACITY, DEFAULT_CARD_OPACITY)
}

/**
 * Notifications quotidiennes : un réglage par jour de la semaine.
 * day = 1 (lundi) … 7 (dimanche), conformément à java.time.DayOfWeek.value.
 */
fun Context.getNotificationDayEnabled(day: Int): Boolean {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_NOTIFICATION_ENABLED_PREFIX + day, false)
}

fun Context.setNotificationDayEnabled(day: Int, enabled: Boolean) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_NOTIFICATION_ENABLED_PREFIX + day, enabled)
        .apply()
}

fun Context.getNotificationHour(day: Int): Int {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_NOTIFICATION_HOUR_PREFIX + day, DEFAULT_NOTIFICATION_HOUR)
}

fun Context.getNotificationMinute(day: Int): Int {
    return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_NOTIFICATION_MINUTE_PREFIX + day, DEFAULT_NOTIFICATION_MINUTE)
}

fun Context.setNotificationTime(day: Int, hour: Int, minute: Int) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_NOTIFICATION_HOUR_PREFIX + day, hour)
        .putInt(KEY_NOTIFICATION_MINUTE_PREFIX + day, minute)
        .apply()
}

/** true si au moins un jour de notification est activé. */
fun Context.hasAnyNotificationDay(): Boolean {
    return (1..7).any { getNotificationDayEnabled(it) }
}

/** Mémorise les dernières coordonnées utilisées (pour la notification quotidienne). */
fun Context.saveLastLocation(lat: Double, lon: Double) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LAST_LAT, lat.toString())
        .putString(KEY_LAST_LON, lon.toString())
        .apply()
}

/** Lit les dernières coordonnées utilisées (défaut : Paris). */
fun Context.getLastLocation(): Pair<Double, Double> {
    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val lat = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull() ?: 48.8566
    val lon = prefs.getString(KEY_LAST_LON, null)?.toDoubleOrNull() ?: 2.3522
    return Pair(lat, lon)
}
