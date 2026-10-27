package com.meteojules

import android.util.Log
import com.meteojules.model.CityResult
import com.meteojules.model.GeocodingResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Géocodage inverse (coordonnées → nom de ville) via OpenStreetMap Nominatim.
 * Gratuit, aucune clé API requise.
 *
 * ⚠️ Nominatim exige un en-tête User-Agent identifiant l'application,
 * sinon les requêtes peuvent être rejetées.
 */
class GeocodingRepository {

    private val TAG = "GeocodingRepository"

    private val api: GeocodingApi by lazy {
        val logging = HttpLoggingInterceptor { message ->
            Log.d(TAG, "Nominatim: $message")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "MeteoJules/1.0 (https://github.com/jc-lg/meteojules)")
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingApi::class.java)
    }

    /**
     * Retourne le nom de la ville pour les coordonnées données, ou null si indisponible.
     */
    suspend fun getCityName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val response = api.reverseGeocode(lat, lon)
            val city = response.cityName()
            Log.d(TAG, "Reverse geocode $lat,$lon -> $city")
            city
        } catch (e: Exception) {
            Log.e(TAG, "Erreur géocodage inverse", e)
            null
        }
    }

    /**
     * Recherche des villes correspondant à un nom (ex: "Paris", "Lyon").
     * Retourne jusqu'à 5 résultats avec leurs coordonnées.
     */
    suspend fun searchCities(query: String): List<CityResult> = withContext(Dispatchers.IO) {
        try {
            val results = api.search(query)
            results.mapNotNull { geo ->
                val lat = geo.lat?.toDoubleOrNull()
                val lon = geo.lon?.toDoubleOrNull()
                val name = geo.display_name ?: geo.address?.city ?: geo.address?.town ?: geo.address?.village
                if (lat != null && lon != null && name != null) {
                    CityResult(name, lat, lon)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur recherche ville '$query'", e)
            emptyList()
        }
    }
}
