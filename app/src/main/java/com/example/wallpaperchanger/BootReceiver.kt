package com.example.wallpaperchanger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the WallpaperChangerService after a device reboot,
 * if the user had the service enabled.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed — checking if service should restart")
            val prefs = PreferencesManager(context)
            if (prefs.serviceEnabled && prefs.hasAlbumSelected) {
                Log.d(TAG, "Restarting WallpaperChangerService after boot")
                WallpaperChangerService.start(context)
            }
        }
    }
}
