package au.edu.jcu.assessment.utilityapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.edu.jcu.assessment.utilityapp.location.hasLocationPermission
import au.edu.jcu.assessment.utilityapp.ui.WeatherScreen
import au.edu.jcu.assessment.utilityapp.ui.WeatherUiState
import au.edu.jcu.assessment.utilityapp.ui.WeatherViewModel
import au.edu.jcu.assessment.utilityapp.ui.SettingsScreen
import au.edu.jcu.assessment.utilityapp.domain.WeatherSettings
import au.edu.jcu.assessment.utilityapp.ui.theme.MobileTech_UtilityAppTheme
import kotlinx.coroutines.delay
import java.time.Instant

class MainActivity : ComponentActivity() {
    private lateinit var model: WeatherViewModel
    private var notificationPermissionDenied by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as UmbrellaApplication).container
        model = ViewModelProvider(this, viewModelFactory {
            initializer { WeatherViewModel(container.repository, container.locationProvider, rainNotifier = container.rainNotifier) }
        })[WeatherViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val preferences by model.settings.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                if (hasLocationPermission()) model.refresh() else model.permissionDenied()
            }
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                notificationPermissionDenied = !granted || !container.rainNotifier.canNotify()
                model.setNotificationsEnabled(granted)
            }
            val locate = {
                if (hasLocationPermission()) model.refresh()
                else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            MobileTech_UtilityAppTheme {
                UtilityApp(state, locate,
                    onAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                    onLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onCancel = model::cancelActiveLoad,
                    settings = preferences,
                    onSettingsChange = model::updateSettings,
                    onNotificationsChange = { enabled ->
                        if (!enabled) model.setNotificationsEnabled(false)
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationPermissionDenied = !container.rainNotifier.canNotify()
                            model.setNotificationsEnabled(true)
                        }
                    },
                    notificationsBlocked = notificationPermissionDenied,
                    onNotificationSettings = {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) {
            model.onResume(hasLocationPermission())
            if ((application as UmbrellaApplication).container.rainNotifier.canNotify()) notificationPermissionDenied = false
        }
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
    onCancel: () -> Unit,
    settings: WeatherSettings = WeatherSettings(),
    onSettingsChange: (WeatherSettings) -> Unit = {},
    onNotificationsChange: (Boolean) -> Unit = {},
    notificationsBlocked: Boolean = false,
    onNotificationSettings: () -> Unit = {}
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
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            NavigationBarItem(icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Weather") },
                selected = selectedTab == "Weather", onClick = { selectedTab = "Weather" })
            NavigationBarItem(icon = { Icon(Icons.Default.Settings, contentDescription = null) }, label = { Text("Settings") },
                selected = selectedTab == "Settings", onClick = { selectedTab = "Settings" })
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            if (selectedTab == "Weather") WeatherScreen(state, now, onLocate, onAppSettings, onLocationSettings, onCancel, settings = settings)
            else SettingsScreen(settings, (state as? WeatherUiState.Ready)?.location?.areaName,
                onSettingsChange, onNotificationsChange, notificationsBlocked, onNotificationSettings)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UtilityAppPreview() {
    MobileTech_UtilityAppTheme { UtilityApp(WeatherUiState.Idle, {}, {}, {}, {}) }
}
