package au.edu.jcu.assessment.utilityapp.data

import com.google.gson.annotations.SerializedName

// Transfer objects for data.gov.sg's v2 two-hour regional forecast response.
data class AreaForecastResponse(
    val code: Int? = null,

    @SerializedName("errorMsg")
    val errorMessage: String? = null,

    val data: AreaForecastData? = null
)

data class AreaForecastData(
    @SerializedName("area_metadata")
    val areaMetadata: List<ForecastAreaMetadata?>? = null,

    val items: List<AreaForecastItem?>? = null,
    val paginationToken: String? = null
)

data class ForecastAreaMetadata(
    val name: String? = null,

    @SerializedName("label_location")
    val labelLocation: ForecastAreaLocation? = null
)

// These are representative forecast points, not precise district boundaries.
data class ForecastAreaLocation(
    val latitude: Double? = null,
    val longitude: Double? = null
)

// The regional API uses ISO 8601 timestamp strings, unlike the Unix weather model.
data class AreaForecastItem(
    @SerializedName("update_timestamp")
    val updateTimestamp: String? = null,

    val timestamp: String? = null,

    @SerializedName("valid_period")
    val validPeriod: ForecastValidityPeriod? = null,

    val forecasts: List<RegionalForecast?>? = null
)

data class ForecastValidityPeriod(
    val start: String? = null,
    val end: String? = null,
    val text: String? = null
)

// Missing descriptions stay null so the data layer can report unavailable forecasts.
data class RegionalForecast(
    val area: String? = null,
    val forecast: String? = null
)
