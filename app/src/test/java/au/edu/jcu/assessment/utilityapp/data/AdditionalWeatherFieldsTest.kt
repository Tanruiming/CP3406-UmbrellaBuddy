package au.edu.jcu.assessment.utilityapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AdditionalWeatherFieldsTest {
    private val now = Instant.parse("2026-10-31T08:30:00Z")
    private fun response() = WeatherResponse("Asia/Singapore",
        CurrentWeather(now.epochSecond, 28.0, 31.0, 3, windSpeedKmh = 36.0, seaLevelPressureHpa = 1013.25),
        HourlyWeather((1L..3L).map { now.epochSecond - 1800 + it * 3600 }, listOf(30, 40, 20), listOf(1.0, 0.0, 2.0)))

    @Test fun preservesSourceUnitsAndPairsPrecipitationWithCorrectHour() {
        val result = WeatherDataMapper().map(response(), now)
        assertEquals(36.0, result.windSpeedKmh!!, 0.0)
        assertEquals(1013.25, result.seaLevelPressureHpa!!, 0.0)
        assertEquals(listOf(1.0, 0.0, 2.0), result.upcomingRain.map { it.precipitationMm })
    }

    @Test fun missingAdditionalFieldsStayUnavailable() {
        val source = response()
        val result = WeatherDataMapper().map(source.copy(current = CurrentWeather(now.epochSecond, 28.0),
            hourly = source.hourly!!.copy(precipitationMm = null)), now)
        assertNull(result.windSpeedKmh)
        assertNull(result.seaLevelPressureHpa)
        assertTrue(result.upcomingRain.all { it.precipitationMm == null })
    }

    @Test fun rejectsNegativeAndNonFiniteAdditionalValues() {
        val source = response()
        val badResponses = listOf(
            source.copy(current = source.current!!.copy(windSpeedKmh = -1.0)),
            source.copy(current = source.current.copy(seaLevelPressureHpa = Double.NaN)),
            source.copy(hourly = source.hourly!!.copy(precipitationMm = listOf(0.0, -1.0, 2.0)))
        )
        badResponses.forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { WeatherDataMapper().map(invalid, now) }
        }
    }

    @Test fun rejectsMismatchedPrecipitationTimes() {
        val source = response()
        assertThrows(IllegalArgumentException::class.java) {
            WeatherDataMapper().map(source.copy(hourly = source.hourly!!.copy(precipitationMm = listOf(1.0))), now)
        }
    }
}
