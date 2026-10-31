package au.edu.jcu.assessment.utilityapp.domain

import java.time.Instant
import java.util.Locale

enum class UmbrellaAdvice { BRING_UMBRELLA, RAIN_LESS_LIKELY, CHECK_FORECAST }

fun umbrellaAdvice(report: WeatherReport, now: Instant, thresholdPercent: Int = 50): UmbrellaAdvice {
    require(thresholdPercent in 1..100)
    if (now < report.fetchedAt || now >= report.fetchedAt.plusSeconds(600)) return UmbrellaAdvice.CHECK_FORECAST
    val regional = report.nearbyArea?.takeIf { now >= it.validFrom && now < it.validUntil }
    val regionalText = regional?.description?.lowercase(Locale.ROOT).orEmpty()
    val regionalRain = listOf("rain", "showers", "thunder", "drizzle").any { it in regionalText }
    val currentRain = report.weather.weatherCode in setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
        71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
    val upcoming = report.weather.upcomingRain.filter { it.endsAt > now }
    if (regionalRain || currentRain || upcoming.any { (it.probabilityPercent ?: -1) >= thresholdPercent }) {
        return UmbrellaAdvice.BRING_UMBRELLA
    }
    if (upcoming.isEmpty() || upcoming.any { it.probabilityPercent == null }) return UmbrellaAdvice.CHECK_FORECAST
    return UmbrellaAdvice.RAIN_LESS_LIKELY
}
