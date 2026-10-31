package au.edu.jcu.assessment.utilityapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import au.edu.jcu.assessment.utilityapp.domain.LocationFailure
import au.edu.jcu.assessment.utilityapp.domain.LocationResult
import au.edu.jcu.assessment.utilityapp.location.AndroidLocationProvider
import au.edu.jcu.assessment.utilityapp.location.hasLocationPermission
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesWithApplicationContainerAndNewWeatherScreen() {
        assertTrue(compose.activity.application is UmbrellaApplication)
        compose.onNodeWithText("Use my location").assertIsDisplayed()
        compose.onNodeWithText("Increment").assertDoesNotExist()
    }

    @Test fun navigationSurvivesActivityRecreation() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Manage location access").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Manage location access").assertIsDisplayed()
        compose.onNodeWithText("Weather").performClick()
        compose.onNodeWithText("Use my location").assertIsDisplayed()
    }

    @Test fun platformLocationProviderRequiresPermissionWithoutPrompting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeFalse("Permission was already granted by the device user", context.hasLocationPermission())
        assertEquals(LocationResult.Failure(LocationFailure.PERMISSION_REQUIRED), AndroidLocationProvider(context).getCurrentLocation())
    }
}
