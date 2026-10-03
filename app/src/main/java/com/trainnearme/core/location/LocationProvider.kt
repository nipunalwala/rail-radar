package com.trainnearme.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class LatLng(val lat: Double, val lng: Double)

interface LocationProvider {
    /** A one-shot position, or null when permission is missing or no fix arrives. */
    suspend fun current(): LatLng?
}

class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    override suspend fun current(): LatLng? {
        if (!hasPermission()) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }?.let { LatLng(it.latitude, it.longitude) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private companion object {
        const val TIMEOUT_MILLIS = 8_000L
    }
}
