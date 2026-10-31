package au.edu.jcu.assessment.utilityapp.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class WeatherSettingsTest {
    @Test fun convertsTemperaturesFromOriginalCelsiusValues() {
        assertEquals(0.0, TemperatureUnit.CELSIUS.fromCelsius(0.0), 0.0)
        assertEquals(32.0, TemperatureUnit.FAHRENHEIT.fromCelsius(0.0), 0.0001)
        assertEquals(212.0, TemperatureUnit.FAHRENHEIT.fromCelsius(100.0), 0.0001)
        assertEquals(-40.0, TemperatureUnit.FAHRENHEIT.fromCelsius(-40.0), 0.0001)
        assertEquals(82.4, TemperatureUnit.FAHRENHEIT.fromCelsius(28.0), 0.0001)
    }

    @Test fun defaultSettingsMatchInitialControls() {
        assertEquals(TemperatureUnit.CELSIUS, WeatherSettings().temperatureUnit)
        assertEquals(50, WeatherSettings().rainThresholdPercent)
        assertFalse(WeatherSettings().rainNotificationsEnabled)
    }

    @Test fun convertsWindPrecipitationAndPressureFromSourceUnits() {
        assertEquals(10.0, WindSpeedUnit.METRES_PER_SECOND.fromKmh(36.0), 0.0001)
        assertEquals(22.3694, WindSpeedUnit.MILES_PER_HOUR.fromKmh(36.0), 0.0001)
        assertEquals(19.4384, WindSpeedUnit.KNOTS.fromKmh(36.0), 0.0001)
        assertEquals(1.0, PrecipitationUnit.INCHES.fromMillimetres(25.4), 0.0001)
        assertEquals(1013.25, PressureUnit.MILLIBARS.fromHpa(1013.25), 0.0001)
        assertEquals(29.9213, PressureUnit.INCHES_OF_MERCURY.fromHpa(1013.25), 0.0001)
        assertEquals(760.0, PressureUnit.MILLIMETRES_OF_MERCURY.fromHpa(1013.25), 0.0001)
    }

    @Test fun thresholdChangesAdviceButDoesNotSuppressCurrentRain() {
        val now = Instant.parse("2026-10-31T08:30:00Z")
        val report = WeatherReport(Coordinates(1.3, 103.8), WeatherSnapshot(ZoneOffset.UTC, now, 28.0, 31.0, 3,
            listOf(HourlyRainForecast(now.minusSeconds(1800), now.plusSeconds(1800), 40))),
            null, RegionalForecastStatus.UNAVAILABLE, now)
        assertEquals(UmbrellaAdvice.BRING_UMBRELLA, umbrellaAdvice(report, now, 30))
        assertEquals(UmbrellaAdvice.RAIN_LESS_LIKELY, umbrellaAdvice(report, now, 60))
        assertEquals(UmbrellaAdvice.BRING_UMBRELLA, umbrellaAdvice(report.copy(weather = report.weather.copy(weatherCode = 61)), now, 90))
    }
}
