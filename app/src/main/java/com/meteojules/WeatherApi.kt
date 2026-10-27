package com.meteojules

import com.meteojules.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") current: Boolean,
        @Query("daily") daily: String,
        @Query("hourly") hourly: String,
        @Query("timezone") tz: String
    ): WeatherResponse
}
