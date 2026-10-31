package au.edu.jcu.assessment.utilityapp.data

import au.edu.jcu.assessment.utilityapp.domain.Coordinates
import au.edu.jcu.assessment.utilityapp.domain.RegionalForecastStatus
import au.edu.jcu.assessment.utilityapp.domain.WeatherFailure
import au.edu.jcu.assessment.utilityapp.domain.WeatherLoadResult
import au.edu.jcu.assessment.utilityapp.domain.WeatherReport
import au.edu.jcu.assessment.utilityapp.domain.WeatherRepository
import com.google.gson.JsonParseException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.DateTimeException
import java.util.concurrent.CancellationException

class DefaultWeatherRepository(
    private val weatherApi: WeatherApi,
    private val areaForecastApi: AreaForecastApi,
    private val weatherMapper: WeatherDataMapper = WeatherDataMapper(),
    private val areaMatcher: AreaForecastMatcher = AreaForecastMatcher(),
    private val clock: Clock = Clock.systemUTC()
) : WeatherRepository {
    override suspend fun load(location: Coordinates, countryCode: String?): WeatherLoadResult = try {
        currentCoroutineContext().ensureActive()
        val weatherResponse = weatherApi.getForecast(location.latitude, location.longitude)
        // Fail early on unusable basic weather before making an optional regional request.
        weatherMapper.map(weatherResponse, clock.instant())

        val country = countryCode?.trim()?.takeIf { it.isNotEmpty() }
        val isSingapore = country.equals("SG", ignoreCase = true)
        val areaResponse = if (isSingapore) fetchRegionalForecast() else null
        currentCoroutineContext().ensureActive()
        val fetchedAt = clock.instant()
        // The optional request may cross an hour boundary, so select periods again at completion.
        val weather = weatherMapper.map(weatherResponse, fetchedAt)
        val nearbyArea = areaResponse?.let {
            areaMatcher.findNearest(it, location, country, fetchedAt)
        }
        val regionalStatus = when {
            country == null -> RegionalForecastStatus.COUNTRY_UNKNOWN
            !isSingapore -> RegionalForecastStatus.OUTSIDE_SINGAPORE
            nearbyArea == null -> RegionalForecastStatus.UNAVAILABLE
            else -> RegionalForecastStatus.AVAILABLE
        }
        WeatherLoadResult.Success(WeatherReport(location, weather, nearbyArea, regionalStatus, fetchedAt))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // Cancellation takes priority if an API reports another exception while being cancelled.
        currentCoroutineContext().ensureActive()
        val reason = when (failure) {
            is IOException -> WeatherFailure.NETWORK
            is HttpException -> WeatherFailure.SERVICE
            is JsonParseException, is IllegalArgumentException, is DateTimeException -> WeatherFailure.INVALID_DATA
            else -> WeatherFailure.UNEXPECTED
        }
        WeatherLoadResult.Failure(reason)
    }

    private suspend fun fetchRegionalForecast(): AreaForecastResponse? = try {
        areaForecastApi.getLatestForecast()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        // Optional regional service failures must not discard validated basic weather.
        null
    }
}
