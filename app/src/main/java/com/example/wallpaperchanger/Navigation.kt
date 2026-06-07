package com.example.wallpaperchanger

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.wallpaperchanger.ui.main.MainScreen
import com.example.wallpaperchanger.ui.photopicker.PhotoPickerScreen
import com.example.wallpaperchanger.ui.position.CustomPositionConfigScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<Main> {
                    MainScreen(
                        onNavigateToPhotoPicker = { bucketId, albumName ->
                            backStack.add(PhotoPicker)
                        },
                        onNavigateToCustomPosition = {
                            backStack.add(CustomPositionConfig)
                        },
                        modifier = Modifier.safeDrawingPadding()
                    )
                }
                entry<PhotoPicker> {
                    val prefs = PreferencesManager(androidx.compose.ui.platform.LocalContext.current)
                    PhotoPickerScreen(
                        bucketId = prefs.albumBucketId,
                        albumName = prefs.albumName,
                        onDone = { backStack.removeLastOrNull() }
                    )
                }
                entry<CustomPositionConfig> {
                    CustomPositionConfigScreen(
                        onDone = { backStack.removeLastOrNull() }
                    )
                }
            },
    )
}

