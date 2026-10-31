package au.edu.jcu.assessment.utilityapp.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import au.edu.jcu.assessment.utilityapp.UtilityApp
import au.edu.jcu.assessment.utilityapp.domain.*
import au.edu.jcu.assessment.utilityapp.ui.theme.MobileTech_UtilityAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupedUnitPickersAndThresholdUpdateSettings() {
        var preferences by mutableStateOf(WeatherSettings())
        compose.setContent { MobileTech_UtilityAppTheme {
            SettingsScreen(preferences, "Bedok", { preferences = it }, { preferences = preferences.copy(rainNotificationsEnabled = it) })
        } }
        snapshot("settings-units-top-test.png")
        choose("Temperature", "Fahrenheit · °F")
        choose("Wind speed", "Miles per hour · mph")
        choose("Precipitation", "Inches · in")
        choose("Pressure", "Inches of mercury · inHg")
        compose.onNodeWithContentDescription("Rain chance threshold").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        assertEquals(WeatherSettings(TemperatureUnit.FAHRENHEIT, 30, WindSpeedUnit.MILES_PER_HOUR,
            PrecipitationUnit.INCHES, PressureUnit.INCHES_OF_MERCURY), preferences)
        compose.onNodeWithText("30%").assertIsDisplayed()
        snapshot("settings-units-bottom-test.png")
    }

    @Test fun preferencesImmediatelyChangeWeatherValuesAndAdvice() {
        var preferences by mutableStateOf(WeatherSettings())
        val now = Instant.now()
        val point = Coordinates(1.321, 103.924)
        val location = DeviceLocation(point, 100f, false, now, "Bedok", "SG")
        val report = WeatherReport(point, WeatherSnapshot(ZoneId.of("Asia/Singapore"), now, 28.0, 31.0, 3,
            (0..2).map { HourlyRainForecast(now.minusSeconds(1800).plusSeconds(it * 3600L),
                now.plusSeconds(1800).plusSeconds(it * 3600L), 40, 25.4) },
            windSpeedKmh = 36.0, seaLevelPressureHpa = 1013.25),
            null, RegionalForecastStatus.UNAVAILABLE, now)
        compose.setContent { MobileTech_UtilityAppTheme {
            UtilityApp(WeatherUiState.Ready(location, report), {}, {}, {}, {}, preferences, { preferences = it })
        } }
        compose.onNodeWithText("Rain looks less likely").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        choose("Temperature", "Fahrenheit · °F")
        choose("Wind speed", "Metres per second · m/s")
        choose("Precipitation", "Inches · in")
        choose("Pressure", "Inches of mercury · inHg")
        compose.onNodeWithContentDescription("Rain chance threshold").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.onNodeWithText("Weather").performClick()
        compose.onNodeWithText("Bring an umbrella").assertIsDisplayed()
        compose.onNodeWithText("82°F").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("88°F").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Wind speed"))
        compose.onNodeWithText("10.0 m/s").assertIsDisplayed()
        compose.onNodeWithText("29.92 inHg").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Upcoming rain"))
        compose.onAllNodesWithText("1.00 in")[0].assertIsDisplayed()
        snapshot("weather-units-test.png")
    }

    @Test fun notificationToggleAndBlockedPermissionStateRemainClear() {
        var enabled = false
        var settingsOpens = 0
        compose.setContent { MobileTech_UtilityAppTheme {
            SettingsScreen(WeatherSettings(), null, {}, { enabled = it }, true, { settingsOpens++ })
        } }
        compose.onNodeWithContentDescription("Umbrella notifications").performClick()
        assertTrue(enabled)
        compose.onNodeWithText("Notification settings").performScrollTo().performClick()
        assertEquals(1, settingsOpens)
        // Until permission/consent succeeds, the displayed switch remains off.
        compose.onNodeWithContentDescription("Umbrella notifications").assertIsOff()
    }

    @Test fun largeTextCanReachUnitPickersAndThreshold() {
        var preferences by mutableStateOf(WeatherSettings())
        compose.setContent { MobileTech_UtilityAppTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                SettingsScreen(preferences, null, { preferences = it }, {})
            }
        } }
        choose("Temperature", "Fahrenheit · °F")
        compose.onNodeWithText("°F").assertIsDisplayed()
        choose("Pressure", "Millimetres of mercury · mmHg")
        compose.onNodeWithContentDescription("Rain chance threshold").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        compose.onNodeWithText("80%").assertIsDisplayed()
    }

    @Test fun darkSettingsRemainReadableAndNotificationSwitchStartsOff() {
        compose.setContent { MobileTech_UtilityAppTheme(darkTheme = true) {
            SettingsScreen(WeatherSettings(), null, {}, {})
        } }
        compose.onNodeWithContentDescription("Umbrella notifications").assertIsOff()
        compose.onNodeWithText("Temperature").assertIsDisplayed()
        compose.onNodeWithText("Pressure").assertIsDisplayed()
        snapshot("settings-dark-test.png")
    }

    private fun choose(row: String, option: String) {
        compose.onNodeWithText(row).performScrollTo().performClick()
        compose.onNodeWithText(option).performScrollTo().performClick()
    }

    private fun snapshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
