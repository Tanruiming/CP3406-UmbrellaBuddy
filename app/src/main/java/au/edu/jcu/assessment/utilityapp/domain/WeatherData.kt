package au.edu.jcu.assessment.utilityapp.domain

import java.time.Instant
import java.time.ZoneId

data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
    }
}

data class WeatherSnapshot(
    val timeZone: ZoneId,
    val observedAt: Instant,
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double?,
    val weatherCode: Int?,
    val upcomingRain: List<HourlyRainForecast>
)

// Probability applies to this whole hour; the first interval may already have begun.
data class HourlyRainForecast(
    val startsAt: Instant,
    val endsAt: Instant,
    val probabilityPercent: Int?
)

// The nearest reference point is an approximation, not a district boundary lookup.
data class NearbyAreaForecast(
    val areaName: String,
    val description: String,
    val distanceKm: Double,
    val issuedAt: Instant,
    val updatedAt: Instant,
    val validFrom: Instant,
    val validUntil: Instant
)
