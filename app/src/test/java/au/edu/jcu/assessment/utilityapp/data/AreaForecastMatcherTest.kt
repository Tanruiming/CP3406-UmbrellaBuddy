package au.edu.jcu.assessment.utilityapp.data

import au.edu.jcu.assessment.utilityapp.domain.Coordinates
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AreaForecastMatcherTest {
    private val matcher = AreaForecastMatcher()
    private val now = Instant.parse("2026-10-31T08:30:00Z")
    private val location = Coordinates(1.321, 103.924)

    private fun item() = AreaForecastItem(
        updateTimestamp = "2026-10-31T16:15:00+08:00",
        timestamp = "2026-10-31T16:00:00+08:00",
        validPeriod = ForecastValidityPeriod("2026-10-31T16:00:00+08:00", "2026-10-31T18:00:00+08:00"),
        forecasts = listOf(RegionalForecast("Bedok", "Showers"), RegionalForecast("City", "Cloudy"))
    )

    private fun response(items: List<AreaForecastItem?> = listOf(item())) = AreaForecastResponse(
        code = 0,
        data = AreaForecastData(
            areaMetadata = listOf(
                ForecastAreaMetadata("City", ForecastAreaLocation(1.292, 103.844)),
                ForecastAreaMetadata("Bedok", ForecastAreaLocation(1.321, 103.924))
            ),
            items = items
        )
    )

    @Test fun choosesNearestPointAndPreservesDescriptionAndValidity() {
        val result = matcher.findNearest(response(), location, "SG", now)!!
        assertEquals("Bedok", result.areaName)
        assertEquals("Showers", result.description)
        assertEquals(0.0, result.distanceKm, 0.0001)
        assertEquals(Instant.parse("2026-10-31T10:00:00Z"), result.validUntil)
        assertEquals(Instant.parse("2026-10-31T08:15:00Z"), result.updatedAt)
    }

    @Test fun computesDistanceToRepresentativePointInKilometres() {
        val result = matcher.findNearest(response(), Coordinates(1.322, 103.924), "sg", now)!!
        assertEquals("Bedok", result.areaName)
        assertEquals(0.1112, result.distanceKm, 0.001)
    }

    @Test fun doesNotUseSingaporeForecastForUnknownOrOtherCountry() {
        for (country in listOf(null, "MY", "AU")) {
            assertNull(matcher.findNearest(response(), location, country, now))
        }
    }

    @Test fun rejectsApiErrorsAndMissingData() {
        for (source in listOf(response().copy(code = 1), response().copy(errorMessage = "Unavailable"),
            AreaForecastResponse(code = 0))) {
            assertNull(matcher.findNearest(source, location, "SG", now))
        }
    }

    @Test fun rejectsExpiredFutureAndMalformedValidity() {
        for (period in listOf(
            ForecastValidityPeriod("2026-10-31T14:00:00+08:00", "2026-10-31T16:30:00+08:00"),
            ForecastValidityPeriod("2026-10-31T17:00:00+08:00", "2026-10-31T19:00:00+08:00"),
            ForecastValidityPeriod("bad-date", "2026-10-31T18:00:00+08:00")
        )) {
            assertNull(matcher.findNearest(response(listOf(item().copy(validPeriod = period))), location, "SG", now))
        }
    }

    @Test fun selectsLatestValidUpdateRegardlessOfResponseOrder() {
        val newer = item().copy(updateTimestamp = "2026-10-31T16:25:00+08:00",
            forecasts = listOf(RegionalForecast("Bedok", "Thundery Showers")))
        val result = matcher.findNearest(response(listOf(newer, item())), location, "SG", now)!!
        assertEquals("Thundery Showers", result.description)
    }

    @Test fun skipsInvalidAndFutureItems() {
        val source = response(listOf(null, item().copy(updateTimestamp = "bad-date"),
            item().copy(updateTimestamp = "2026-10-31T17:00:00+08:00"), item()))
        assertEquals("Bedok", matcher.findNearest(source, location, "SG", now)?.areaName)
    }

    @Test fun skipsInvalidMetadataAndAreasWithNoDescription() {
        val source = response().copy(data = response().data!!.copy(areaMetadata = listOf(
            null,
            ForecastAreaMetadata("Bedok", ForecastAreaLocation(Double.NaN, 103.924)),
            ForecastAreaMetadata("Unknown", ForecastAreaLocation(1.321, 103.924)),
            ForecastAreaMetadata("City", ForecastAreaLocation(1.292, 103.844))
        )))
        assertEquals("City", matcher.findNearest(source, location, "SG", now)?.areaName)
    }

    @Test fun missingDescriptionDoesNotBecomeFineWeather() {
        val source = response(listOf(item().copy(forecasts = listOf(RegionalForecast("Bedok", " ")))))
        assertNull(matcher.findNearest(source, location, "SG", now))
    }
}
