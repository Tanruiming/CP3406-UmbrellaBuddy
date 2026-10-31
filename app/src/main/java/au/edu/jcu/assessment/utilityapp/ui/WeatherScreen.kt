package au.edu.jcu.assessment.utilityapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.edu.jcu.assessment.utilityapp.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(
    state: WeatherUiState,
    now: Instant,
    onLocate: () -> Unit,
    onAppSettings: () -> Unit,
    onLocationSettings: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("UMBRELLA BUDDY", style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Before you head out", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("A quick weather check for where you are.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (state) {
            WeatherUiState.Idle -> item {
                SectionCard {
                    UmbrellaDrawing(MaterialTheme.colorScheme.primary, Modifier.size(64.dp))
                    Text("Should I bring an umbrella?", style = MaterialTheme.typography.headlineSmall)
                    Text("Use your current location to see rain chances and a nearby forecast. Approximate location works too.")
                    Button(onClick = onLocate, modifier = Modifier.fillMaxWidth()) { Text("Use my location") }
                    Text("Your coordinates are sent to the weather service for this check. No background tracking.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            WeatherUiState.Locating, is WeatherUiState.Loading -> item {
                SectionCard {
                    Text(if (state is WeatherUiState.Loading) "Checking the forecast" else "Finding your location",
                        style = MaterialTheme.typography.titleLarge)
                    Text(if (state is WeatherUiState.Loading) "Getting weather for ${state.location.areaName ?: "your current area"}."
                        else "Keep location switched on. This may take a few seconds.")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            is WeatherUiState.Error -> item {
                ErrorCard(state.problem, onLocate, onAppSettings, onLocationSettings)
            }
            is WeatherUiState.Ready -> {
                val report = state.report
                val zone = report.weather.timeZone
                val upcoming = report.weather.upcomingRain.filter { it.endsAt > now }
                val stale = now < report.fetchedAt || now >= report.fetchedAt.plusSeconds(600) || upcoming.isEmpty()
                val advice = umbrellaAdvice(report, now)
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(state.location.areaName ?: "Current location", style = MaterialTheme.typography.titleLarge)
                            val accuracy = state.location.accuracyMetres
                            val distance = if (accuracy >= 1000) String.format(Locale.ENGLISH, "%.1f km", accuracy / 1000)
                                else "${accuracy.roundToInt()} m"
                            Text("${if (state.location.approximate) "Approximate" else "Device"} location · ±$distance",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = onLocate) { Text("Refresh") }
                    }
                }
                item { AdviceCard(advice, upcoming.mapNotNull { it.probabilityPercent }.maxOrNull()) }
                if (stale) item {
                    Text("This forecast needs refreshing. Tap Refresh before relying on the advice.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    SectionCard {
                        Text(weatherDescription(report.weather.weatherCode), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TemperatureMetric("Temperature", report.weather.temperatureCelsius, Modifier.weight(1f))
                            TemperatureMetric("Feels like", report.weather.feelsLikeCelsius, Modifier.weight(1f))
                        }
                        Text("Conditions at ${formatTime(report.weather.observedAt, zone)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    SectionCard {
                        Text("Upcoming rain", style = MaterialTheme.typography.titleLarge)
                        if (upcoming.isEmpty()) Text("Refresh to get upcoming hourly periods.")
                        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            upcoming.forEach { period ->
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val start = period.startsAt.atZone(zone)
                                    val end = period.endsAt.atZone(zone)
                                    val day = start.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) +
                                        if (start.toLocalDate() != end.toLocalDate()) "–${end.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))}" else ""
                                    Text(day, style = MaterialTheme.typography.labelMedium)
                                    Text("${formatTime(period.startsAt, zone)}\n– ${formatTime(period.endsAt, zone)}",
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(period.probabilityPercent?.let { "$it%" } ?: "—",
                                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                    if (period.probabilityPercent != null) LinearProgressIndicator(
                                        progress = { period.probabilityPercent / 100f }, modifier = Modifier.fillMaxWidth())
                                    Text(if (period.probabilityPercent == null) "Unavailable"
                                        else if (period.startsAt < now) "In progress" else "Hourly chance",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text("Each chance covers the full hour shown; the first hour may already be in progress.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { RegionalCard(report, now) }
                item {
                    Text("Updated ${formatTime(report.fetchedAt, zone)} · ${zone.id.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Forecasts describe an area and can differ from rain on your street.\nWeather: Open-Meteo · Singapore regional forecast: NEA / MSS via data.gov.sg",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdviceCard(advice: UmbrellaAdvice, highestChance: Int?) {
    val title = when (advice) {
        UmbrellaAdvice.BRING_UMBRELLA -> "Bring an umbrella"
        UmbrellaAdvice.RAIN_LESS_LIKELY -> "Rain looks less likely"
        UmbrellaAdvice.CHECK_FORECAST -> "Keep an eye on the sky"
    }
    val detail = when (advice) {
        UmbrellaAdvice.BRING_UMBRELLA -> "Rain is indicated by current conditions, a nearby forecast or an hourly chance of at least 50%."
        UmbrellaAdvice.RAIN_LESS_LIKELY -> "The available hourly chances are below 50%. Rain is still possible."
        UmbrellaAdvice.CHECK_FORECAST -> "Some information is missing or needs refreshing. Carry a small umbrella if unsure."
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UmbrellaDrawing(MaterialTheme.colorScheme.onPrimaryContainer, Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (highestChance != null) Text("Highest upcoming hourly chance: $highestChance%", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RegionalCard(report: WeatherReport, now: Instant) {
    SectionCard {
        Text("Nearby regional forecast", style = MaterialTheme.typography.titleLarge)
        val area = report.nearbyArea
        if (area != null && now >= area.validFrom && now < area.validUntil) {
            Text(area.areaName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(area.description, style = MaterialTheme.typography.headlineSmall)
            Text("Valid ${formatTime(area.validFrom, report.weather.timeZone)}–${formatTime(area.validUntil, report.weather.timeZone)}")
            Text(String.format(Locale.ENGLISH, "Nearest forecast reference point, %.1f km away. This is an approximate area match.", area.distanceKm),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(when {
                area != null -> "This regional forecast has expired. Refresh to check for an update."
                report.regionalStatus == RegionalForecastStatus.OUTSIDE_SINGAPORE -> "Regional nowcasts cover Singapore only. The weather above uses your current coordinates."
                report.regionalStatus == RegionalForecastStatus.COUNTRY_UNKNOWN -> "Your country could not be confirmed. The weather above still uses your current coordinates."
                else -> "A regional forecast is unavailable right now. The weather above is still available."
            })
        }
    }
}

@Composable
private fun ErrorCard(problem: WeatherProblem, onRetry: () -> Unit, onAppSettings: () -> Unit, onLocationSettings: () -> Unit) {
    val (title, detail) = when (problem) {
        WeatherProblem.PERMISSION_REQUIRED -> "Location access is needed" to "Allow approximate or precise location to check the weather where you are. If the prompt no longer appears, use App settings."
        WeatherProblem.LOCATION_DISABLED -> "Location is switched off" to "Switch on your device's location service, then try again."
        WeatherProblem.LOCATION_TIMEOUT -> "Location is taking too long" to "Try again near a window or outdoors."
        WeatherProblem.LOCATION_UNAVAILABLE -> "Location is unavailable" to "A fresh location could not be obtained. Check location access and try again."
        WeatherProblem.NETWORK -> "Could not connect" to "Check your internet connection, then try again."
        WeatherProblem.SERVICE -> "Weather service is unavailable" to "The service could not complete this request. Please try again later."
        WeatherProblem.INVALID_DATA -> "Forecast could not be verified" to "The service returned incomplete or out-of-date information. Please refresh."
        WeatherProblem.UNEXPECTED -> "Weather could not be loaded" to "Please try again."
    }
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Text(detail)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        if (problem == WeatherProblem.PERMISSION_REQUIRED) OutlinedButton(onClick = onAppSettings, modifier = Modifier.fillMaxWidth()) { Text("App settings") }
        if (problem == WeatherProblem.LOCATION_DISABLED) OutlinedButton(onClick = onLocationSettings, modifier = Modifier.fillMaxWidth()) { Text("Location settings") }
    }
}

@Composable
private fun TemperatureMetric(label: String, celsius: Double?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(celsius?.let { "${it.roundToInt()}°C" } ?: "—", style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun UmbrellaDrawing(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val canopy = Path().apply {
            moveTo(size.width * .08f, size.height * .5f)
            quadraticTo(size.width * .5f, -size.height * .15f, size.width * .92f, size.height * .5f)
            close()
        }
        drawPath(canopy, color)
        drawLine(color, Offset(size.width * .5f, size.height * .45f), Offset(size.width * .5f, size.height * .8f), size.width * .05f)
        drawArc(color, 0f, 180f, false, Offset(size.width * .3f, size.height * .7f),
            androidx.compose.ui.geometry.Size(size.width * .2f, size.height * .2f), style = Stroke(size.width * .05f))
    }
}

private fun formatTime(time: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(zone).format(time)

private fun weatherDescription(code: Int?): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77, 85, 86 -> "Snow"
    80, 81, 82 -> "Rain showers"
    95, 96, 99 -> "Thunderstorms"
    else -> "Current conditions"
}
