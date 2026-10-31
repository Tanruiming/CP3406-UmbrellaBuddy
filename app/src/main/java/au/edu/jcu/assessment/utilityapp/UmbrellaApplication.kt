package au.edu.jcu.assessment.utilityapp

import android.app.Application
import android.content.Context
import au.edu.jcu.assessment.utilityapp.data.DefaultWeatherRepository
import au.edu.jcu.assessment.utilityapp.data.NetworkClients
import au.edu.jcu.assessment.utilityapp.domain.LocationProvider
import au.edu.jcu.assessment.utilityapp.domain.WeatherRepository
import au.edu.jcu.assessment.utilityapp.location.AndroidLocationProvider
import au.edu.jcu.assessment.utilityapp.notifications.AndroidRainNotifier

class UmbrellaApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

// One application-scoped container shares services without retaining an Activity.
class AppContainer(context: Context) {
    private val clients by lazy { NetworkClients() }
    val repository: WeatherRepository by lazy { DefaultWeatherRepository(clients.weatherApi, clients.areaForecastApi) }
    val locationProvider: LocationProvider by lazy { AndroidLocationProvider(context.applicationContext) }
    val rainNotifier by lazy { AndroidRainNotifier(context.applicationContext) }
}
