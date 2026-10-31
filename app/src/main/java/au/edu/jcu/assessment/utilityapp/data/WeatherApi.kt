package au.edu.jcu.assessment.utilityapp.data

import retrofit2.http.GET
import retrofit2.http.Query

// Retrofit base URL: https://api.open-meteo.com/
interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,weather_code",
        @Query("hourly") hourly: String = "precipitation_probability",
        // Two days keep upcoming hourly periods available near local midnight.
        @Query("forecast_days") forecastDays: Int = 2,
        @Query("timezone") timezone: String = "auto",
        // Match the Long timestamps declared in WeatherResponse.kt.
        @Query("timeformat") timeFormat: String = "unixtime",
        // Keep one source unit; display-unit conversion belongs in the app.
        @Query("temperature_unit") temperatureUnit: String = "celsius"
    ): WeatherResponse
}
