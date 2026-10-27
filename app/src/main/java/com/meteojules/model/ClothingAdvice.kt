package com.meteojules.model

object ClothingAdvice {

    /**
     * Retourne un conseil vestimentaire simple selon la température (°C).
     */
    fun getAdvice(temp: Double): String = when {
        temp >= 20.0 -> "T-shirt ☀️"
        temp >= 10.0 -> "Pull léger / manches longues 🧥"
        temp >= 0.0 -> "Pull + veste 🧣"
        else -> "Manteau, écharpe et bonnet ❄️"
    }

    fun getDetails(temp: Double): String = when {
        temp >= 25.0 -> "Très chaud, t-shirt et shorts conseillés."
        temp >= 20.0 -> "Chaud, un t-shirt suffit."
        temp >= 15.0 -> "Doux, une chemise à manches longues ou un pull léger."
        temp >= 10.0 -> "Frais, prévoyez un pull ou un gilet."
        temp >= 0.0 -> "Froid, portez un pull et une veste."
        else -> "Très froid, manteau, écharpe et bonnet recommandés."
    }

    /**
     * Indice de catégorie vestimentaire (0..3) pour la température (°C) :
     * 0 = T-shirt (>= 20 °C), 1 = Pull léger (10-19 °C),
     * 2 = Pull + veste (0-9 °C), 3 = Manteau (< 0 °C).
     * Sert à associer une photo personnalisée à chaque tenue.
     */
    fun getCategoryIndex(temp: Double): Int = when {
        temp >= 20.0 -> 0
        temp >= 10.0 -> 1
        temp >= 0.0 -> 2
        else -> 3
    }
}
