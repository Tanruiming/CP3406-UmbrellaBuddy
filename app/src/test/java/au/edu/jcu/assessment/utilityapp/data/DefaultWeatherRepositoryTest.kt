package au.edu.jcu.assessment.utilityapp.data

import au.edu.jcu.assessment.utilityapp.domain.Coordinates
import au.edu.jcu.assessment.utilityapp.domain.RegionalForecastStatus
import au.edu.jcu.assessment.utilityapp.domain.WeatherFailure
import au.edu.jcu.assessment.utilityapp.domain.WeatherLoadResult
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CancellationException

class DefaultWeatherRepositoryTest {
    private val now = Instant.parse("2026-10-31T08:30:00Z")
    private val location = Coordinates(1.321, 103.924)

    private fun weatherResponse() = WeatherResponse(
        timezone = "Asia/Singapore",
        current = CurrentWeather(now.epochSecond, 28.0, 31.0, 61),
        hourly = HourlyWeather(
            (1L..5L).map { now.epochSecond - 1800 + it * 3600 },
            listOf(20, 40, 60, 80, 90)
        )
    )

    private fun regionalResponse() = AreaForecastResponse(code = 0, data = AreaForecastData(
        areaMetadata = listOf(ForecastAreaMetadata("Bedok", ForecastAreaLocation(1.321, 103.924))),
        items = listOf(AreaForecastItem(
            updateTimestamp = "2026-10-31T16:15:00+08:00",
            timestamp = "2026-10-31T16:00:00+08:00",
            validPeriod = ForecastValidityPeriod("2026-10-31T16:00:00+08:00", "2026-10-31T18:00:00+08:00"),
            forecasts = listOf(RegionalForecast("Bedok", "Showers"))
        ))
    ))

    private class FakeWeatherApi(private val answer: suspend () -> WeatherResponse) : WeatherApi {
        var receivedLocation: Coordinates? = null
        override suspend fun getForecast(latitude: Double, longitude: Double, current: String,
            hourly: String, forecastDays: Int, timezone: String, timeFormat: String,
            temperatureUnit: String, windSpeedUnit: String, precipitationUnit: String): WeatherResponse {
            receivedLocation = Coordinates(latitude, longitude)
            return answer()
        }
    }

    private class FakeAreaApi(private val answer: suspend () -> AreaForecastResponse) : AreaForecastApi {
        var calls = 0
        override suspend fun getLatestForecast(): AreaForecastResponse {
            calls++
            return answer()
        }
    }

    private fun repository(weather: WeatherApi, area: AreaForecastApi,
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC)) =
        DefaultWeatherRepository(weather, area, clock = clock)

    @Test fun combinesValidatedWeatherAndNearbyForecastForRequestedCoordinates() = runBlocking {
        val weather = FakeWeatherApi { weatherResponse() }
        val area = FakeAreaApi { regionalResponse() }
        val result = repository(weather, area).load(location, " sg ") as WeatherLoadResult.Success
        assertEquals(location, weather.receivedLocation)
        assertEquals(location, result.report.location)
        assertEquals(28.0, result.report.weather.temperatureCelsius, 0.0)
        assertEquals("Bedok", result.report.nearbyArea?.areaName)
        assertEquals(RegionalForecastStatus.AVAILABLE, result.report.regionalStatus)
        assertEquals(now, result.report.fetchedAt)
        assertEquals(1, area.calls)
    }

    @Test fun preservesBasicWeatherWhenRegionalNetworkOrParsingFails() = runBlocking {
        for (failure in listOf(IOException("Offline"), JsonSyntaxException("Invalid JSON"))) {
            val result = repository(FakeWeatherApi { weatherResponse() }, FakeAreaApi { throw failure })
                .load(location, "SG") as WeatherLoadResult.Success
            assertEquals(28.0, result.report.weather.temperatureCelsius, 0.0)
            assertNull(result.report.nearbyArea)
            assertEquals(RegionalForecastStatus.UNAVAILABLE, result.report.regionalStatus)
        }
    }

    @Test fun unavailableOrExpiredRegionalDataDoesNotBecomeAForecast() = runBlocking {
        val expired = regionalResponse().let { source -> source.copy(data = source.data!!.copy(
            items = source.data.items!!.map { it!!.copy(validPeriod =
                ForecastValidityPeriod("2026-10-31T14:00:00+08:00", "2026-10-31T16:00:00+08:00")) }
        )) }
        for (response in listOf(AreaForecastResponse(code = 1), AreaForecastResponse(code = 0), expired)) {
            val result = repository(FakeWeatherApi { weatherResponse() }, FakeAreaApi { response })
                .load(location, "SG") as WeatherLoadResult.Success
            assertNull(result.report.nearbyArea)
            assertEquals(RegionalForecastStatus.UNAVAILABLE, result.report.regionalStatus)
        }
    }

    @Test fun skipsRegionalRequestsOutsideSingaporeOrWithUnknownCountry() = runBlocking {
        for (country in listOf("AU", "MY", null, " ")) {
            val area = FakeAreaApi { throw AssertionError("Regional API should not be called") }
            val result = repository(FakeWeatherApi { weatherResponse() }, area)
                .load(location, country) as WeatherLoadResult.Success
            val status = if (country.isNullOrBlank()) RegionalForecastStatus.COUNTRY_UNKNOWN
                else RegionalForecastStatus.OUTSIDE_SINGAPORE
            assertEquals(status, result.report.regionalStatus)
            assertNull(result.report.nearbyArea)
            assertEquals(0, area.calls)
        }
    }

    @Test fun classifiesBasicWeatherFailuresAndSkipsOptionalRequest() = runBlocking {
        val failures = listOf(
            IOException("Offline") to WeatherFailure.NETWORK,
            HttpException(Response.error<WeatherResponse>(503, ResponseBody.create(null, "Unavailable"))) to WeatherFailure.SERVICE,
            JsonSyntaxException("Invalid JSON") to WeatherFailure.INVALID_DATA,
            IllegalStateException("Unexpected failure") to WeatherFailure.UNEXPECTED
        )
        for ((failure, reason) in failures) {
            val area = FakeAreaApi { regionalResponse() }
            val result = repository(FakeWeatherApi { throw failure }, area).load(location, "SG")
            assertEquals(WeatherLoadResult.Failure(reason), result)
            assertEquals(0, area.calls)
        }
    }

    @Test fun malformedWeatherIsInvalidDataRatherThanASuccess() = runBlocking {
        for (response in listOf(WeatherResponse(), weatherResponse().copy(timezone = "Invalid/Zone"))) {
            val area = FakeAreaApi { regionalResponse() }
            val result = repository(FakeWeatherApi { response }, area).load(location, "SG")
            assertEquals(WeatherLoadResult.Failure(WeatherFailure.INVALID_DATA), result)
            assertEquals(0, area.calls)
        }
    }

    @Test fun propagatesCancellationFromEitherApi() {
        for (cancelBasic in listOf(true, false)) {
            val cancellation = CancellationException("Request cancelled")
            val weather = FakeWeatherApi { if (cancelBasic) throw cancellation else weatherResponse() }
            val area = FakeAreaApi { throw cancellation }
            val caught = assertThrows(CancellationException::class.java) {
                runBlocking { repository(weather, area).load(location, "SG") }
            }
            assertSame(cancellation, caught)
        }
    }

    @Test fun cancelledContextDoesNotReturnNetworkFailure() {
        val job = Job()
        val weather = FakeWeatherApi { job.cancel(); throw IOException("Cancelled connection") }
        assertThrows(CancellationException::class.java) {
            runBlocking(job) { repository(weather, FakeAreaApi { regionalResponse() }).load(location, "SG") }
        }
    }

    @Test fun doesNotReuseRegionalDataFromPreviousLoad() = runBlocking {
        var regionalAvailable = true
        val area = FakeAreaApi { if (regionalAvailable) regionalResponse() else throw IOException("Offline") }
        val repository = repository(FakeWeatherApi { weatherResponse() }, area)
        val first = repository.load(location, "SG") as WeatherLoadResult.Success
        assertNotNull(first.report.nearbyArea)
        regionalAvailable = false
        val second = repository.load(location, "SG") as WeatherLoadResult.Success
        assertNull(second.report.nearbyArea)
        assertEquals(RegionalForecastStatus.UNAVAILABLE, second.report.regionalStatus)
    }

    @Test fun rechecksHourlySelectionAndRegionalExpiryAfterOptionalRequest() = runBlocking {
        val clock = MutableClock(now)
        val area = FakeAreaApi { clock.time = now.plusSeconds(5400); regionalResponse() }
        val result = repository(FakeWeatherApi { weatherResponse() }, area, clock)
            .load(location, "SG") as WeatherLoadResult.Success
        assertEquals(listOf(60, 80, 90), result.report.weather.upcomingRain.map { it.probabilityPercent })
        assertEquals(RegionalForecastStatus.UNAVAILABLE, result.report.regionalStatus)
        assertEquals(clock.time, result.report.fetchedAt)
    }

    private class MutableClock(var time: Instant) : Clock() {
        override fun instant(): Instant = time
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(time, zone)
    }
}
