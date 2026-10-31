package au.edu.jcu.assessment.utilityapp.ui

import au.edu.jcu.assessment.utilityapp.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-31T08:30:00Z")
    private val location = DeviceLocation(Coordinates(1.321, 103.924), 100f, true, now, "Bedok", "SG")
    private val report = WeatherReport(location.coordinates,
        WeatherSnapshot(ZoneOffset.UTC, now, 28.0, 31.0, 3, emptyList()),
        null, RegionalForecastStatus.UNAVAILABLE, now)

    @Before fun setMainDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    private class FakeLocation(var answer: suspend () -> LocationResult) : LocationProvider {
        var calls = 0
        override suspend fun getCurrentLocation(): LocationResult { calls++; return answer() }
    }
    private class FakeRepository(var answer: suspend () -> WeatherLoadResult) : WeatherRepository {
        var calls = 0
        var coordinates: Coordinates? = null
        var country: String? = null
        override suspend fun load(location: Coordinates, countryCode: String?): WeatherLoadResult {
            calls++; coordinates = location; country = countryCode
            return answer()
        }
    }
    private fun model(repository: WeatherRepository, provider: LocationProvider) =
        WeatherViewModel(repository, provider, Clock.fixed(now, ZoneOffset.UTC))

    @Test fun progressesThroughLocationAndWeatherLoadingToReady() = runTest {
        val fix = CompletableDeferred<LocationResult>()
        val forecast = CompletableDeferred<WeatherLoadResult>()
        val repository = FakeRepository { forecast.await() }
        val model = model(repository, FakeLocation { fix.await() })
        assertEquals(WeatherUiState.Idle, model.state.value)
        model.refresh()
        runCurrent()
        assertEquals(WeatherUiState.Locating, model.state.value)
        fix.complete(LocationResult.Success(location))
        runCurrent()
        assertEquals(WeatherUiState.Loading(location), model.state.value)
        forecast.complete(WeatherLoadResult.Success(report))
        runCurrent()
        assertEquals(WeatherUiState.Ready(location, report), model.state.value)
        assertEquals(location.coordinates, repository.coordinates)
        assertEquals("SG", repository.country)
    }

    @Test fun locationFailuresDoNotRequestWeather() = runTest {
        val cases = mapOf(LocationFailure.PERMISSION_REQUIRED to WeatherProblem.PERMISSION_REQUIRED,
            LocationFailure.DISABLED to WeatherProblem.LOCATION_DISABLED,
            LocationFailure.TIMEOUT to WeatherProblem.LOCATION_TIMEOUT,
            LocationFailure.UNAVAILABLE to WeatherProblem.LOCATION_UNAVAILABLE)
        for ((reason, problem) in cases) {
            val repository = FakeRepository { WeatherLoadResult.Success(report) }
            val model = model(repository, FakeLocation { LocationResult.Failure(reason) })
            model.refresh(); runCurrent()
            assertEquals(WeatherUiState.Error(problem), model.state.value)
            assertEquals(0, repository.calls)
        }
    }

    @Test fun weatherFailuresBecomeAppropriateErrorStates() = runTest {
        val cases = mapOf(WeatherFailure.NETWORK to WeatherProblem.NETWORK,
            WeatherFailure.SERVICE to WeatherProblem.SERVICE,
            WeatherFailure.INVALID_DATA to WeatherProblem.INVALID_DATA,
            WeatherFailure.UNEXPECTED to WeatherProblem.UNEXPECTED)
        for ((reason, problem) in cases) {
            val model = model(FakeRepository { WeatherLoadResult.Failure(reason) }, FakeLocation { LocationResult.Success(location) })
            model.refresh(); runCurrent()
            assertEquals(WeatherUiState.Error(problem), model.state.value)
        }
    }

    @Test fun newerRefreshCannotBeOverwrittenByCancelledOlderRequest() = runTest {
        var calls = 0
        val repository = FakeRepository {
            calls++
            if (calls == 1) withContext(NonCancellable) { delay(1000); WeatherLoadResult.Failure(WeatherFailure.NETWORK) }
            else WeatherLoadResult.Success(report)
        }
        val model = model(repository, FakeLocation { LocationResult.Success(location) })
        model.refresh(); runCurrent()
        model.refresh(); runCurrent()
        assertEquals(WeatherUiState.Ready(location, report), model.state.value)
        advanceUntilIdle()
        assertEquals(WeatherUiState.Ready(location, report), model.state.value)
    }

    @Test fun leavingScreenCancelsAnActiveLocationRequest() = runTest {
        val fix = CompletableDeferred<LocationResult>()
        val repository = FakeRepository { WeatherLoadResult.Success(report) }
        val model = model(repository, FakeLocation { fix.await() })
        model.refresh(); runCurrent()
        model.cancelActiveLoad()
        fix.complete(LocationResult.Success(location)); runCurrent()
        assertEquals(WeatherUiState.Idle, model.state.value)
        assertEquals(0, repository.calls)
    }

    @Test fun permissionRevocationClearsWeatherAndStopsPendingWork() = runTest {
        val model = model(FakeRepository { WeatherLoadResult.Success(report) }, FakeLocation { LocationResult.Success(location) })
        model.refresh(); runCurrent()
        model.onResume(false)
        assertEquals(WeatherUiState.Error(WeatherProblem.PERMISSION_REQUIRED), model.state.value)
    }

    @Test fun returningAfterPermissionGrantLoadsWeather() = runTest {
        val model = model(FakeRepository { WeatherLoadResult.Success(report) }, FakeLocation { LocationResult.Success(location) })
        model.permissionDenied()
        model.onResume(true); runCurrent()
        assertEquals(WeatherUiState.Ready(location, report), model.state.value)
    }

    @Test fun returningWithExpiredDataRequestsAFreshLocation() = runTest {
        val provider = FakeLocation { LocationResult.Success(location) }
        val model = model(FakeRepository { WeatherLoadResult.Success(report.copy(fetchedAt = now.minusSeconds(601))) }, provider)
        model.refresh(); runCurrent()
        model.onResume(true); runCurrent()
        assertEquals(2, provider.calls)
    }

    @Test fun backgroundingDoesNotDiscardACompletedForecast() = runTest {
        val model = model(FakeRepository { WeatherLoadResult.Success(report) }, FakeLocation { LocationResult.Success(location) })
        model.refresh(); runCurrent()
        model.cancelActiveLoad()
        assertEquals(WeatherUiState.Ready(location, report), model.state.value)
    }

    @Test fun unexpectedProviderFailureHasARetryableError() = runTest {
        val model = model(FakeRepository { WeatherLoadResult.Success(report) }, FakeLocation { throw IllegalStateException() })
        model.refresh(); runCurrent()
        assertEquals(WeatherUiState.Error(WeatherProblem.UNEXPECTED), model.state.value)
    }
}
