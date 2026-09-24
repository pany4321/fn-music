package com.fnmusic.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.fnmusic.tv.core.data.repository.SessionState

/**
 * Reports usable networks so an in-progress startup recovery can retry immediately
 * instead of waiting out the remaining backoff delay — car units switch between
 * home Wi-Fi, hotspots, and relays mid-restore.
 */
fun interface NetworkRestoreMonitor {
    fun register(onNetworkAvailable: () -> Unit)
}

internal class AndroidNetworkRestoreMonitor(context: Context) : NetworkRestoreMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun register(onNetworkAvailable: () -> Unit) {
        val manager = connectivityManager ?: return
        // No VALIDATED capability: a captive portal should still trigger one probe
        // attempt; its failure simply re-enters the normal backoff loop.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onNetworkAvailable()
                }
            },
        )
    }
}

/**
 * Only a recovering session may restart: registration also fires for networks that
 * were already active, and signed-in playback or the login form must never be reset.
 */
internal fun shouldRetrySessionOnNetworkAvailable(state: SessionState): Boolean =
    state is SessionState.Recovering
