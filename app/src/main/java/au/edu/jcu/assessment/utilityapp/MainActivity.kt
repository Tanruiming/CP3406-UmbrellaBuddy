package au.edu.jcu.assessment.utilityapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.edu.jcu.assessment.utilityapp.location.hasLocationPermission
import au.edu.jcu.assessment.utilityapp.ui.WeatherScreen
import au.edu.jcu.assessment.utilityapp.ui.WeatherUiState
import au.edu.jcu.assessment.utilityapp.ui.WeatherViewModel
import au.edu.jcu.assessment.utilityapp.ui.theme.MobileTech_UtilityAppTheme
import kotlinx.coroutines.delay
import java.time.Instant

class MainActivity : ComponentActivity() {
    private lateinit var model: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as UmbrellaApplication).container
        model = ViewModelProvider(this, viewModelFactory {
            initializer { WeatherViewModel(container.repository, container.locationProvider) }
        })[WeatherViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (hasLocationPermission()) model.refresh() else model.permissionDenied()
            }
            val locate = {
                if (hasLocationPermission()) model.refresh()
                else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            MobileTech_UtilityAppTheme {
                UtilityApp(state, locate,
                    onAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                    onLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onCancel = model::cancelActiveLoad)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) model.onResume(hasLocationPermission())
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && ::model.isInitialized) model.cancelActiveLoad()
    }
}

@Composable
fun UtilityApp(
    state: WeatherUiState,
    onLocate: () -> Unit,
    onAppSettings: () -> Unit,
    onLocationSettings: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf("Weather") }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(state) {
        now = Instant.now()
        if (state is WeatherUiState.Ready) while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Weather") },
                selected = selectedTab == "Weather", onClick = { selectedTab = "Weather" })
            NavigationBarItem(icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text("Settings") },
                selected = selectedTab == "Settings", onClick = { selectedTab = "Settings" })
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            if (selectedTab == "Weather") WeatherScreen(state, now, onLocate, onAppSettings, onLocationSettings, onCancel)
            else SettingsScreen(onAppSettings)
        }
    }
}

@Composable
private fun SettingsScreen(onAppSettings: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Text("Location access", style = MaterialTheme.typography.titleLarge)
        Text("Approximate location is supported. Your location is checked when you request weather; the app does not track you in the background.")
        OutlinedButton(onClick = onAppSettings) { Text("Manage location access") }
        HorizontalDivider()
        Text("Weather sources", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { uriHandler.openUri("https://open-meteo.com/") }) { Text("Open-Meteo weather data") }
        TextButton(onClick = { uriHandler.openUri("https://data.gov.sg/datasets/d_3f9e064e25005b0e42969944ccaf2e7a/view") }) { Text("NEA / MSS regional forecast") }
    }
}

@Preview(showBackground = true)
@Composable
fun UtilityAppPreview() {
    MobileTech_UtilityAppTheme { UtilityApp(WeatherUiState.Idle, {}, {}, {}, {}) }
}
