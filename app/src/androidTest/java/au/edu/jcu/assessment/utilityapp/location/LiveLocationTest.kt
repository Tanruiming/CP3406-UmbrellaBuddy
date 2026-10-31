package au.edu.jcu.assessment.utilityapp.location

import android.location.Location
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import au.edu.jcu.assessment.utilityapp.data.DefaultWeatherRepository
import au.edu.jcu.assessment.utilityapp.data.NetworkClients
import au.edu.jcu.assessment.utilityapp.domain.*
import au.edu.jcu.assessment.utilityapp.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

// Explicit opt-in emulator check. Set the public test point before running; never grants permission.
@RunWith(AndroidJUnit4::class)
class LiveLocationTest {
    @Test fun platformLocationAndLiveWeatherFollowTheSelectedTestPoint() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in live location verification", args.getString("liveLocation") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("The device user must allow location first", context.hasLocationPermission())
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val result = withContext(Dispatchers.Main.immediate) { AndroidLocationProvider(context).getCurrentLocation() }
            assertTrue("Expected a fresh location, got $result", result is LocationResult.Success)
            val fix = (result as LocationResult.Success).location
            val expectedLatitude = args.getString("expectedLatitude")!!.toDouble()
            val expectedLongitude = args.getString("expectedLongitude")!!.toDouble()
            val distances = FloatArray(1)
            Location.distanceBetween(expectedLatitude, expectedLongitude, fix.coordinates.latitude, fix.coordinates.longitude, distances)
            assertTrue("Device fix did not follow the selected test point", distances[0] < 1000f)
            assertEquals("SG", fix.countryCode)
            val clients = NetworkClients()
            val weather = DefaultWeatherRepository(clients.weatherApi, clients.areaForecastApi).load(fix.coordinates, fix.countryCode)
            assertTrue("Expected live weather for the device location, got $weather", weather is WeatherLoadResult.Success)
            val report = (weather as WeatherLoadResult.Success).report
            assertEquals(fix.coordinates, report.location)
            assertEquals("Asia/Singapore", report.weather.timeZone.id)
            assertNotNull("Expected a valid nearby Singapore nowcast", report.nearbyArea)
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("verifiedArea", fix.areaName)
                putString("verifiedCountry", fix.countryCode)
                putString("temperatureCelsius", report.weather.temperatureCelsius.toString())
                putString("nearbyForecastArea", report.nearbyArea?.areaName)
            })
        } finally {
            scenario.close()
        }
    }
}
