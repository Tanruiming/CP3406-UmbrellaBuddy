package au.edu.jcu.assessment.utilityapp.data

import au.edu.jcu.assessment.utilityapp.domain.HourlyRainForecast
import au.edu.jcu.assessment.utilityapp.domain.WeatherSnapshot
import java.time.Instant
import java.time.ZoneId

class WeatherDataMapper {
    fun map(response: WeatherResponse, now: Instant): WeatherSnapshot {
        val zone = ZoneId.of(requireNotNull(response.timezone) { "Missing time zone" })
        val current = requireNotNull(response.current) { "Missing current conditions" }
        val observedAt = epochTime(current.time)
        // App freshness policy: accept at most two hours old or 15 minutes ahead.
        require(observedAt >= now.minusSeconds(7200) && observedAt <= now.plusSeconds(900)) {
            "Current conditions are outside the freshness window"
        }
        val temperature = requireNotNull(current.temperature) { "Missing temperature" }
        require(temperature.isFinite()) { "Invalid temperature" }
        require(current.feelsLike == null || current.feelsLike.isFinite()) { "Invalid feels-like value" }
        require(current.windSpeedKmh == null || (current.windSpeedKmh.isFinite() && current.windSpeedKmh >= 0)) { "Invalid wind speed" }
        require(current.seaLevelPressureHpa == null || (current.seaLevelPressureHpa.isFinite() && current.seaLevelPressureHpa > 0)) { "Invalid pressure" }

        val hourly = requireNotNull(response.hourly) { "Missing hourly forecast" }
        val times = requireNotNull(hourly.time) { "Missing hourly times" }
        val probabilities = requireNotNull(hourly.rainProbability) { "Missing hourly probabilities" }
        require(times.size == probabilities.size && times.isNotEmpty()) { "Mismatched hourly arrays" }
        require(hourly.precipitationMm == null || hourly.precipitationMm.size == times.size) { "Mismatched precipitation array" }

        val intervals = times.mapIndexed { index, time ->
            val end = epochTime(time)
            val probability = probabilities[index]
            require(probability == null || probability in 0..100) { "Invalid rain probability" }
            val amount = hourly.precipitationMm?.get(index)
            require(amount == null || (amount.isFinite() && amount >= 0)) { "Invalid precipitation amount" }
            // Open-Meteo reports precipitation probability for the preceding hour.
            HourlyRainForecast(end.minusSeconds(3600), end, probability, amount)
        }
        require(intervals.zipWithNext().all { (first, second) -> first.endsAt == second.startsAt }) {
            "Hourly times must be consecutive and ordered"
        }
        val upcoming = intervals.filter { it.endsAt > now }.take(3)
        require(upcoming.size == 3 && upcoming.first().startsAt <= now) {
            "Missing rain forecast for the current or upcoming hours"
        }

        return WeatherSnapshot(
            timeZone = zone,
            observedAt = observedAt,
            temperatureCelsius = temperature,
            feelsLikeCelsius = current.feelsLike,
            // Unknown codes remain unavailable rather than becoming clear weather.
            weatherCode = current.weatherCode?.takeIf { it in WEATHER_CODES },
            upcomingRain = upcoming,
            windSpeedKmh = current.windSpeedKmh,
            seaLevelPressureHpa = current.seaLevelPressureHpa
        )
    }

    private fun epochTime(seconds: Long?): Instant {
        require(seconds != null && seconds > 0) { "Missing or invalid Unix timestamp" }
        return Instant.ofEpochSecond(seconds)
    }

    private companion object {
        val WEATHER_CODES = setOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57,
            61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
    }
}
