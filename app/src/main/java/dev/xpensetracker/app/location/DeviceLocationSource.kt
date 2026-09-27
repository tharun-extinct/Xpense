package dev.xpensetracker.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** What one tap on "Tag location" can produce, including every way it can fail. */
sealed interface LocationResult {
    data class Fix(val latitude: Double, val longitude: Double) : LocationResult

    /** The permission was never granted, or was revoked after it was. */
    data object PermissionMissing : LocationResult

    /** Location services are off device-wide; no permission grant works around that. */
    data object LocationOff : LocationResult

    /** Permission and services are fine, but no provider produced a fix. */
    data object Unavailable : LocationResult
}

/**
 * Reads a single position from the platform [LocationManager].
 *
 * Deliberately not Play Services' fused client: that would add a closed-source Google dependency
 * to an app whose proposition is that it holds no network permission, to buy a convenience this
 * feature does not need. The platform API ships in the SDK, and `LocationManagerCompat` already
 * backports the one-shot request below to this app's minimum API.
 *
 * Nothing here is reachable from the SMS receiver or the backfill worker. A position is read only
 * when the user taps to attach one, so the app never accumulates a location history.
 */
class DeviceLocationSource(private val context: Context) {

    private val manager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    fun hasPermission(): Boolean = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun currentLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionMissing
        val manager = manager ?: return LocationResult.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.LocationOff

        // Ordered by how likely each is to answer promptly indoors, which is where most card
        // transactions happen and where a satellite fix is least likely to arrive.
        for (provider in enabledProviders(manager)) {
            val fix = requestFix(manager, provider)
            if (fix != null) return fix
        }
        return LocationResult.Unavailable
    }

    private fun enabledProviders(manager: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    // Permission is checked at the top of currentLocation, which lint cannot follow across the
    // suspend boundary into this private helper.
    @SuppressLint("MissingPermission")
    private suspend fun requestFix(manager: LocationManager, provider: String): LocationResult.Fix? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                signal,
                Executor { it.run() },
            ) { location ->
                continuation.resume(location?.let { LocationResult.Fix(it.latitude, it.longitude) })
            }
        }

    private companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
