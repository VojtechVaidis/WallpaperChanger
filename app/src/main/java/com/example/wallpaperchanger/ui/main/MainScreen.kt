package com.example.wallpaperchanger.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wallpaperchanger.*
import com.example.wallpaperchanger.theme.*

@Composable
fun MainScreen(
    onNavigateToPhotoPicker: (Long, String) -> Unit,
    onNavigateToCustomPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val albumRepo = remember { AlbumRepository(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isBatteryOptimized by remember {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        mutableStateOf(!pm.isIgnoringBatteryOptimizations(context.packageName))
    }



    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var serviceEnabled by remember { mutableStateOf(prefs.serviceEnabled) }
    var selectedAlbumName by remember { mutableStateOf(prefs.albumName) }
    var selectedBucketId by remember { mutableStateOf(prefs.albumBucketId) }
    var selectionMode by remember { mutableIntStateOf(prefs.selectionMode) }
    var wallpaperTarget by remember { mutableIntStateOf(prefs.wallpaperTarget) }
    var scalingMode by remember { mutableIntStateOf(prefs.scalingMode) }
    var selectedPhotoCount by remember { mutableIntStateOf(prefs.selectedPhotoUris.size) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showScalingPicker by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
                // Sync Compose UI state with latest preferences on resume (e.g. from share sheet)
                selectedAlbumName = prefs.albumName
                selectedBucketId = prefs.albumBucketId
                selectionMode = prefs.selectionMode
                selectedPhotoCount = prefs.selectedPhotoUris.size
                serviceEnabled = prefs.serviceEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val imagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }
        hasPermission = imagePermission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        }
        if (imagePermission) {
            albums = albumRepo.getAlbums()
        }
    }

    val cloudPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    android.provider.MediaStore.getPickImagesMaxLimit()
                } catch (e: Exception) {
                    1000
                }
            } else {
                1000
            }
        )
    ) { uris ->
        if (uris.isNotEmpty()) {
            val persistedUris = mutableListOf<String>()
            val contentResolver = context.contentResolver
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    persistedUris.add(uri.toString())
                } catch (e: SecurityException) {
                    android.util.Log.e("MainScreen", "Failed to persist URI permission for $uri", e)
                    persistedUris.add(uri.toString())
                }
            }

            if (persistedUris.isNotEmpty()) {
                prefs.albumBucketId = PreferencesManager.BUCKET_ID_GOOGLE_PHOTOS
                prefs.albumName = "Google Photos"
                prefs.selectionMode = PreferencesManager.MODE_SELECTED_PHOTOS
                prefs.selectedPhotoUris = persistedUris.toSet()

                selectedAlbumName = "Google Photos"
                selectedBucketId = PreferencesManager.BUCKET_ID_GOOGLE_PHOTOS
                selectionMode = PreferencesManager.MODE_SELECTED_PHOTOS
                selectedPhotoCount = persistedUris.size

                // If service is enabled, restart/refresh it
                if (serviceEnabled) {
                    WallpaperChangerService.stop(context)
                    WallpaperChangerService.start(context)
                }
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            albums = albumRepo.getAlbums()
        }
    }

    LaunchedEffect(Unit) {
        if (prefs.serviceEnabled && prefs.hasAlbumSelected) {
            WallpaperChangerService.start(context)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            HeaderSection(serviceEnabled = serviceEnabled)

            Spacer(modifier = Modifier.height(24.dp))

            // Service Toggle
            ServiceToggleCard(
                enabled = serviceEnabled,
                hasAlbum = prefs.hasAlbumSelected,
                onToggle = { enabled ->
                    if (enabled && prefs.hasAlbumSelected) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        } else {
                            prefs.serviceEnabled = true
                            serviceEnabled = true
                            WallpaperChangerService.start(context)
                        }
                    } else {
                        prefs.serviceEnabled = false
                        serviceEnabled = false
                        WallpaperChangerService.stop(context)
                    }
                }
            )

            if (isBatteryOptimized) {
                Spacer(modifier = Modifier.height(16.dp))
                BatteryOptimizationCard(
                    onRequestExemption = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                android.util.Log.e("MainScreen", "Failed to launch battery settings", ex)
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Album Selection Card
            AlbumSelectionCard(
                albumName = selectedAlbumName,
                selectionMode = selectionMode,
                selectedPhotoCount = selectedPhotoCount,
                onPickAlbum = { showAlbumPicker = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Wallpaper Target Card
            WallpaperTargetCard(
                target = wallpaperTarget,
                onPickTarget = { showTargetPicker = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Wallpaper Scaling Card
            WallpaperScalingCard(
                scalingMode = scalingMode,
                onPickScaling = { showScalingPicker = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status info
            if (prefs.lastChangedUri.isNotEmpty()) {
                LastChangedCard(uri = prefs.lastChangedUri)
            }
        }

        // Album Picker Bottom Sheet
        if (showAlbumPicker) {
            AlbumPickerSheet(
                albums = albums,
                currentBucketId = selectedBucketId,
                hasStoragePermission = hasPermission,
                onRequestStoragePermission = {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionLauncher.launch(permissions)
                },
                onAlbumSelected = { album ->
                    showAlbumPicker = false
                    selectedAlbumName = album.name
                    selectedBucketId = album.bucketId
                    prefs.albumBucketId = album.bucketId
                    prefs.albumName = album.name
                    // Default to whole album mode
                    prefs.selectionMode = PreferencesManager.MODE_WHOLE_ALBUM
                    selectionMode = PreferencesManager.MODE_WHOLE_ALBUM
                    prefs.selectedPhotoUris = emptySet()
                    selectedPhotoCount = 0
                },
                onSelectPhotos = { album ->
                    showAlbumPicker = false
                    selectedAlbumName = album.name
                    selectedBucketId = album.bucketId
                    prefs.albumBucketId = album.bucketId
                    prefs.albumName = album.name
                    onNavigateToPhotoPicker(album.bucketId, album.name)
                },
                onLaunchCloudPicker = {
                    showAlbumPicker = false
                    cloudPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onDismiss = { showAlbumPicker = false }
            )
        }

        // Wallpaper Target Picker
        if (showTargetPicker) {
            WallpaperTargetSheet(
                currentTarget = wallpaperTarget,
                onTargetSelected = { target ->
                    wallpaperTarget = target
                    prefs.wallpaperTarget = target
                    showTargetPicker = false
                },
                onDismiss = { showTargetPicker = false }
            )
        }

        // Wallpaper Scaling Picker
        if (showScalingPicker) {
            WallpaperScalingSheet(
                currentMode = scalingMode,
                onModeSelected = { mode ->
                    scalingMode = mode
                    prefs.scalingMode = mode
                    showScalingPicker = false
                    if (mode == PreferencesManager.SCALING_CUSTOM) {
                        onNavigateToCustomPosition()
                    }
                },
                onDismiss = { showScalingPicker = false }
            )
        }
    }
}

// --- Header ---

@Composable
private fun HeaderSection(serviceEnabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Wallpaper",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            Text(
                text = "Changer",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White
            )
        }

        // Status indicator
        val pulseAnim = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by pulseAnim.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    if (serviceEnabled) ActiveGreen.copy(alpha = pulseAlpha)
                    else InactiveGrey
                )
        )
    }
}

// --- Permission Card ---

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = AccentGradientStart,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Photo Access Required",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We need access to your photos to set them as wallpapers. Your photos never leave your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleText,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGradientStart
                )
            ) {
                Text("Grant Permission", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// --- Service Toggle ---

@Composable
private fun ServiceToggleCard(
    enabled: Boolean,
    hasAlbum: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFF1A2E1A) else DarkCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (enabled) ActiveGreen.copy(alpha = 0.15f)
                            else InactiveGrey.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (enabled) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        tint = if (enabled) ActiveGreen else InactiveGrey,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (enabled) "Active" else "Inactive",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (!hasAlbum) "Select an album first"
                        else if (enabled) "Changing wallpaper on screen wake"
                        else "Tap to start",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleText
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = hasAlbum,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ActiveGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = InactiveGrey
                )
            )
        }
    }
}

// --- Album Selection Card ---

@Composable
private fun AlbumSelectionCard(
    albumName: String,
    selectionMode: Int,
    selectedPhotoCount: Int,
    onPickAlbum: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPickAlbum() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentGradientStart.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = AccentGradientStart,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (albumName.isNotEmpty()) albumName else "Select Album",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            albumName.isEmpty() -> "Tap to choose an album"
                            selectionMode == PreferencesManager.MODE_WHOLE_ALBUM -> "Whole album"
                            else -> "$selectedPhotoCount photos selected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleText
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = SubtleText,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// --- Wallpaper Target Card ---

@Composable
private fun WallpaperTargetCard(
    target: Int,
    onPickTarget: () -> Unit
) {
    val targetLabel = when (target) {
        PreferencesManager.TARGET_HOME -> "Home screen"
        PreferencesManager.TARGET_LOCK -> "Lock screen"
        PreferencesManager.TARGET_BOTH -> "Both screens"
        else -> "Both screens"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPickTarget() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Pink40.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Wallpaper,
                        contentDescription = null,
                        tint = Pink40,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Wallpaper Target",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = targetLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleText
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = SubtleText,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// --- Wallpaper Scaling Card ---

@Composable
private fun WallpaperScalingCard(
    scalingMode: Int,
    onPickScaling: () -> Unit
) {
    val scalingLabel = when (scalingMode) {
        PreferencesManager.SCALING_FILL -> "Fill Screen (Crop)"
        PreferencesManager.SCALING_FIT -> "Fit Screen (Blurred background)"
        PreferencesManager.SCALING_CUSTOM -> "Custom Position (White background)"
        else -> "Fill Screen (Crop)"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPickScaling() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AspectRatio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Scaling Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = scalingLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleText
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = SubtleText,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// --- Last Changed Card ---

@Composable
private fun LastChangedCard(uri: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(Uri.parse(uri))
                    .crossfade(true)
                    .build(),
                contentDescription = "Last wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Last Wallpaper",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "Changed on last screen wake",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleText
                )
            }
        }
    }
}

// --- Album Picker Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumPickerSheet(
    albums: List<Album>,
    currentBucketId: Long,
    hasStoragePermission: Boolean,
    onRequestStoragePermission: () -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onSelectPhotos: (Album) -> Unit,
    onLaunchCloudPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceVariant,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Choose Album",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Use whole album or pick individual photos",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleText,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Google Photos / Cloud Picker Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { onLaunchCloudPicker() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                border = if (currentBucketId == PreferencesManager.BUCKET_ID_GOOGLE_PHOTOS) {
                    androidx.compose.foundation.BorderStroke(2.dp, AccentGradientStart)
                } else null
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentGradientStart.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = AccentGradientStart,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Photos / Cloud Picker",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Select photos from your cloud albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleText
                        )
                    }
                    if (currentBucketId == PreferencesManager.BUCKET_ID_GOOGLE_PHOTOS) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(AccentGradientStart, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = SubtleText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (!hasStoragePermission) {
                // Local Albums Locked Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = Pink40,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Local Albums Locked",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To use photos from your local device gallery, grant storage permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestStoragePermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGradientStart
                            )
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(albums) { album ->
                        AlbumGridItem(
                            album = album,
                            isSelected = album.bucketId == currentBucketId,
                            onUseWholeAlbum = { onAlbumSelected(album) },
                            onSelectPhotos = { onSelectPhotos(album) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGridItem(
    album: Album,
    isSelected: Boolean,
    onUseWholeAlbum: () -> Unit,
    onSelectPhotos: () -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    AccentGradientStart,
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .clickable { showOptions = !showOptions },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(album.coverUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )

                // Image count badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            OverlayDark,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${album.imageCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(AccentGradientStart, CircleShape),
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

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                AnimatedVisibility(visible = showOptions) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(
                            onClick = onUseWholeAlbum,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = AccentGradientStart
                            )
                        ) {
                            Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Whole Album", fontSize = 13.sp)
                        }
                        TextButton(
                            onClick = onSelectPhotos,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Pink40
                            )
                        ) {
                            Icon(
                                Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Photos", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- Wallpaper Target Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperTargetSheet(
    currentTarget: Int,
    onTargetSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceVariant,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Wallpaper Target",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val targets = listOf(
                Triple(PreferencesManager.TARGET_HOME, "Home Screen", Icons.Outlined.Home),
                Triple(PreferencesManager.TARGET_LOCK, "Lock Screen", Icons.Outlined.Lock),
                Triple(PreferencesManager.TARGET_BOTH, "Both Screens", Icons.Outlined.Wallpaper),
            )

            targets.forEach { (target, label, icon) ->
                val selected = currentTarget == target
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(
                            if (selected) Modifier.border(
                                1.5.dp,
                                AccentGradientStart,
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .clickable { onTargetSelected(target) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) AccentGradientStart.copy(alpha = 0.1f) else DarkCard
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) AccentGradientStart else SubtleText,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) Color.White else SubtleText
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = AccentGradientStart,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Wallpaper Scaling Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperScalingSheet(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceVariant,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Scaling Mode",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val modes = listOf(
                Triple(PreferencesManager.SCALING_FILL, "Fill Screen (Crop)", "Crops image to fill entire screen"),
                Triple(PreferencesManager.SCALING_FIT, "Fit Screen (Blurred background)", "Fits full image with blurred background bars"),
                Triple(PreferencesManager.SCALING_CUSTOM, "Custom Position (White background)", "Scale/position inside custom frames with solid white margins"),
            )

            modes.forEach { (mode, label, desc) ->
                val selected = currentMode == mode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(
                            if (selected) Modifier.border(
                                1.5.dp,
                                AccentGradientStart,
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .clickable { onModeSelected(mode) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) AccentGradientStart.copy(alpha = 0.1f) else DarkCard
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) Color.White else SubtleText
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = SubtleText
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = AccentGradientStart,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard(onRequestExemption: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRequestExemption() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3E2D22)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFA726))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFA726).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFA726),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Restrictions Active",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = "App may freeze in background. Tap to set Battery usage to Unrestricted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleText
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = SubtleText,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
