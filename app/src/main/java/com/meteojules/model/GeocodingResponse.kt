package com.meteojules.model

data class GeocodingResponse(
    val display_name: String?,
    val lat: String?,
    val lon: String?,
    val address: Address?
) {

    data class Address(
        val city: String?,
        val town: String?,
        val village: String?,
        val county: String?,
        val state: String?,
        val country: String?
    )

    /** Extrait le nom de la ville (ville > bourg > village > comté > État). */
    fun cityName(): String? =
        address?.city
            ?: address?.town
            ?: address?.village
            ?: address?.county
            ?: address?.state
            ?: display_name
}

/** Résultat de recherche d'une ville par nom. */
data class CityResult(
    val name: String,
    val lat: Double,
    val lon: Double
)
