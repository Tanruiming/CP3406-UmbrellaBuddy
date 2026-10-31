package au.edu.jcu.assessment.utilityapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import au.edu.jcu.assessment.utilityapp.domain.Coordinates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

// Opt-in network check using a public forecast reference point, never a user's device location.
@RunWith(AndroidJUnit4::class)
class LiveWeatherServiceTest {
    @Test fun realServicesReturnUsableWeatherAndRegionalForecast() = runBlocking {
        assumeTrue("Opt-in live service check", InstrumentationRegistry.getArguments().getString("liveWeather") == "true")
        val clients = NetworkClients()
        val point = Coordinates(1.321, 103.924) // Public Bedok forecast reference point.
        val response = clients.weatherApi.getForecast(point.latitude, point.longitude)
        val weather = WeatherDataMapper().map(response, Instant.now())
        assertEquals("Asia/Singapore", weather.timeZone.id)
        assertEquals(3, weather.upcomingRain.size)
        val regional = clients.areaForecastApi.getLatestForecast()
        val nearby = AreaForecastMatcher().findNearest(regional, point, "SG", Instant.now())
        assertNotNull("Expected a currently valid regional forecast", nearby)
        assertEquals("Bedok", nearby!!.areaName)
    }
}
