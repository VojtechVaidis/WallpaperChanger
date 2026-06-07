package com.example.wallpaperchanger.ui.position

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wallpaperchanger.AlbumRepository
import com.example.wallpaperchanger.PreferencesManager
import com.example.wallpaperchanger.theme.*

/**
 * Screen that allows users to interactively configure the custom drawing bounding box
 * representing the usable part of the vertical screen where photos should appear.
 *
 * Why: The user wants to specify a single region of the screen (e.g. to avoid the status/notifications
 * area at the top of the lock screen) where photos are drawn without being scaled up if they are low-resolution.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPositionConfigScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val albumRepo = remember { AlbumRepository(context) }

    // Load preview image URI from last wallpaper or album cover
    val previewUri = remember {
        if (prefs.lastChangedUri.isNotEmpty()) {
            Uri.parse(prefs.lastChangedUri)
        } else if (prefs.hasAlbumSelected) {
            val images = albumRepo.getImagesForAlbum(prefs.albumBucketId)
            images.firstOrNull()
        } else {
            null
        }
    }

    // Temporary layout coordinates during adjustment (loaded from unified keys)
    var left by remember { mutableFloatStateOf(prefs.customRectLeft) }
    var top by remember { mutableFloatStateOf(prefs.customRectTop) }
    var right by remember { mutableFloatStateOf(prefs.customRectRight) }
    var bottom by remember { mutableFloatStateOf(prefs.customRectBottom) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Wallpaper Position",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkSurface,
        bottomBar = {
            Surface(
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDone) {
                        Text("Cancel", color = SubtleText, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            // Save bounds
                            prefs.customRectLeft = left
                            prefs.customRectTop = top
                            prefs.customRectRight = right
                            prefs.customRectBottom = bottom

                            // Set scaling mode to custom
                            prefs.scalingMode = PreferencesManager.SCALING_CUSTOM

                            onDone()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGradientStart
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Configure Wallpaper Area",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select the usable screen rectangle. Photos will appear inside this region. The margins outside remain solid white.",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(0.1f))

            // Mockup frame area container - Vertical screen mockup (9:16 aspect ratio)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.95f)
                        .aspectRatio(9f / 16f)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .border(4.dp, InactiveGrey, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    InteractiveCropBox(
                        previewUri = previewUri,
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        onCoordinatesChanged = { l, t, r, b ->
                            left = l
                            top = t
                            right = r
                            bottom = b
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveCropBox(
    previewUri: Uri?,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onCoordinatesChanged: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Cache the image request to prevent building it on every drag update frame
    val imageRequest = remember(previewUri) {
        if (previewUri != null) {
            ImageRequest.Builder(context)
                .data(previewUri)
                .crossfade(true)
                .build()
        } else {
            null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Minimum crop box size
        val minSize = 0.15f

        // Capture latest states dynamically to avoid restarting the pointerInput block
        val currentLeft by rememberUpdatedState(left)
        val currentTop by rememberUpdatedState(top)
        val currentRight by rememberUpdatedState(right)
        val currentBottom by rememberUpdatedState(bottom)
        val currentOnCoordinatesChanged by rememberUpdatedState(onCoordinatesChanged)

        // Draw the image positioned inside the custom rectangle
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val w = (constraints.maxWidth * (currentRight - currentLeft)).toInt()
                    val h = (constraints.maxHeight * (currentBottom - currentTop)).toInt()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    val x = (constraints.maxWidth * currentLeft).toInt()
                    val y = (constraints.maxHeight * currentTop).toInt()
                    layout(w, h) {
                        placeable.placeRelative(x, y)
                    }
                }
                .border(1.5.dp, AccentGradientStart, RoundedCornerShape(4.dp))
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val w = currentRight - currentLeft
                        val h = currentBottom - currentTop

                        val newLeft = (currentLeft + dragAmount.x / widthPx).coerceIn(0f, 1f - w)
                        val newTop = (currentTop + dragAmount.y / heightPx).coerceIn(0f, 1f - h)

                        currentOnCoordinatesChanged(newLeft, newTop, newLeft + w, newTop + h)
                    }
                }
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Fit, // Fits the original photo inside the drag box
                    alignment = Alignment.TopCenter, // Aligns photo to top limit, matching the actual layout logic
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.LightGray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No Image",
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Corner Resize Handles (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
        val handleSize = 36.dp
        val halfHandleSizePx = remember(density) { with(density) { 18.dp.roundToPx() } }

        // Top-Left Handle
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (left * widthPx).toInt() - halfHandleSizePx,
                        y = (top * heightPx).toInt() - halfHandleSizePx
                    )
                }
                .size(handleSize)
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newLeft = (currentLeft + dragAmount.x / widthPx).coerceIn(0f, currentRight - minSize)
                        val newTop = (currentTop + dragAmount.y / heightPx).coerceIn(0f, currentBottom - minSize)
                        currentOnCoordinatesChanged(newLeft, newTop, currentRight, currentBottom)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            HandleDot()
        }

        // Top-Right Handle
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (right * widthPx).toInt() - halfHandleSizePx,
                        y = (top * heightPx).toInt() - halfHandleSizePx
                    )
                }
                .size(handleSize)
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newRight = (currentRight + dragAmount.x / widthPx).coerceIn(currentLeft + minSize, 1f)
                        val newTop = (currentTop + dragAmount.y / heightPx).coerceIn(0f, currentBottom - minSize)
                        currentOnCoordinatesChanged(currentLeft, newTop, newRight, currentBottom)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            HandleDot()
        }

        // Bottom-Left Handle
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (left * widthPx).toInt() - halfHandleSizePx,
                        y = (bottom * heightPx).toInt() - halfHandleSizePx
                    )
                }
                .size(handleSize)
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newLeft = (currentLeft + dragAmount.x / widthPx).coerceIn(0f, currentRight - minSize)
                        val newBottom = (currentBottom + dragAmount.y / heightPx).coerceIn(currentTop + minSize, 1f)
                        currentOnCoordinatesChanged(newLeft, currentTop, currentRight, newBottom)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            HandleDot()
        }

        // Bottom-Right Handle
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (right * widthPx).toInt() - halfHandleSizePx,
                        y = (bottom * heightPx).toInt() - halfHandleSizePx
                    )
                }
                .size(handleSize)
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newRight = (currentRight + dragAmount.x / widthPx).coerceIn(currentLeft + minSize, 1f)
                        val newBottom = (currentBottom + dragAmount.y / heightPx).coerceIn(currentTop + minSize, 1f)
                        currentOnCoordinatesChanged(currentLeft, currentTop, newRight, newBottom)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            HandleDot()
        }
    }
}

@Composable
private fun HandleDot() {
    Box(
        modifier = Modifier
            .size(16.dp)
            .shadow(4.dp, CircleShape)
            .background(Color.White, CircleShape)
            .border(2.dp, AccentGradientStart, CircleShape)
    )
}
