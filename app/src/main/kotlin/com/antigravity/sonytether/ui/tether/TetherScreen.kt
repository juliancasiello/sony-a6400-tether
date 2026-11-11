package com.antigravity.sonytether.ui.tether

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import com.antigravity.sonytether.lumepic.LumepicAlbum
import com.antigravity.sonytether.lumepic.LumepicUploadState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.antigravity.sonytether.editor.PresetConfig
import com.antigravity.sonytether.model.CameraConnectionState
import com.antigravity.sonytether.model.TetheredPhoto
import com.antigravity.sonytether.model.UploadStatus
import com.antigravity.sonytether.momentia.MomentiaEvent
import com.antigravity.sonytether.momentia.MomentiaUploadState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TetherScreen(
    modifier: Modifier = Modifier,
    viewModel: TetherViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val latestPhoto by viewModel.latestPhoto.collectAsStateWithLifecycle()
    val selectedPhoto by viewModel.selectedPhoto.collectAsStateWithLifecycle()
    val presetConfig by viewModel.presetConfig.collectAsStateWithLifecycle()
    val heroPreviewBitmap by viewModel.heroPreviewBitmap.collectAsStateWithLifecycle()
    val pendingCountdownSeconds by viewModel.pendingCountdownSeconds.collectAsStateWithLifecycle()
    val isUsbCameraConnected by viewModel.isUsbCameraConnected.collectAsStateWithLifecycle()
    val momentiaState by viewModel.momentiaState.collectAsStateWithLifecycle()
    val lumepicState by viewModel.lumepicState.collectAsStateWithLifecycle()
    val activeEventName by viewModel.activeEventName.collectAsStateWithLifecycle()
    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val garminHeartbeatMinutes by viewModel.garminHeartbeatMinutes.collectAsStateWithLifecycle()
    val isComparingOriginal by viewModel.isComparingOriginal.collectAsStateWithLifecycle()
    val isBwCopyEnabled by viewModel.isBwCopyEnabled.collectAsStateWithLifecycle()

    var isEditingPreset by rememberSaveable { mutableStateOf(false) }
    var showMomentiaDialog by remember { mutableStateOf(false) }
    var showMomentiaLoginDialog by remember { mutableStateOf(false) }
    var showMomentiaAlbumPicker by remember { mutableStateOf(false) }

    var showLumepicDialog by remember { mutableStateOf(false) }
    var showLumepicLoginDialog by remember { mutableStateOf(false) }
    var showLumepicAlbumPicker by remember { mutableStateOf(false) }

    var showEventSettings by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val currentDisplayPhoto = selectedPhoto ?: latestPhoto
    val isViewingPending = currentDisplayPhoto != null && pendingCountdownSeconds != null && currentDisplayPhoto.fileName == latestPhoto?.fileName

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isLandscape) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sony a6400",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isUsbCameraConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "📁 $activeEventName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Sony a6400",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (isUsbCameraConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                            shape = CircleShape
                                        )
                                )
                            }
                            Text(
                                text = "📁 $activeEventName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Toggle rápido para subir copia en blanco y negro (+ B&N)
                    BwTogglePill(
                        isEnabled = isBwCopyEnabled,
                        onClick = { viewModel.toggleBwCopy() }
                    )

                    // Pill de estado de Lumepic
                    LumepicStatusPill(
                        state = lumepicState,
                        onClick = { showLumepicDialog = true }
                    )

                    // Pill de estado de Momentia
                    MomentiaStatusPill(
                        state = momentiaState,
                        onClick = { showMomentiaDialog = true }
                    )

                    IconButton(onClick = { showEventSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes del Evento")
                    }

                    IconButton(onClick = { showExitConfirmDialog = true }) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Detener y Cerrar App",
                            tint = Color(0xFFFF5252)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (isLandscape) {
            // ==============================================================
            // ORIENTACIÓN HORIZONTAL (LANDSCAPE - IDEAL PARA MONTAJE EN CÁMARA)
            // ==============================================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0F1015))
            ) {
                // 1. Visor principal de la foto en formato panorámico
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    PhotoHeroView(
                        photo = currentDisplayPhoto,
                        isUsbCameraConnected = isUsbCameraConnected,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 2. Tira vertical de miniaturas a la derecha
                Surface(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight(),
                    color = Color(0xFF161822)
                ) {
                    if (photos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Sin fotos",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(photos, key = { it.id }) { photo ->
                                val isSelected = currentDisplayPhoto?.id == photo.id
                                ThumbnailCard(
                                    photo = photo,
                                    isSelected = isSelected,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    onClick = { viewModel.selectPhoto(photo) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ==============================================================
            // ORIENTACIÓN VERTICAL (PORTRAIT)
            // ==============================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0F1015))
            ) {
                // 1. Visor principal de la foto en la parte superior
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    PhotoHeroView(
                        photo = currentDisplayPhoto,
                        isUsbCameraConnected = isUsbCameraConnected,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 2. Tira horizontal de miniaturas inferior
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    color = Color(0xFF161822)
                ) {
                    if (photos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Aún no hay fotos en este evento",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(photos, key = { it.id }) { photo ->
                                val isSelected = currentDisplayPhoto?.id == photo.id
                                ThumbnailCard(
                                    photo = photo,
                                    isSelected = isSelected,
                                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                                    onClick = { viewModel.selectPhoto(photo) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // DIÁLOGO DE AJUSTES DEL EVENTO Y SINCRONIZACIÓN
    if (showEventSettings) {
        EventSettingsDialog(
            activeEventName = activeEventName,
            recentEvents = recentEvents,
            garminHeartbeatMinutes = garminHeartbeatMinutes,
            onGarminHeartbeatChange = { viewModel.setGarminHeartbeatMinutes(it) },
            isBwCopyEnabled = isBwCopyEnabled,
            onBwCopyChange = { viewModel.setBwCopyEnabled(it) },
            onEventNameChange = { viewModel.setActiveEventName(it) },
            onDeleteEvent = { viewModel.deleteEventPhotos(it) },
            onClearHistory = { viewModel.clearDownloadedHistory() },
            logs = logs,
            onExitAppClick = { showExitConfirmDialog = true },
            onDismiss = { showEventSettings = false }
        )
    }

    // DIÁLOGO DE MOMENTIA
    if (showMomentiaDialog) {
        MomentiaStatusDialog(
            state = momentiaState,
            onLoginClick = {
                showMomentiaDialog = false
                showMomentiaLoginDialog = true
            },
            onLogoutClick = { viewModel.logoutMomentia() },
            onSelectAlbumClick = { showMomentiaAlbumPicker = true },
            onToggleAutoUpload = { viewModel.setMomentiaAutoUpload(it) },
            onRefreshAlbums = { viewModel.refreshMomentiaEvents() },
            onDismiss = { showMomentiaDialog = false }
        )
    }

    if (showMomentiaLoginDialog) {
        MomentiaLoginDialog(
            onDismiss = { showMomentiaLoginDialog = false },
            onLogin = { email, pass, onResult ->
                viewModel.loginMomentia(email, pass, onResult)
            }
        )
    }

    if (showMomentiaAlbumPicker) {
        MomentiaAlbumPickerDialog(
            events = momentiaState.availableEvents,
            selectedEventId = momentiaState.selectedEventId,
            onSelect = { viewModel.selectMomentiaEvent(it) },
            onDismiss = { showMomentiaAlbumPicker = false }
        )
    }

    // DIÁLOGOS DE LUMEPIC
    if (showLumepicDialog) {
        LumepicStatusDialog(
            state = lumepicState,
            onLoginClick = {
                showLumepicDialog = false
                showLumepicLoginDialog = true
            },
            onLogoutClick = { viewModel.logoutLumepic() },
            onSelectAlbumClick = { showLumepicAlbumPicker = true },
            onToggleAutoUpload = { viewModel.setLumepicAutoUpload(it) },
            onRefreshAlbums = { viewModel.refreshLumepicAlbums() },
            onDismiss = { showLumepicDialog = false }
        )
    }

    if (showLumepicLoginDialog) {
        LumepicLoginDialog(
            onDismiss = { showLumepicLoginDialog = false },
            onLoginSuccess = { cookieHeader ->
                viewModel.setLumepicCookies(cookieHeader)
            }
        )
    }

    if (showLumepicAlbumPicker) {
        LumepicAlbumPickerDialog(
            albums = lumepicState.availableAlbums,
            selectedAlbumId = lumepicState.selectedAlbumId,
            onSelect = { viewModel.selectLumepicAlbum(it) },
            onDismiss = { showLumepicAlbumPicker = false }
        )
    }

    // DIÁLOGO DE CONFIRMACIÓN DE SALIDA DEFINITIVA
    if (showExitConfirmDialog) {
        val context = LocalContext.current
        ExitConfirmDialog(
            onConfirm = {
                showExitConfirmDialog = false
                viewModel.shutdownAndExit(context)
            },
            onDismiss = { showExitConfirmDialog = false }
        )
    }
}

/**
 * Diálogo de Login con Momentia
 */
@Composable
private fun MomentiaLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (email: String, pass: String, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Iniciar Sesión en Momentia", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ingresá tus credenciales de Momentia para habilitar la subida automática directa de fotos editadas.",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        isLoading = true
                        errorMessage = null
                        onLogin(email, password) { success, err ->
                            isLoading = false
                            if (success) {
                                onDismiss()
                            } else {
                                errorMessage = err ?: "Error al iniciar sesión"
                            }
                        }
                    }
                },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                } else {
                    Text("Iniciar Sesión")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Pill minimalista de estado de subida a Momentia
 */
@Composable
private fun MomentiaStatusPill(
    state: MomentiaUploadState,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        !state.isLoggedIn -> Color.Gray.copy(alpha = 0.2f)
        state.isUploading -> Color(0xFF7C5CFC).copy(alpha = 0.25f)
        state.isAutoUploadEnabled -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        else -> Color.DarkGray.copy(alpha = 0.3f)
    }

    val contentColor = when {
        !state.isLoggedIn -> MaterialTheme.colorScheme.onSurfaceVariant
        state.isUploading -> Color(0xFF9E86FF)
        state.isAutoUploadEnabled -> Color(0xFF81C784)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
                Text(
                    text = "Subiendo",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            } else if (state.isLoggedIn && state.isAutoUploadEnabled) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${state.uploadedCount} subidas",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            } else if (state.isLoggedIn) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Pausado",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            } else {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Momentia",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Toggle rápido en la barra superior para activar/desactivar copia en Blanco y Negro (B&N)
 */
@Composable
private fun BwTogglePill(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isEnabled) Color(0xFFEEEEEE) else Color.DarkGray.copy(alpha = 0.35f),
        border = if (isEnabled) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isEnabled) Color.Black else Color.Gray,
                        shape = CircleShape
                    )
            )
            Text(
                text = if (isEnabled) "+ Copia B&N" else "+ B&N Off",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) Color.Black else Color.LightGray
            )
        }
    }
}

/**
 * Miniatura para el carrete inferior
 */
@Composable
private fun ThumbnailCard(
    photo: TetheredPhoto,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF9E86FF) else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        if (photo.localUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.localUri)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }

        // Distintivo "B&N" en la esquina inferior izquierda si es copia monocromática
        val isBw = photo.fileName.contains("_BN.", ignoreCase = true) || photo.remoteUrl.startsWith("bw://")
        if (isBw) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "B&N",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // Badge de estado de subida en la esquina superior derecha
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            when (photo.uploadStatus) {
                UploadStatus.COMPLETED -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Subido",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(14.dp)
                    )
                }
                UploadStatus.UPLOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF9E86FF)
                    )
                }
                UploadStatus.QUEUED -> {
                    Icon(
                        Icons.Default.HourglassTop,
                        contentDescription = "En espera",
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(14.dp)
                    )
                }
                else -> {}
            }
        }
    }
}

/**
 * Visor central de la foto actual (Hero Photo)
 * Muestra la imagen fiel original directamente recibida por USB de la Sony a6400.
 */
@Composable
private fun PhotoHeroView(
    photo: TetheredPhoto?,
    isUsbCameraConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (photo != null) {
            if (photo.localUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.localUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Badge superior izquierdo con nombre de archivo y peso
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.70f),
                shape = RoundedCornerShape(14.dp)
            ) {
                val mb = photo.fileSizeBytes / (1024f * 1024f)
                val sizeText = if (mb > 0) " (%.1f MB)".format(mb) else ""
                val isBw = photo.fileName.contains("_BN.", ignoreCase = true) || photo.remoteUrl.startsWith("bw://")

                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isBw) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "B&N",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "${photo.fileName}$sizeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Badge superior derecho con estado de subida a Momentia
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                color = when (photo.uploadStatus) {
                    UploadStatus.COMPLETED -> Color(0xFF1B5E20).copy(alpha = 0.85f)
                    UploadStatus.UPLOADING -> Color(0xFF4A148C).copy(alpha = 0.85f)
                    UploadStatus.QUEUED -> Color(0xFFE65100).copy(alpha = 0.85f)
                    else -> Color.Black.copy(alpha = 0.70f)
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (photo.uploadStatus) {
                        UploadStatus.COMPLETED -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                            Text("Subido", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        UploadStatus.UPLOADING -> {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(12.dp))
                            Text("Subiendo...", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        UploadStatus.QUEUED -> {
                            Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFFFCC80), modifier = Modifier.size(14.dp))
                            Text("En cola para subir", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        else -> {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                            Text("Guardada en celular", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                        }
                    }
                }
            }
        } else {
            // Estado vacío esperando fotos
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (isUsbCameraConnected) "Cámara conectada por USB" else "Esperando conexión USB...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isUsbCameraConnected)
                            "Dispará con la Sony a6400. La foto aparecerá aquí y se subirá automáticamente a Momentia."
                        else
                            "Conectá el cable USB a la cámara en modo 'PC remoto'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Diálogo de Administración de Momentia
 */
@Composable
private fun MomentiaStatusDialog(
    state: MomentiaUploadState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSelectAlbumClick: () -> Unit,
    onToggleAutoUpload: (Boolean) -> Unit,
    onRefreshAlbums: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF7C5CFC))
                Text("Subida a Momentia", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.isLoggedIn) {
                    Text(
                        text = "Conectado como: ${state.userName ?: state.userEmail}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Álbum destino
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Álbum Destino:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = state.selectedEventName ?: "Ningún álbum seleccionado",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.selectedEventId != null) Color(0xFF7C5CFC) else MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onSelectAlbumClick, modifier = Modifier.weight(1f)) {
                                    Text("Elegir Álbum")
                                }
                                IconButton(onClick = onRefreshAlbums) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                                }
                            }
                        }
                    }

                    // Switch Subida Automática
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Subir fotos al disparar", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Envía las fotos a Momentia automáticamente (y copia B&N si está activa)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.isAutoUploadEnabled,
                            onCheckedChange = onToggleAutoUpload
                        )
                    }

                    // Estadísticas de subida
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text("Subidas: ${state.uploadedCount}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        if (state.failedCount > 0) {
                            Text("Fallidas: ${state.failedCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    OutlinedButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cerrar Sesión")
                    }
                } else {
                    Text(
                        "Conectá tu cuenta de Momentia para que cada disparo con la a6400 se guarde, edite y suba directamente a tu álbum.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
                    ) {
                        Text("Iniciar Sesión en Momentia")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

/**
 * Diálogo de Ajustes del Evento, Almacenamiento y Notificaciones Garmin
 */
@Composable
private fun EventSettingsDialog(
    activeEventName: String,
    recentEvents: List<String>,
    garminHeartbeatMinutes: Int,
    onGarminHeartbeatChange: (Int) -> Unit,
    isBwCopyEnabled: Boolean,
    onBwCopyChange: (Boolean) -> Unit,
    onEventNameChange: (String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onClearHistory: () -> Unit,
    logs: List<String>,
    onExitAppClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var newEventName by remember { mutableStateOf(activeEventName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustes del Evento y Segundo Plano", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = newEventName,
                    onValueChange = { newEventName = it },
                    label = { Text("Nombre de Carpeta del Evento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (newEventName.isNotBlank()) {
                            onEventNameChange(newEventName.trim())
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar Carpeta")
                }

                Spacer(modifier = Modifier.height(4.dp))

                // SECCIÓN COPIA BLANCO Y NEGRO (B&N)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Subir copia adicional en B&N", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Al capturar una foto, guarda y sube automáticamente dos fotos: la original en color + una versión en blanco y negro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isBwCopyEnabled,
                            onCheckedChange = onBwCopyChange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // SECCIÓN GARMIN Y MODO MOCHILA
                Text("🎒 Modo Mochila & Smartwatch Garmin", fontWeight = FontWeight.Bold)

                Text(
                    text = "Avisos de tranquilidad 'Todo OK' periódicos a tu reloj:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val intervals = listOf(0 to "Off", 5 to "5 min", 10 to "10 min", 15 to "15 min")
                    intervals.forEach { (mins, label) ->
                        val isSel = garminHeartbeatMinutes == mins
                        FilterChip(
                            selected = isSel,
                            onClick = { onGarminHeartbeatChange(mins) },
                            label = { Text(label) }
                        )
                    }
                }

                Text(
                    text = "El servicio en segundo plano mantiene la transferencia activa con la pantalla apagada en la mochila o bolsillo. Tu Garmin vibrará periódicamente confirmando que todo está OK y te alertará al instante si se desconecta el cable USB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text("Gestión de Almacenamiento", fontWeight = FontWeight.Bold)

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eliminar fotos de este evento del cel")
                }

                Text(
                    text = "Conserva las fotos originales en la SD de la cámara y borra las copias del celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // SECCIÓN CERRAR APP Y DETENER TETHER
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                            Text("Detener Tether y Cerrar", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                        }
                        Text(
                            text = "Apaga el servicio en segundo plano, corta las notificaciones y cierra la app para no gastar batería ni recibir alertas de cable desconectado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                onDismiss()
                                onExitAppClick()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detener Servicio y Salir")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar fotos del evento?") },
            text = { Text("Se borrarán las copias locales de '$activeEventName' para liberar espacio en el teléfono. Los originales en la SD de la cámara no se tocan.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEvent(activeEventName)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Diálogo de Confirmación de Cierre y Apagado del Servicio
 */
@Composable
private fun ExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(32.dp))
        },
        title = { Text("¿Detener Tether y Cerrar?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Esta acción detendrá por completo el servicio en segundo plano, liberará la conexión USB con la cámara Sony, cancelará las notificaciones y cerrará la app.\n\n" +
                "Ideal para cuando termines la cobertura del evento y quieras que no consuma batería ni envíe alertas de cable desconectado.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("Detener y Cerrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Pill minimalista de estado de subida a Lumepic
 */
@Composable
private fun LumepicStatusPill(
    state: LumepicUploadState,
    onClick: () -> Unit
) {
    val lumepicColor = Color(0xFF00B4D8)
    val backgroundColor = when {
        !state.isLoggedIn -> Color.Gray.copy(alpha = 0.2f)
        state.isUploading -> lumepicColor.copy(alpha = 0.25f)
        state.isAutoUploadEnabled -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        else -> Color.DarkGray.copy(alpha = 0.3f)
    }

    val contentColor = when {
        !state.isLoggedIn -> MaterialTheme.colorScheme.onSurfaceVariant
        state.isUploading -> lumepicColor
        state.isAutoUploadEnabled -> Color(0xFF81C784)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
                Text(
                    text = "Lumepic...",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            } else if (state.isLoggedIn && state.isAutoUploadEnabled) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${state.uploadedCount} L",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            } else if (state.isLoggedIn) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Lumi Pausa",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            } else {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Lumepic",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Diálogo de Administración y Estado de Lumepic
 */
@Composable
private fun LumepicStatusDialog(
    state: LumepicUploadState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSelectAlbumClick: () -> Unit,
    onToggleAutoUpload: (Boolean) -> Unit,
    onRefreshAlbums: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF00B4D8))
                Text("Subida a Lumepic", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.isLoggedIn) {
                    Text(
                        text = "Conectado a Lumepic (${state.userEmail ?: "Sesión activa"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Álbum destino
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Álbum Destino en Lumepic:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = state.selectedAlbumName ?: "Ningún álbum seleccionado",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.selectedAlbumId != null) Color(0xFF00B4D8) else MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onSelectAlbumClick,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B4D8))
                                ) {
                                    Text("Elegir Álbum", color = Color.White)
                                }
                                IconButton(onClick = onRefreshAlbums) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                                }
                            }
                        }
                    }

                    // Switch Subida Automática
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Subir fotos al disparar", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Envía a Lumepic con metadatos EXIF (y copia B&N si está activa)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.isAutoUploadEnabled,
                            onCheckedChange = onToggleAutoUpload
                        )
                    }

                    // Estadísticas de subida
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text("Subidas: ${state.uploadedCount}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        if (state.failedCount > 0) {
                            Text("Fallidas: ${state.failedCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    OutlinedButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cerrar Sesión")
                    }
                } else {
                    Text(
                        "Conectá tu cuenta de fotógrafo en Lumepic para subir directamente tus capturas a tus álbumes activos mientras hacés la cobertura.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B4D8))
                    ) {
                        Text("Iniciar Sesión en Lumepic", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

/**
 * Diálogo de Login en Lumepic (con WebView y fallback manual de cookies)
 */
@Composable
private fun LumepicLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (cookies: String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var manualCookie by remember { mutableStateOf("") }
    var detectedCookies by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val cookieManager = remember { CookieManager.getInstance() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF00B4D8))
                Text("Conectar con Lumepic", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Navegador Web") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Cookie Manual") }
                    )
                }

                if (selectedTab == 0) {
                    Text(
                        text = "1. Iniciá sesión con tu cuenta de fotógrafo.\n2. Al entrar a tu panel de álbumes, tocá 'Detectar Sesión'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            val currentCookies = cookieManager.getCookie("https://photographer.lumepic.com")
                                            if (!currentCookies.isNullOrBlank()) {
                                                detectedCookies = currentCookies
                                            }
                                        }
                                    }
                                    loadUrl("https://photographer.lumepic.com/auth/login")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Button(
                        onClick = {
                            val cookies = detectedCookies ?: cookieManager.getCookie("https://photographer.lumepic.com")
                            if (!cookies.isNullOrBlank() && (cookies.contains("lumepic") || cookies.contains("session") || cookies.length > 20)) {
                                onLoginSuccess(cookies)
                                onDismiss()
                            } else {
                                statusMessage = "Aún no se detectaron cookies de sesión activas. Asegurate de iniciar sesión en la web de arriba."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B4D8))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Detectar Sesión y Guardar", color = Color.White)
                    }
                } else {
                    Text(
                        text = "Pegá acá el encabezado Cookie copiado del navegador o DevTools (incluye lumepic-photographer.session-token, etc.):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = manualCookie,
                        onValueChange = { manualCookie = it; statusMessage = null },
                        label = { Text("Cookie Header") },
                        placeholder = { Text("lumepic-photographer.session-token=...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 6
                    )

                    Button(
                        onClick = {
                            if (manualCookie.isNotBlank()) {
                                onLoginSuccess(manualCookie.trim())
                                onDismiss()
                            } else {
                                statusMessage = "Por favor pegá una cookie válida."
                            }
                        },
                        enabled = manualCookie.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B4D8))
                    ) {
                        Text("Guardar Cookie Manual", color = Color.White)
                    }
                }

                if (statusMessage != null) {
                    Text(
                        text = statusMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo de Selector de Álbum de Lumepic
 */
@Composable
private fun LumepicAlbumPickerDialog(
    albums: List<LumepicAlbum>,
    selectedAlbumId: String?,
    onSelect: (LumepicAlbum) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir Álbum de Lumepic", fontWeight = FontWeight.Bold) },
        text = {
            if (albums.isEmpty()) {
                Text("No se encontraron álbumes en tu cuenta de Lumepic. Tocá actualizar o creá uno en photographer.lumepic.com.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    albums.forEach { album ->
                        val isSelected = album.id == selectedAlbumId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(album)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF00B4D8).copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF00B4D8) else MaterialTheme.colorScheme.onSurface
                                    )
                                    val subtitle = buildString {
                                        album.date?.let { append(it) }
                                        if (album.status.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(album.status)
                                        }
                                        if (album.photosCount > 0) {
                                            if (isNotEmpty()) append(" • ")
                                            append("${album.photosCount} fotos")
                                        }
                                    }
                                    if (subtitle.isNotBlank()) {
                                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00B4D8), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

/**
 * Diálogo de Selector de Álbum de Momentia
 */
@Composable
private fun MomentiaAlbumPickerDialog(
    events: List<MomentiaEvent>,
    selectedEventId: String?,
    onSelect: (MomentiaEvent) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir Álbum de Momentia", fontWeight = FontWeight.Bold) },
        text = {
            if (events.isEmpty()) {
                Text("No se encontraron álbumes en tu cuenta. Tocá actualizar o creá uno en la web.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    events.forEach { event ->
                        val isSelected = event.id == selectedEventId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(event)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF7C5CFC).copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF7C5CFC) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!event.date.isNullOrBlank()) {
                                        Text(text = event.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF7C5CFC), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
