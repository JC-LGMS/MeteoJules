package com.meteojules.model

data class WeatherResponse(
    val current_weather: CurrentWeather?,
    val daily: Daily?
)

data class CurrentWeather(
    val temperature: Double,
    val windspeed: Double,
    val weathercode: Int,
    val time: String
)

data class Daily(
    val time: List<String>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>,
    val weather_code: List<Int>? = null,
    val precipitation_probability_max: List<Int>? = null
)
