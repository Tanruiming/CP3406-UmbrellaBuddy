package au.edu.jcu.assessment.utilityapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.edu.jcu.assessment.utilityapp.domain.*
import kotlin.math.roundToInt

private enum class UnitPicker { TEMPERATURE, WIND, PRECIPITATION, PRESSURE }
private data class UnitOption(val label: String, val selected: Boolean, val choose: () -> Unit)

@Composable
fun SettingsScreen(
    settings: WeatherSettings,
    currentArea: String?,
    onSettingsChange: (WeatherSettings) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    notificationsBlocked: Boolean = false,
    onNotificationSettings: () -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SettingsContent(settings, currentArea, onSettingsChange, onNotificationsChange, notificationsBlocked, onNotificationSettings)
    }
}

@Composable
private fun SettingsContent(
    settings: WeatherSettings,
    currentArea: String?,
    onSettingsChange: (WeatherSettings) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    notificationsBlocked: Boolean,
    onNotificationSettings: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var picker by rememberSaveable { mutableStateOf<UnitPicker?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("MAKE IT YOURS", style = MaterialTheme.typography.labelMedium,
            letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Your weather, your way.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        SectionLabel("NOTIFICATIONS")
        GroupCard {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Umbrella notifications", fontWeight = FontWeight.Medium)
                    Text("A rain reminder after you refresh.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.rainNotificationsEnabled, onCheckedChange = onNotificationsChange,
                    modifier = Modifier.semantics { contentDescription = "Umbrella notifications" })
            }
        }
        if (notificationsBlocked) {
            Text("Notifications are not allowed by your device. You can enable them in notification settings.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onNotificationSettings) { Text("Notification settings") }
        } else Text("Off by default. When enabled, reminders are sent only after a weather refresh; there are no scheduled background checks.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionLabel("WEATHER UNITS")
        GroupCard {
            UnitRow("Temperature", settings.temperatureUnit.symbol, "°", Color(0xFFFFA052)) { picker = UnitPicker.TEMPERATURE }
            RowDivider()
            UnitRow("Wind speed", settings.windSpeedUnit.symbol, "↝", Color(0xFF42A6AA)) { picker = UnitPicker.WIND }
            RowDivider()
            UnitRow("Precipitation", settings.precipitationUnit.symbol, "☂", Color(0xFF5599E8)) { picker = UnitPicker.PRECIPITATION }
            RowDivider()
            UnitRow("Pressure", settings.pressureUnit.symbol, "◉", Color(0xFF8D81D8)) { picker = UnitPicker.PRESSURE }
        }
        Text("Units change immediately across your forecast.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionLabel("RAIN REMINDER")
        GroupCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rain chance threshold", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("${settings.rainThresholdPercent}%", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = settings.rainThresholdPercent.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(rainThresholdPercent = ((it / 10).roundToInt() * 10).coerceIn(10, 90))) },
                    valueRange = 10f..90f, steps = 7,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Rain chance threshold" })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("10% · More cautious", style = MaterialTheme.typography.labelSmall)
                    Text("90% · Higher tolerance", style = MaterialTheme.typography.labelSmall)
                }
                Text("Used for the on-screen advice and rain notifications. Current rain or nearby showers can still recommend an umbrella.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionLabel("LOCATION")
        GroupCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(currentArea?.takeIf { it.isNotBlank() } ?: "Current location", fontWeight = FontWeight.Medium)
                Text("Weather uses your current location. Tap Refresh on the Weather tab to update it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Preferences stay while the app is running and reset after a full restart.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { uriHandler.openUri("https://open-meteo.com/") }) { Text("Weather data by Open-Meteo ↗") }
        TextButton(onClick = { uriHandler.openUri("https://data.gov.sg/datasets/d_3f9e064e25005b0e42969944ccaf2e7a/view") }) {
            Text("Singapore forecast by NEA / MSS ↗")
        }
    }

    val activePicker = picker
    if (activePicker != null) {
        val (title, options) = when (activePicker) {
            UnitPicker.TEMPERATURE -> "Temperature" to TemperatureUnit.entries.map { unit ->
                UnitOption(if (unit == TemperatureUnit.CELSIUS) "Celsius · °C" else "Fahrenheit · °F",
                    unit == settings.temperatureUnit) { onSettingsChange(settings.copy(temperatureUnit = unit)) }
            }
            UnitPicker.WIND -> "Wind speed" to WindSpeedUnit.entries.map { unit ->
                UnitOption(when (unit) {
                    WindSpeedUnit.KILOMETRES_PER_HOUR -> "Kilometres per hour · km/h"
                    WindSpeedUnit.METRES_PER_SECOND -> "Metres per second · m/s"
                    WindSpeedUnit.MILES_PER_HOUR -> "Miles per hour · mph"
                    WindSpeedUnit.KNOTS -> "Knots · kn"
                }, unit == settings.windSpeedUnit) { onSettingsChange(settings.copy(windSpeedUnit = unit)) }
            }
            UnitPicker.PRECIPITATION -> "Precipitation" to PrecipitationUnit.entries.map { unit ->
                UnitOption(if (unit == PrecipitationUnit.MILLIMETRES) "Millimetres · mm" else "Inches · in",
                    unit == settings.precipitationUnit) { onSettingsChange(settings.copy(precipitationUnit = unit)) }
            }
            UnitPicker.PRESSURE -> "Pressure" to PressureUnit.entries.map { unit ->
                UnitOption(when (unit) {
                    PressureUnit.HECTOPASCALS -> "Hectopascals · hPa"
                    PressureUnit.MILLIBARS -> "Millibars · mbar"
                    PressureUnit.INCHES_OF_MERCURY -> "Inches of mercury · inHg"
                    PressureUnit.MILLIMETRES_OF_MERCURY -> "Millimetres of mercury · mmHg"
                }, unit == settings.pressureUnit) { onSettingsChange(settings.copy(pressureUnit = unit)) }
            }
        }
        AlertDialog(onDismissRequest = { picker = null }, title = { Text(title) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                options.forEach { option ->
                    Row(Modifier.fillMaxWidth().selectable(option.selected, role = Role.RadioButton,
                        onClick = { option.choose(); picker = null }).padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = option.selected, onClick = null)
                        Text(option.label, modifier = Modifier.weight(1f))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = null }) { Text("Done") } })
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun UnitRow(title: String, value: String, glyph: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(34.dp).background(color.copy(alpha = .15f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Text(glyph, color = color, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        }
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(value, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RowDivider() { HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant) }
