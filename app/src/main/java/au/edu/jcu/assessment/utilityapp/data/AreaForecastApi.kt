package au.edu.jcu.assessment.utilityapp.data

import retrofit2.http.GET

// Retrofit base URL: https://api-open.data.gov.sg/v2/real-time/api/
interface AreaForecastApi {
    // Fetch all Singapore forecast areas; nearby-area matching happens in the app.
    // Omitting date filters requests the latest available two-hour forecast.
    @GET("two-hr-forecast")
    suspend fun getLatestForecast(): AreaForecastResponse
}
