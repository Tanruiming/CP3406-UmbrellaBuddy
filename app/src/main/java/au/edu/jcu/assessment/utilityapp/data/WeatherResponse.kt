package au.edu.jcu.assessment.utilityapp.data

import com.google.gson.annotations.SerializedName

// Nullable fields preserve missing API values for validation by the data layer.
data class WeatherResponse(
    val timezone: String? = null,
    val current: CurrentWeather? = null,
    val hourly: HourlyWeather? = null
)

// Request timeformat=unixtime so timestamps are Unix seconds, independent of timezone.
data class CurrentWeather(
    val time: Long? = null,

    @SerializedName("temperature_2m")
    val temperature: Double? = null,

    @SerializedName("apparent_temperature")
    val feelsLike: Double? = null,

    @SerializedName("weather_code")
    val weatherCode: Int? = null
)

data class HourlyWeather(
    val time: List<Long?>? = null,

    @SerializedName("precipitation_probability")
    val rainProbability: List<Int?>? = null
)
