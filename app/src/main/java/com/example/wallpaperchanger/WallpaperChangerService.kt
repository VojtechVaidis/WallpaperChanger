package com.example.wallpaperchanger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that listens for ACTION_SCREEN_ON broadcasts and display state changes,
 * and changes the wallpaper to a pre-cached random image from the selected album/photos.
 *
 * Preloads the next wallpaper in the background to react instantly to screen wake.
 */
class WallpaperChangerService : Service() {

    companion object {
        private const val TAG = "WallpaperChangerSvc"
        private const val CHANNEL_ID = "wallpaper_changer_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, WallpaperChangerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WallpaperChangerService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var albumRepo: AlbumRepository
    private lateinit var wallpaperHelper: WallpaperHelper

    private var screenOnReceiver: BroadcastReceiver? = null
    private var mediaObserver: ContentObserver? = null

    // Background thread scope
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Memory cache for next wallpaper
    @Volatile
    private var cachedNextBitmap: Bitmap? = null
    @Volatile
    private var cachedNextUri: Uri? = null
    @Volatile
    private var isPreloading = false

    // Display Listener for AOD support
    private var displayListener: DisplayManager.DisplayListener? = null
    private var lastDisplayState: Int = Display.STATE_UNKNOWN
    private var lastChangeTime: Long = 0

    // Preference Listener to invalidate cache when settings change
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    // Cached list of image URIs for the selected album (whole-album mode)
    private var cachedAlbumImages: List<Uri> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        prefs = PreferencesManager(this)
        albumRepo = AlbumRepository(this)
        wallpaperHelper = WallpaperHelper(this)

        createNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        registerScreenOnReceiver()
        registerDisplayListener()
        registerPrefsListener()
        refreshImageCache()
        registerMediaObserver()
        
        // Initial preload
        preloadNextWallpaper()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        
        unregisterScreenOnReceiver()
        unregisterDisplayListener()
        unregisterPrefsListener()
        unregisterMediaObserver()
        
        cachedNextBitmap?.recycle()
        cachedNextBitmap = null
        cachedNextUri = null
    }

    // --- Screen On Receiver ---

    private fun registerScreenOnReceiver() {
        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    Log.i(TAG, "Screen ON broadcast received — changing wallpaper")
                    changeWallpaper()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenOnReceiver, filter)
        Log.i(TAG, "Screen ON receiver registered")
    }

    private fun unregisterScreenOnReceiver() {
        screenOnReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen receiver", e)
            }
        }
        screenOnReceiver = null
    }

    // --- Display Listener (Always On Display support) ---

    private fun registerDisplayListener() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    val display = displayManager.getDisplay(displayId)
                    if (display != null) {
                        val currentState = display.state
                        Log.d(TAG, "Display state changed: $currentState (last: $lastDisplayState)")
                        if (currentState == Display.STATE_ON && 
                            lastDisplayState != Display.STATE_ON &&
                            lastDisplayState != Display.STATE_UNKNOWN) {
                            Log.i(TAG, "Display transition to STATE_ON detected — changing wallpaper")
                            changeWallpaper()
                        }
                        lastDisplayState = currentState
                    }
                }
            }
        }

        val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        if (defaultDisplay != null) {
            lastDisplayState = defaultDisplay.state
        }

        displayManager.registerDisplayListener(displayListener, null)
        Log.i(TAG, "DisplayManager listener registered (initial state: $lastDisplayState)")
    }

    private fun unregisterDisplayListener() {
        displayListener?.let {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            displayManager.unregisterDisplayListener(it)
        }
        displayListener = null
    }

    // --- Preference Listener ---

    private fun registerPrefsListener() {
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "album_bucket_id" || key == "selection_mode" || key == "selected_photo_uris" || key == "scaling_mode") {
                Log.d(TAG, "Preferences changed ($key) — refreshing cache and preloading")
                refreshImageCache()
                preloadNextWallpaper()
            }
        }
        prefs.registerListener(prefsListener!!)
    }

    private fun unregisterPrefsListener() {
        prefsListener?.let {
            prefs.unregisterListener(it)
        }
        prefsListener = null
    }

    // --- Media Observer (auto-detect new photos in album) ---

    private fun registerMediaObserver() {
        val handler = Handler(Looper.getMainLooper())
        mediaObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "MediaStore changed — refreshing cache and preloading")
                refreshImageCache()
                preloadNextWallpaper()
                updateNotification()
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver!!
        )
        Log.d(TAG, "MediaStore observer registered")
    }

    private fun unregisterMediaObserver() {
        mediaObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        mediaObserver = null
    }

    // --- Image Cache ---

    private fun refreshImageCache() {
        if (prefs.selectionMode == PreferencesManager.MODE_WHOLE_ALBUM && prefs.hasAlbumSelected) {
            cachedAlbumImages = albumRepo.getImagesForAlbum(prefs.albumBucketId)
            Log.d(TAG, "Refreshed album image cache: ${cachedAlbumImages.size} images")
        }
    }

    // --- Preloading Logic ---

    private fun preloadNextWallpaper() {
        if (isPreloading) return

        serviceScope.launch {
            isPreloading = true
            try {
                if (!prefs.hasAlbumSelected) {
                    isPreloading = false
                    return@launch
                }

                val imageUris: List<Uri> = when (prefs.selectionMode) {
                    PreferencesManager.MODE_WHOLE_ALBUM -> {
                        val fresh = albumRepo.getImagesForAlbum(prefs.albumBucketId)
                        cachedAlbumImages = fresh
                        fresh
                    }
                    PreferencesManager.MODE_SELECTED_PHOTOS -> {
                        prefs.selectedPhotoUris.map { Uri.parse(it) }
                    }
                    else -> emptyList()
                }

                if (imageUris.isEmpty()) {
                    isPreloading = false
                    return@launch
                }

                val randomUri = imageUris.random()
                Log.d(TAG, "Preloading next wallpaper from: $randomUri")

                val bitmap = wallpaperHelper.processWallpaperBitmap(randomUri)
                if (bitmap != null) {
                    val oldBitmap = cachedNextBitmap
                    cachedNextBitmap = bitmap
                    cachedNextUri = randomUri
                    oldBitmap?.recycle()
                    Log.d(TAG, "Successfully preloaded wallpaper bitmap")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading next wallpaper", e)
            } finally {
                isPreloading = false
            }
        }
    }

    // --- Wallpaper Changing Logic ---

    private fun changeWallpaper() {
        val now = System.currentTimeMillis()
        if (now - lastChangeTime < 2000) {
            Log.d(TAG, "Skipping wallpaper change (rate limit: ${now - lastChangeTime}ms since last change)")
            return
        }
        lastChangeTime = now

        serviceScope.launch {
            val bitmap = cachedNextBitmap
            val uri = cachedNextUri
            
            if (bitmap != null && uri != null) {
                Log.i(TAG, "Setting wallpaper from cached preloaded bitmap: $uri")
                cachedNextBitmap = null
                cachedNextUri = null

                val success = wallpaperHelper.setWallpaperFromBitmap(bitmap, prefs.wallpaperTarget)
                bitmap.recycle()

                if (success) {
                    prefs.lastChangedUri = uri.toString()
                    updateNotification()
                }
                
                // Immediately preload the next one in the background
                preloadNextWallpaper()
            } else {
                Log.i(TAG, "No cached wallpaper available, decoding on demand")
                if (!prefs.hasAlbumSelected) {
                    Log.w(TAG, "No album selected, skipping wallpaper change")
                    return@launch
                }

                val imageUris: List<Uri> = when (prefs.selectionMode) {
                    PreferencesManager.MODE_WHOLE_ALBUM -> {
                        val fresh = albumRepo.getImagesForAlbum(prefs.albumBucketId)
                        cachedAlbumImages = fresh
                        fresh
                    }
                    PreferencesManager.MODE_SELECTED_PHOTOS -> {
                        prefs.selectedPhotoUris.map { Uri.parse(it) }
                    }
                    else -> emptyList()
                }

                if (imageUris.isEmpty()) {
                    Log.w(TAG, "No images available for wallpaper change")
                    return@launch
                }

                val randomUri = imageUris.random()
                Log.i(TAG, "Setting wallpaper on-demand from: $randomUri")

                val success = wallpaperHelper.setWallpaper(randomUri, prefs.wallpaperTarget)
                if (success) {
                    prefs.lastChangedUri = randomUri.toString()
                    updateNotification()
                }
                
                // Trigger preload for the next wake event
                preloadNextWallpaper()
            }
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wallpaper Changer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps wallpaper changer running in the background"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val albumInfo = if (prefs.hasAlbumSelected) {
            val count = when (prefs.selectionMode) {
                PreferencesManager.MODE_WHOLE_ALBUM -> cachedAlbumImages.size
                PreferencesManager.MODE_SELECTED_PHOTOS -> prefs.selectedPhotoUris.size
                else -> 0
            }
            "${prefs.albumName} • $count photos"
        } else {
            "No album selected"
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Wallpaper Changer Active")
            .setContentText(albumInfo)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
