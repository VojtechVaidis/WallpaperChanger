package com.example.wallpaperchanger

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages persistent preferences for the wallpaper changer app.
 * Stores selected album info, individual photo selections, service state, and wallpaper target.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallpaper_changer_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ALBUM_BUCKET_ID = "album_bucket_id"
        private const val KEY_ALBUM_NAME = "album_name"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_WALLPAPER_TARGET = "wallpaper_target"
        private const val KEY_SELECTION_MODE = "selection_mode"
        private const val KEY_SELECTED_PHOTO_URIS = "selected_photo_uris"
        private const val KEY_LAST_CHANGED_URI = "last_changed_uri"
        private const val KEY_SCALING_MODE = "scaling_mode"

        private const val KEY_CUSTOM_RECT_LEFT = "custom_rect_left"
        private const val KEY_CUSTOM_RECT_TOP = "custom_rect_top"
        private const val KEY_CUSTOM_RECT_RIGHT = "custom_rect_right"
        private const val KEY_CUSTOM_RECT_BOTTOM = "custom_rect_bottom"


        const val TARGET_HOME = 0
        const val TARGET_LOCK = 1
        const val TARGET_BOTH = 2

        const val MODE_WHOLE_ALBUM = 0
        const val MODE_SELECTED_PHOTOS = 1

        const val SCALING_FILL = 0
        const val SCALING_FIT = 1
        const val SCALING_CUSTOM = 2

        const val BUCKET_ID_GOOGLE_PHOTOS = -2L
    }

    var albumBucketId: Long
        get() = prefs.getLong(KEY_ALBUM_BUCKET_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_ALBUM_BUCKET_ID, value).apply()

    var albumName: String
        get() = prefs.getString(KEY_ALBUM_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ALBUM_NAME, value).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var wallpaperTarget: Int
        get() = prefs.getInt(KEY_WALLPAPER_TARGET, TARGET_BOTH)
        set(value) = prefs.edit().putInt(KEY_WALLPAPER_TARGET, value).apply()

    var selectionMode: Int
        get() = prefs.getInt(KEY_SELECTION_MODE, MODE_WHOLE_ALBUM)
        set(value) = prefs.edit().putInt(KEY_SELECTION_MODE, value).apply()

    var selectedPhotoUris: Set<String>
        get() {
            val json = prefs.getString(KEY_SELECTED_PHOTO_URIS, null) ?: return emptySet()
            val type = object : TypeToken<Set<String>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit().putString(KEY_SELECTED_PHOTO_URIS, json).apply()
        }

    var lastChangedUri: String
        get() = prefs.getString(KEY_LAST_CHANGED_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_CHANGED_URI, value).apply()

    var scalingMode: Int
        get() = prefs.getInt(KEY_SCALING_MODE, SCALING_FILL)
        set(value) = prefs.edit().putInt(KEY_SCALING_MODE, value).apply()

    var customRectLeft: Float
        get() = prefs.getFloat(KEY_CUSTOM_RECT_LEFT, 0.1f)
        set(value) = prefs.edit().putFloat(KEY_CUSTOM_RECT_LEFT, value).apply()

    var customRectTop: Float
        get() = prefs.getFloat(KEY_CUSTOM_RECT_TOP, 0.1f)
        set(value) = prefs.edit().putFloat(KEY_CUSTOM_RECT_TOP, value).apply()

    var customRectRight: Float
        get() = prefs.getFloat(KEY_CUSTOM_RECT_RIGHT, 0.9f)
        set(value) = prefs.edit().putFloat(KEY_CUSTOM_RECT_RIGHT, value).apply()

    var customRectBottom: Float
        get() = prefs.getFloat(KEY_CUSTOM_RECT_BOTTOM, 0.9f)
        set(value) = prefs.edit().putFloat(KEY_CUSTOM_RECT_BOTTOM, value).apply()



    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun clearAlbumSelection() {
        prefs.edit()
            .remove(KEY_ALBUM_BUCKET_ID)
            .remove(KEY_ALBUM_NAME)
            .remove(KEY_SELECTED_PHOTO_URIS)
            .remove(KEY_SELECTION_MODE)
            .apply()
    }

    val hasAlbumSelected: Boolean
        get() = albumBucketId != -1L
}
