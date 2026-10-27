package com.meteojules

import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Récupère la position actuelle de l'appareil via Google Play Services.
 * Retourne null si la position est indisponible (dernière position inconnue
 * et aucune mise à jour reçue dans le délai imparti).
 */
class LocationRepository(private val context: Context) {

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Récupère la position actuelle.
     * 1. Essaie d'abord la dernière position connue (rapide, pas de GPS requis).
     * 2. Sinon, demande une seule mise à jour GPS (timeout 15 s).
     */
    suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        val last = getLastLocation()
        if (last != null) return@withContext last
        requestSingleUpdate()
    }

    private suspend fun getLastLocation(): Location? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Location?>()
        val task = client.lastLocation
        task.addOnSuccessListener { loc: Location -> deferred.complete(loc) }
        task.addOnFailureListener { _ -> deferred.complete(null) }
        deferred.await()
    }

    private suspend fun requestSingleUpdate(): Location? = withTimeoutOrNull(15_000L) {
        val deferred = CompletableDeferred<Location?>()
        val callback = object : LocationCallback() {
            override fun onLocationResult(
                result: com.google.android.gms.location.LocationResult
            ) {
                client.removeLocationUpdates(this)
                if (!deferred.isCompleted) {
                    deferred.complete(result.lastLocation)
                }
            }
        }
        val request = LocationRequest.create().apply {
            setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            setNumUpdates(1)
            setInterval(5000)
            setFastestInterval(1000)
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        deferred.await()
    }
}
