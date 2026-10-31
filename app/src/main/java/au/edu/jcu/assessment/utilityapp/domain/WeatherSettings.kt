package au.edu.jcu.assessment.utilityapp.domain

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"), FAHRENHEIT("°F");

    fun fromCelsius(value: Double): Double = if (this == CELSIUS) value else value * 9 / 5 + 32
}

enum class WindSpeedUnit(val symbol: String) {
    KILOMETRES_PER_HOUR("km/h"), METRES_PER_SECOND("m/s"), MILES_PER_HOUR("mph"), KNOTS("kn");
    fun fromKmh(value: Double): Double = when (this) {
        KILOMETRES_PER_HOUR -> value
        METRES_PER_SECOND -> value / 3.6
        MILES_PER_HOUR -> value / 1.609344
        KNOTS -> value / 1.852
    }
}

enum class PrecipitationUnit(val symbol: String) {
    MILLIMETRES("mm"), INCHES("in");
    fun fromMillimetres(value: Double): Double = if (this == MILLIMETRES) value else value / 25.4
}

enum class PressureUnit(val symbol: String) {
    HECTOPASCALS("hPa"), MILLIBARS("mbar"), INCHES_OF_MERCURY("inHg"), MILLIMETRES_OF_MERCURY("mmHg");
    fun fromHpa(value: Double): Double = when (this) {
        HECTOPASCALS, MILLIBARS -> value
        INCHES_OF_MERCURY -> value * 0.0295299830714
        MILLIMETRES_OF_MERCURY -> value * 0.750061683
    }
}

data class WeatherSettings(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val rainThresholdPercent: Int = 50,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.KILOMETRES_PER_HOUR,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MILLIMETRES,
    val pressureUnit: PressureUnit = PressureUnit.HECTOPASCALS,
    val rainNotificationsEnabled: Boolean = false
) {
    init { require(rainThresholdPercent in 10..90) }
}
