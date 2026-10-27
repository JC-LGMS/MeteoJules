package com.meteojules.model

/**
 * Icônes (emoji) du temps selon les codes WMO d'Open-Meteo.
 */
object WeatherIcons {

    /** Icône correspondant au code météo WMO (soleil, nuage, pluie, neige, brouillard, orage…). */
    fun iconFor(code: Int?): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    /** true si le code correspond à un temps de pluie. */
    fun isRainy(code: Int?): Boolean =
        code != null && (code in 51..67 || code in 80..82 || code in 95..99)
}
