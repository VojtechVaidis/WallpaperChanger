package com.example.wallpaperchanger.ui.photopicker

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wallpaperchanger.AlbumRepository
import com.example.wallpaperchanger.PreferencesManager
import com.example.wallpaperchanger.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPickerScreen(
    bucketId: Long,
    albumName: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val albumRepo = remember { AlbumRepository(context) }

    var images by remember { mutableStateOf<List<AlbumRepository.ImageItem>>(emptyList()) }
    var selectedUris by remember { mutableStateOf(prefs.selectedPhotoUris) }

    LaunchedEffect(bucketId) {
        images = albumRepo.getImageItemsForAlbum(bucketId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "${selectedUris.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Select All / Deselect All
                    val allSelected = selectedUris.size == images.size && images.isNotEmpty()
                    IconButton(onClick = {
                        selectedUris = if (allSelected) {
                            emptySet()
                        } else {
                            images.map { it.uri.toString() }.toSet()
                        }
                    }) {
                        Icon(
                            imageVector = if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                            contentDescription = if (allSelected) "Deselect All" else "Select All",
                            tint = AccentGradientStart
                        )
                    }

                    // Done button
                    TextButton(
                        onClick = {
                            prefs.selectedPhotoUris = selectedUris
                            prefs.selectionMode = PreferencesManager.MODE_SELECTED_PHOTOS
                            onDone()
                        },
                        enabled = selectedUris.isNotEmpty()
                    ) {
                        Text(
                            "Done",
                            color = if (selectedUris.isNotEmpty()) AccentGradientStart else InactiveGrey,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkSurface
    ) { padding ->
        if (images.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGradientStart)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 4.dp,
                    end = 4.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 80.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(images, key = { it.id }) { imageItem ->
                    val isSelected = imageItem.uri.toString() in selectedUris
                    PhotoGridItem(
                        imageItem = imageItem,
                        isSelected = isSelected,
                        onToggle = {
                            val uriString = imageItem.uri.toString()
                            selectedUris = if (isSelected) {
                                selectedUris - uriString
                            } else {
                                selectedUris + uriString
                            }
                        }
                    )
                }
            }
        }

        // Bottom bar with count
        if (selectedUris.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentGradientStart)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.selectedPhotoUris = selectedUris
                                prefs.selectionMode = PreferencesManager.MODE_SELECTED_PHOTOS
                                onDone()
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Use ${selectedUris.size} Photos",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    imageItem: AlbumRepository.ImageItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(if (isSelected) 12.dp else 4.dp))
            .then(
                if (isSelected) Modifier.border(
                    2.5.dp,
                    Brush.linearGradient(listOf(AccentGradientStart, AccentGradientEnd)),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable { onToggle() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageItem.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay + check mark
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AccentGradientStart.copy(alpha = 0.2f))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(
                        Brush.linearGradient(listOf(AccentGradientStart, AccentGradientEnd)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
