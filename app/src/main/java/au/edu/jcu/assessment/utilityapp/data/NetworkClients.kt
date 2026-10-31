package au.edu.jcu.assessment.utilityapp.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// The application container will own one instance so both services share HTTP resources.
class NetworkClients {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gsonConverter = GsonConverterFactory.create()

    val weatherApi: WeatherApi by lazy {
        createRetrofit("https://api.open-meteo.com/")
            .create(WeatherApi::class.java)
    }

    val areaForecastApi: AreaForecastApi by lazy {
        createRetrofit("https://api-open.data.gov.sg/v2/real-time/api/")
            .create(AreaForecastApi::class.java)
    }

    private fun createRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(gsonConverter)
        // Check interface annotations when a service is first created.
        .validateEagerly(true)
        .build()
}
