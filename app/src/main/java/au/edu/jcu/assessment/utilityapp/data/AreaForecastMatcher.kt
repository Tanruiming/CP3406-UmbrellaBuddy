package au.edu.jcu.assessment.utilityapp.data

import au.edu.jcu.assessment.utilityapp.domain.Coordinates
import au.edu.jcu.assessment.utilityapp.domain.NearbyAreaForecast
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AreaForecastMatcher {
    // The location layer must confirm country coverage; proximity alone can cross borders.
    // Unknown country, API failure or unavailable regional data leaves the global forecast usable.
    fun findNearest(
        response: AreaForecastResponse,
        location: Coordinates,
        countryCode: String?,
        now: Instant
    ): NearbyAreaForecast? {
        if (!countryCode.equals("SG", ignoreCase = true) || response.code != 0 ||
            !response.errorMessage.isNullOrBlank()) return null
        val data = response.data ?: return null
        val items = data.items.orEmpty().mapNotNull { item ->
            item ?: return@mapNotNull null
            val start = parseTime(item.validPeriod?.start) ?: return@mapNotNull null
            val end = parseTime(item.validPeriod?.end) ?: return@mapNotNull null
            val issued = parseTime(item.timestamp) ?: return@mapNotNull null
            val updated = parseTime(item.updateTimestamp) ?: return@mapNotNull null
            if (start >= end || now < start || now >= end || issued > now || updated > now ||
                updated < issued) return@mapNotNull null
            ValidItem(item, start, end, issued, updated)
        }
        val latest = items.maxByOrNull { it.updated } ?: return null
        val forecasts = latest.item.forecasts.orEmpty().filterNotNull()
            .filter { !it.area.isNullOrBlank() && !it.forecast.isNullOrBlank() }
            .associate { it.area!!.trim() to it.forecast!!.trim() }

        return data.areaMetadata.orEmpty().mapNotNull { area ->
            val name = area?.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val description = forecasts[name] ?: return@mapNotNull null
            val latitude = area.labelLocation?.latitude ?: return@mapNotNull null
            val longitude = area.labelLocation.longitude ?: return@mapNotNull null
            if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                !longitude.isFinite() || longitude !in -180.0..180.0) return@mapNotNull null
            NearbyAreaForecast(
                areaName = name,
                description = description,
                distanceKm = distanceKm(location, Coordinates(latitude, longitude)),
                issuedAt = latest.issued,
                updatedAt = latest.updated,
                validFrom = latest.start,
                validUntil = latest.end
            )
        }.minByOrNull { it.distanceKm }
    }

    private fun distanceKm(first: Coordinates, second: Coordinates): Double {
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val latitudeDifference = secondLatitude - firstLatitude
        val longitudeDifference = Math.toRadians(second.longitude - first.longitude)
        val a = sin(latitudeDifference / 2).let { it * it } +
            cos(firstLatitude) * cos(secondLatitude) * sin(longitudeDifference / 2).let { it * it }
        return 6371.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun parseTime(value: String?): Instant? = try {
        value?.let { OffsetDateTime.parse(it).toInstant() }
    } catch (_: DateTimeParseException) {
        null
    }

    private data class ValidItem(
        val item: AreaForecastItem,
        val start: Instant,
        val end: Instant,
        val issued: Instant,
        val updated: Instant
    )
}
