package au.edu.jcu.assessment.utilityapp.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import au.edu.jcu.assessment.utilityapp.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission") // Check immediately before use; also handle revocation during the request.
    override suspend fun getCurrentLocation(): LocationResult {
        if (!context.hasLocationPermission()) return LocationResult.Failure(LocationFailure.PERMISSION_REQUIRED)
        return try {
            if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.Failure(LocationFailure.DISABLED)
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val providers = buildList {
                // With precise access, request a fresh GPS fix before accepting a fused estimate.
                // Some emulators keep returning their default fused location after a GPS point changes.
                if (fine) add(LocationManager.GPS_PROVIDER)
                if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
            }.filter { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }
            if (providers.isEmpty()) return LocationResult.Failure(LocationFailure.UNAVAILABLE)

            var completed = false
            val fix = withTimeoutOrNull(25_000) {
                for (provider in providers) {
                    val candidate = withTimeoutOrNull(9_000) { requestFix(provider) }
                    if (candidate != null && isUsable(candidate)) return@withTimeoutOrNull candidate
                }
                completed = true
                null
            } ?: return LocationResult.Failure(if (completed) LocationFailure.UNAVAILABLE else LocationFailure.TIMEOUT)

            val coordinates = Coordinates(fix.latitude, fix.longitude)
            val address = findArea(coordinates)
            currentCoroutineContext().ensureActive()
            LocationResult.Success(DeviceLocation(
                coordinates = coordinates,
                accuracyMetres = fix.accuracy,
                approximate = !fine || fix.accuracy >= 1000,
                capturedAt = Instant.ofEpochMilli(fix.time),
                areaName = address?.subLocality ?: address?.locality ?: address?.subAdminArea ?: address?.adminArea,
                countryCode = address?.countryCode
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            LocationResult.Failure(LocationFailure.PERMISSION_REQUIRED)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            LocationResult.Failure(LocationFailure.UNAVAILABLE)
        }
    }

    private fun isUsable(fix: Location): Boolean = isUsableLocationFix(
        fix.latitude, fix.longitude, if (fix.hasAccuracy()) fix.accuracy else Float.NaN,
        (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
    )

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { fix ->
            if (continuation.isActive) continuation.resume(fix)
        }
    }

    private suspend fun findArea(coordinates: Coordinates): Address? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(context, Locale.ENGLISH)
            withTimeoutOrNull(5_000) {
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                                }
                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            })
                    }
                } else {
                    // The pre-33 API blocks, so run it away from the main thread.
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)?.firstOrNull()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null // Area names are optional; coordinates still support a basic weather request.
        }
    }
}
