package au.edu.jcu.assessment.utilityapp.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import au.edu.jcu.assessment.utilityapp.domain.*
import au.edu.jcu.assessment.utilityapp.ui.theme.MobileTech_UtilityAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class WeatherScreenTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-10-31T08:30:00Z")
    private val location = DeviceLocation(Coordinates(1.321, 103.924), 1500f, true, now, "East Coast", "SG")
    private fun report() = WeatherReport(location.coordinates,
        WeatherSnapshot(ZoneId.of("Asia/Singapore"), now, 28.0, 31.0, 3,
            listOf(30, 70, 60).mapIndexed { index, chance ->
                HourlyRainForecast(now.minusSeconds(1800).plusSeconds(index * 3600L),
                    now.plusSeconds(1800).plusSeconds(index * 3600L), chance)
            }),
        NearbyAreaForecast("Bedok", "Showers", 1.2, now.minusSeconds(1800), now.minusSeconds(900),
            now.minusSeconds(1800), now.plusSeconds(5400)),
        RegionalForecastStatus.AVAILABLE, now)

    @Test fun initialScreenRequestsLocationOnlyAfterButtonTap() {
        var requests = 0
        compose.setContent { MobileTech_UtilityAppTheme {
            WeatherScreen(WeatherUiState.Idle, now, { requests++ }, {}, {}, {})
        } }
        assertEquals(0, requests)
        compose.onNodeWithText("Use my location").performClick()
        assertEquals(1, requests)
        compose.onNodeWithText("Increment").assertDoesNotExist()
    }

    @Test fun permissionErrorOffersRetryAndAppSettings() {
        var retries = 0
        var settings = 0
        compose.setContent { MobileTech_UtilityAppTheme {
            WeatherScreen(WeatherUiState.Error(WeatherProblem.PERMISSION_REQUIRED), now,
                { retries++ }, { settings++ }, {}, {})
        } }
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.onNodeWithText("App settings").performScrollTo().performClick()
        assertEquals(1, retries)
        assertEquals(1, settings)
    }

    @Test fun disabledLocationOffersSystemSettings() {
        var settings = 0
        compose.setContent { MobileTech_UtilityAppTheme {
            WeatherScreen(WeatherUiState.Error(WeatherProblem.LOCATION_DISABLED), now, {}, {}, { settings++ }, {})
        } }
        compose.onNodeWithText("Location settings").performScrollTo().performClick()
        assertEquals(1, settings)
    }

    @Test fun loadingCanBeCancelled() {
        var cancelled = 0
        compose.setContent { MobileTech_UtilityAppTheme {
            WeatherScreen(WeatherUiState.Loading(location), now, {}, {}, {}, { cancelled++ })
        } }
        compose.onNodeWithText("Checking the forecast").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test fun weatherScreenShowsAdviceHourlyRainAndNearbyRegion() {
        compose.setContent { MobileTech_UtilityAppTheme {
            WeatherScreen(WeatherUiState.Ready(location, report()), now, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Bring an umbrella").assertIsDisplayed()
        compose.onNodeWithText("Refresh").assertIsDisplayed()
        snapshot("weather-ready-test.png")
        compose.onNodeWithText("Upcoming rain").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("70%").assertIsDisplayed()
        snapshot("weather-hourly-test.png")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Nearby regional forecast"))
        compose.onNodeWithText("Bedok").assertIsDisplayed()
        compose.onNodeWithText("Showers").assertIsDisplayed()
        snapshot("weather-regional-test.png")
    }

    @Test fun expiredDataOffersRefreshAndDoesNotShowOldRegionalDescription() {
        val old = report().copy(fetchedAt = now.minusSeconds(601), nearbyArea = report().nearbyArea!!.copy(validUntil = now))
        compose.setContent { MobileTech_UtilityAppTheme {
            WeatherScreen(WeatherUiState.Ready(location, old), now, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Keep an eye on the sky").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Nearby regional forecast"))
        compose.onNodeWithText("This regional forecast has expired. Refresh to check for an update.").assertIsDisplayed()
        compose.onNodeWithText("Showers").assertDoesNotExist()
    }

    @Test fun basicWeatherRemainsWhenRegionalForecastIsUnavailable() {
        val basic = report().copy(nearbyArea = null, regionalStatus = RegionalForecastStatus.COUNTRY_UNKNOWN)
        compose.setContent { MobileTech_UtilityAppTheme {
            WeatherScreen(WeatherUiState.Ready(location, basic), now, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Temperature").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("28°C").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Nearby regional forecast"))
        compose.onNodeWithText("Your country could not be confirmed. The weather above still uses your current coordinates.").assertIsDisplayed()
    }

    @Test fun largeTextStillAllowsReachingRainAndRegionCards() {
        compose.setContent { MobileTech_UtilityAppTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                WeatherScreen(WeatherUiState.Ready(location, report()), now, {}, {}, {}, {})
            }
        } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Upcoming rain"))
        compose.onNodeWithText("70%").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Nearby regional forecast"))
        compose.onNodeWithText("Bedok").assertIsDisplayed()
    }

    private fun snapshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
