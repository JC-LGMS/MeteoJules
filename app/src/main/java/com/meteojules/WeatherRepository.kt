package com.meteojules

import com.meteojules.model.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRepository {

    private val api: WeatherApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WeatherApi::class.java)

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse =
        withContext(Dispatchers.Default) {
            api.getWeather(
                lat = lat,
                lon = lon,
                current = true,
                daily = "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max",
                hourly = "temperature_2m",
                tz = "Europe/Paris"
            )
        }
}
