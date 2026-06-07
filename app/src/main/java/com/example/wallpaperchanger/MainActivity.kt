package com.example.wallpaperchanger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.wallpaperchanger.theme.AccentGradientStart
import com.example.wallpaperchanger.theme.DarkSurfaceVariant
import com.example.wallpaperchanger.theme.SubtleText
import com.example.wallpaperchanger.theme.WallpaperChangerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ImportState {
    data object Idle : ImportState
    data class Loading(val progress: Int, val total: Int) : ImportState
    data class Success(val count: Int) : ImportState
    data class Error(val message: String) : ImportState
}

class MainActivity : ComponentActivity() {

    private var importState by mutableStateOf<ImportState>(ImportState.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleShareIntent(intent)

        enableEdgeToEdge()
        setContent {
            WallpaperChangerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainNavigation()

                        // Overlay dialog/indicator when importing shared photos
                        when (val state = importState) {
                            is ImportState.Loading -> {
                                AlertDialog(
                                    onDismissRequest = {},
                                    confirmButton = {},
                                    dismissButton = {},
                                    title = { Text("Importing Photos...", color = Color.White) },
                                    text = {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val progressFloat = if (state.total > 0) state.progress.toFloat() / state.total.toFloat() else 0f
                                            CircularProgressIndicator(
                                                progress = { progressFloat },
                                                color = AccentGradientStart
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "${state.progress} / ${state.total}",
                                                color = SubtleText,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    },
                                    containerColor = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(24.dp)
                                )
                            }
                            is ImportState.Success -> {
                                AlertDialog(
                                    onDismissRequest = { importState = ImportState.Idle },
                                    confirmButton = {
                                        TextButton(onClick = { importState = ImportState.Idle }) {
                                            Text("OK", color = AccentGradientStart)
                                        }
                                    },
                                    title = { Text("Import Successful", color = Color.White) },
                                    text = {
                                        Text(
                                            "Successfully imported ${state.count} photos from Google Photos.",
                                            color = SubtleText
                                        )
                                    },
                                    containerColor = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(24.dp)
                                )
                            }
                            is ImportState.Error -> {
                                AlertDialog(
                                    onDismissRequest = { importState = ImportState.Idle },
                                    confirmButton = {
                                        TextButton(onClick = { importState = ImportState.Idle }) {
                                            Text("OK", color = AccentGradientStart)
                                        }
                                    },
                                    title = { Text("Import Failed", color = Color.White) },
                                    text = {
                                        Text(
                                            state.message,
                                            color = SubtleText
                                        )
                                    },
                                    containerColor = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(24.dp)
                                )
                            }
                            ImportState.Idle -> {}
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type
        if ((Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action) && type?.startsWith("image/") == true) {
            val uris = when (action) {
                Intent.ACTION_SEND -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) listOf(uri) else emptyList()
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    list ?: emptyList()
                }
                else -> emptyList()
            }

            if (uris.isNotEmpty()) {
                importPhotos(uris)
            }
        }
    }

    private fun importPhotos(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            importState = ImportState.Loading(0, uris.size)
            val prefs = PreferencesManager(this@MainActivity)
            val importDir = File(filesDir, "imported_photos")

            try {
                if (!importDir.exists()) {
                    importDir.mkdirs()
                }
                // Clear previously imported photos to replace the selection
                importDir.listFiles()?.forEach { it.delete() }

                var successCount = 0
                val contentResolver = contentResolver

                uris.forEachIndexed { index, uri ->
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            val originalName = getFileName(this@MainActivity, uri)
                            val destFile = File(importDir, originalName)
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                            successCount++
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to copy photo: $uri", e)
                    }
                    withContext(Dispatchers.Main) {
                        importState = ImportState.Loading(index + 1, uris.size)
                    }
                }

                val localUris = importDir.listFiles()?.map { Uri.fromFile(it).toString() }?.toSet() ?: emptySet()

                if (localUris.isNotEmpty()) {
                    prefs.albumBucketId = PreferencesManager.BUCKET_ID_GOOGLE_PHOTOS
                    prefs.albumName = "Google Photos (Imported)"
                    prefs.selectionMode = PreferencesManager.MODE_SELECTED_PHOTOS
                    prefs.selectedPhotoUris = localUris

                    // Restart/refresh the background service to pick up new images
                    if (prefs.serviceEnabled) {
                        WallpaperChangerService.stop(this@MainActivity)
                        WallpaperChangerService.start(this@MainActivity)
                    }

                    withContext(Dispatchers.Main) {
                        importState = ImportState.Success(successCount)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        importState = ImportState.Error("No photos could be imported.")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Import failed", e)
                withContext(Dispatchers.Main) {
                    importState = ImportState.Error(e.localizedMessage ?: "Unknown error occurred.")
                }
            }
        }
    }

    private fun getFileName(context: android.content.Context, uri: Uri): String {
        var name = ""
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: ""
        }
        if (name.isEmpty()) {
            name = "photo_${System.currentTimeMillis()}"
        }
        // Normalize filename to prevent security/directory traversal issues
        name = name.replace(File.separator, "_")
        return name
    }
}
