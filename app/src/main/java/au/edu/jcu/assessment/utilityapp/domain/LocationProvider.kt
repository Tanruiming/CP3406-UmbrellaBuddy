package au.edu.jcu.assessment.utilityapp.domain

import java.time.Instant

interface LocationProvider {
    suspend fun getCurrentLocation(): LocationResult
}

data class DeviceLocation(
    val coordinates: Coordinates,
    val accuracyMetres: Float,
    val approximate: Boolean,
    val capturedAt: Instant,
    val areaName: String? = null,
    val countryCode: String? = null
)

sealed interface LocationResult {
    data class Success(val location: DeviceLocation) : LocationResult
    data class Failure(val reason: LocationFailure) : LocationResult
}

enum class LocationFailure { PERMISSION_REQUIRED, DISABLED, TIMEOUT, UNAVAILABLE }

// Use monotonic age rather than wall-clock time to reject old fixes after clock changes.
fun isUsableLocationFix(latitude: Double, longitude: Double, accuracyMetres: Float, ageMillis: Long): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        accuracyMetres.isFinite() && accuracyMetres > 0 && ageMillis in 0..120_000
