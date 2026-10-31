package au.edu.jcu.assessment.utilityapp.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class UmbrellaAdviceTest {
    private val now = Instant.parse("2026-10-31T08:30:00Z")
    private fun report(probabilities: List<Int?> = listOf(10, 20, 30), code: Int? = 3): WeatherReport = WeatherReport(
        Coordinates(1.3, 103.8), WeatherSnapshot(ZoneOffset.UTC, now, 28.0, null, code,
            probabilities.mapIndexed { index, probability ->
                HourlyRainForecast(now.minusSeconds(1800).plusSeconds(index * 3600L),
                    now.plusSeconds(1800).plusSeconds(index * 3600L), probability)
            }), null, RegionalForecastStatus.UNAVAILABLE, now)
    private fun regional() = NearbyAreaForecast("Bedok", "Thundery Showers", 1.0, now.minusSeconds(300),
        now.minusSeconds(200), now.minusSeconds(1800), now.plusSeconds(5400))

    @Test fun highHourlyChanceRecommendsUmbrella() {
        assertEquals(UmbrellaAdvice.BRING_UMBRELLA, umbrellaAdvice(report(listOf(10, 50, 30)), now))
    }
    @Test fun currentRainRecommendsUmbrellaEvenWithLowHourlyChances() {
        assertEquals(UmbrellaAdvice.BRING_UMBRELLA, umbrellaAdvice(report(code = 61), now))
    }
    @Test fun validRegionalShowersRecommendsUmbrella() {
        assertEquals(UmbrellaAdvice.BRING_UMBRELLA, umbrellaAdvice(report().copy(nearbyArea = regional()), now))
    }
    @Test fun expiredRegionalRainDoesNotOverrideNewerLowProbabilities() {
        val expired = regional().copy(validUntil = now)
        assertEquals(UmbrellaAdvice.RAIN_LESS_LIKELY, umbrellaAdvice(report().copy(nearbyArea = expired), now))
    }
    @Test fun completeLowProbabilitiesDoNotPromiseNoRain() {
        assertEquals(UmbrellaAdvice.RAIN_LESS_LIKELY, umbrellaAdvice(report(), now))
    }
    @Test fun missingProbabilityIsNotTreatedAsZero() {
        assertEquals(UmbrellaAdvice.CHECK_FORECAST, umbrellaAdvice(report(listOf(10, null, 20)), now))
    }
    @Test fun oldAndFutureFetchedTimesDoNotProduceCurrentAdvice() {
        for (fetchedAt in listOf(now.minusSeconds(600), now.plusSeconds(1))) {
            assertEquals(UmbrellaAdvice.CHECK_FORECAST, umbrellaAdvice(report(listOf(90, 90, 90)).copy(fetchedAt = fetchedAt), now))
        }
    }
    @Test fun periodsThatHaveEndedDoNotInfluenceAdvice() {
        val base = report(listOf(90, 10, 10))
        val later = now.plusSeconds(1800)
        assertEquals(UmbrellaAdvice.RAIN_LESS_LIKELY, umbrellaAdvice(base.copy(fetchedAt = later), later))
    }
}
