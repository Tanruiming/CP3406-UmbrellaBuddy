package au.edu.jcu.assessment.utilityapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.edu.jcu.assessment.utilityapp.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.CancellationException

sealed interface WeatherUiState {
    data object Idle : WeatherUiState
    data object Locating : WeatherUiState
    data class Loading(val location: DeviceLocation) : WeatherUiState
    data class Ready(val location: DeviceLocation, val report: WeatherReport) : WeatherUiState
    data class Error(val problem: WeatherProblem) : WeatherUiState
}

enum class WeatherProblem {
    PERMISSION_REQUIRED, LOCATION_DISABLED, LOCATION_TIMEOUT, LOCATION_UNAVAILABLE,
    NETWORK, SERVICE, INVALID_DATA, UNEXPECTED
}

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val locationProvider: LocationProvider,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {
    private val mutableState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val state: StateFlow<WeatherUiState> = mutableState.asStateFlow()
    private var activeLoad: Job? = null
    private var generation = 0

    fun refresh() {
        activeLoad?.cancel()
        val request = ++generation
        mutableState.value = WeatherUiState.Locating
        activeLoad = viewModelScope.launch {
            try {
                val result = locationProvider.getCurrentLocation()
                currentCoroutineContext().ensureActive()
                if (request != generation) return@launch
                when (result) {
                    is LocationResult.Failure -> mutableState.value = WeatherUiState.Error(when (result.reason) {
                        LocationFailure.PERMISSION_REQUIRED -> WeatherProblem.PERMISSION_REQUIRED
                        LocationFailure.DISABLED -> WeatherProblem.LOCATION_DISABLED
                        LocationFailure.TIMEOUT -> WeatherProblem.LOCATION_TIMEOUT
                        LocationFailure.UNAVAILABLE -> WeatherProblem.LOCATION_UNAVAILABLE
                    })
                    is LocationResult.Success -> {
                        mutableState.value = WeatherUiState.Loading(result.location)
                        val weather = repository.load(result.location.coordinates, result.location.countryCode)
                        currentCoroutineContext().ensureActive()
                        if (request != generation) return@launch
                        mutableState.value = when (weather) {
                            is WeatherLoadResult.Success -> WeatherUiState.Ready(result.location, weather.report)
                            is WeatherLoadResult.Failure -> WeatherUiState.Error(when (weather.reason) {
                                WeatherFailure.NETWORK -> WeatherProblem.NETWORK
                                WeatherFailure.SERVICE -> WeatherProblem.SERVICE
                                WeatherFailure.INVALID_DATA -> WeatherProblem.INVALID_DATA
                                WeatherFailure.UNEXPECTED -> WeatherProblem.UNEXPECTED
                            })
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (request == generation) mutableState.value = WeatherUiState.Error(WeatherProblem.UNEXPECTED)
            }
        }
    }

    fun permissionDenied() {
        ++generation
        activeLoad?.cancel()
        mutableState.value = WeatherUiState.Error(WeatherProblem.PERMISSION_REQUIRED)
    }

    fun onResume(hasPermission: Boolean) {
        if (!hasPermission && state.value != WeatherUiState.Idle) permissionDenied()
        else if (hasPermission && state.value == WeatherUiState.Error(WeatherProblem.PERMISSION_REQUIRED)) refresh()
        else if (hasPermission && (state.value as? WeatherUiState.Ready)?.report?.let {
                clock.instant() >= it.fetchedAt.plusSeconds(600)
            } == true) refresh()
    }

    // Stop foreground location/network work when the app leaves the screen, retaining completed data.
    fun cancelActiveLoad() {
        if (state.value is WeatherUiState.Locating || state.value is WeatherUiState.Loading) {
            ++generation
            activeLoad?.cancel()
            mutableState.value = WeatherUiState.Idle
        }
    }
}
