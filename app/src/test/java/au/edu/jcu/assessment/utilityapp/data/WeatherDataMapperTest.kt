package au.edu.jcu.assessment.utilityapp.data

import au.edu.jcu.assessment.utilityapp.domain.Coordinates
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherDataMapperTest {
    private val mapper = WeatherDataMapper()
    private val now = Instant.parse("2026-10-31T15:30:00Z")
    private val hourEnd = Instant.parse("2026-10-31T16:00:00Z").epochSecond

    private fun response() = WeatherResponse(
        timezone = "Asia/Singapore",
        current = CurrentWeather(now.epochSecond, 28.5, 31.0, 61),
        hourly = HourlyWeather(
            (0L..4L).map { hourEnd + (it - 1) * 3600 },
            listOf(10, 40, 60, 80, 90)
        )
    )

    @Test fun selectsThreeIntervalsThatHaveNotEndedAcrossMidnight() {
        val weather = mapper.map(response(), now)
        assertEquals(28.5, weather.temperatureCelsius, 0.0)
        assertEquals("Asia/Singapore", weather.timeZone.id)
        assertEquals(listOf(40, 60, 80), weather.upcomingRain.map { it.probabilityPercent })
        val first = weather.upcomingRain.first()
        assertEquals(Instant.parse("2026-10-31T15:00:00Z"), first.startsAt)
        assertEquals(Instant.parse("2026-11-01T00:00:00+08:00"), first.endsAt)
    }

    @Test fun excludesIntervalEndingExactlyNow() {
        val weather = mapper.map(response(), Instant.ofEpochSecond(hourEnd))
        assertEquals(listOf(60, 80, 90), weather.upcomingRain.map { it.probabilityPercent })
    }

    @Test fun missingProbabilityRemainsUnknown() {
        val source = response()
        val hourly = source.hourly!!.copy(rainProbability = listOf(10, null, 60, 80, 90))
        assertNull(mapper.map(source.copy(hourly = hourly), now).upcomingRain.first().probabilityPercent)
    }

    @Test fun missingOptionalConditionsRemainUnavailable() {
        val source = response().copy(current = CurrentWeather(now.epochSecond, 28.0, null, 999))
        val result = mapper.map(source, now)
        assertNull(result.feelsLikeCelsius)
        assertNull(result.weatherCode)
    }

    @Test fun rejectsMissingAndNonFiniteTemperatures() {
        for (temperature in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            val source = response().copy(current = CurrentWeather(now.epochSecond, temperature))
            assertThrows(IllegalArgumentException::class.java) { mapper.map(source, now) }
        }
    }

    @Test fun rejectsStaleAndFutureCurrentConditions() {
        for (time in listOf(now.minusSeconds(7201), now.plusSeconds(901))) {
            val source = response().copy(current = CurrentWeather(time.epochSecond, 28.0))
            assertThrows(IllegalArgumentException::class.java) { mapper.map(source, now) }
        }
    }

    @Test fun rejectsMismatchedArraysAndOutOfRangeProbability() {
        val source = response()
        for (probabilities in listOf(listOf(20), listOf(10, -1, 60, 80, 90), listOf(10, 101, 60, 80, 90))) {
            val hourly = source.hourly!!.copy(rainProbability = probabilities)
            assertThrows(IllegalArgumentException::class.java) { mapper.map(source.copy(hourly = hourly), now) }
        }
    }

    @Test fun rejectsDuplicateReversedOrGappedTimes() {
        val source = response()
        for (times in listOf(
            listOf(hourEnd, hourEnd, hourEnd + 3600),
            listOf(hourEnd + 3600, hourEnd, hourEnd + 7200),
            listOf(hourEnd, hourEnd + 7200, hourEnd + 10800)
        )) {
            val hourly = HourlyWeather(times, listOf(20, 30, 40))
            assertThrows(IllegalArgumentException::class.java) { mapper.map(source.copy(hourly = hourly), now) }
        }
    }

    @Test fun rejectsForecastWithoutCurrentHourOrEnoughUpcomingPeriods() {
        val source = response()
        for (times in listOf(
            listOf(hourEnd + 3600, hourEnd + 7200, hourEnd + 10800),
            listOf(hourEnd - 3600, hourEnd, hourEnd + 3600)
        )) {
            val hourly = HourlyWeather(times, listOf(20, 30, 40))
            assertThrows(IllegalArgumentException::class.java) { mapper.map(source.copy(hourly = hourly), now) }
        }
    }

    @Test fun rejectsInvalidCoordinates() {
        for ((latitude, longitude) in listOf(91.0 to 0.0, 0.0 to 181.0, Double.NaN to 0.0)) {
            assertThrows(IllegalArgumentException::class.java) { Coordinates(latitude, longitude) }
        }
    }
}
