package au.edu.jcu.assessment.utilityapp.domain

import java.time.Instant

interface WeatherRepository {
    suspend fun load(location: Coordinates, countryCode: String?): WeatherLoadResult
}

data class WeatherReport(
    val location: Coordinates,
    val weather: WeatherSnapshot,
    val nearbyArea: NearbyAreaForecast?,
    val regionalStatus: RegionalForecastStatus,
    val fetchedAt: Instant
)

enum class RegionalForecastStatus {
    AVAILABLE, UNAVAILABLE, OUTSIDE_SINGAPORE, COUNTRY_UNKNOWN
}

sealed interface WeatherLoadResult {
    data class Success(val report: WeatherReport) : WeatherLoadResult
    data class Failure(val reason: WeatherFailure) : WeatherLoadResult
}

enum class WeatherFailure {
    NETWORK, SERVICE, INVALID_DATA, UNEXPECTED
}
