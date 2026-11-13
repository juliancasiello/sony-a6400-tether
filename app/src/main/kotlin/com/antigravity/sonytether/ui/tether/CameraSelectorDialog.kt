package com.antigravity.sonytether.ui.tether

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.sonytether.model.CameraFamily
import com.antigravity.sonytether.model.SonyCameraDatabase
import com.antigravity.sonytether.model.SonyCameraModel
import com.antigravity.sonytether.model.UsbConnectorType
import com.antigravity.sonytether.network.FtpServerState
import com.antigravity.sonytether.network.PtpIpClientState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraSelectorDialog(
    currentCamera: SonyCameraModel,
    ftpState: FtpServerState,
    ptpIpState: PtpIpClientState,
    onToggleFtpServer: () -> Unit,
    onConnectPtpIp: (String) -> Unit,
    onDisconnectPtpIp: () -> Unit,
    onSelectCamera: (SonyCameraModel) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCameraId by remember { mutableStateOf<String?>(currentCamera.id) }

    val tabTitles = listOf("Todas", "APS-C", "Full Frame", "Vlog & Cine", "📡 Modo WiFi")

    val filteredCameras = remember(selectedTab, searchQuery) {
        var list = SonyCameraDatabase.cameras
        when (selectedTab) {
            1 -> list = list.filter { it.family == CameraFamily.APS_C }
            2 -> list = list.filter { it.family == CameraFamily.FULL_FRAME }
            3 -> list = list.filter { it.family == CameraFamily.CINEMA_VLOG }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.displayName.lowercase().contains(q) ||
                it.shortName.lowercase().contains(q)
            }
        }
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Selector & Guía de Cámara",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Seleccioná tu modelo Sony y mirá cómo conectarla",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 4) {
                    // TAB 4: MODO INALÁMBRICO (WIFI / FTP)
                    WirelessGuideContent(
                        ftpState = ftpState,
                        ptpIpState = ptpIpState,
                        onToggleFtp = onToggleFtpServer,
                        onConnectPtpIp = onConnectPtpIp,
                        onDisconnectPtpIp = onDisconnectPtpIp
                    )
                } else {
                    // BUSCADOR
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        placeholder = { Text("Buscar modelo (ej: A7 III, 6700, FX3)...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true
                    )

                    // LISTA DE CÁMARAS
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCameras, key = { it.id }) { camera ->
                            val isCurrent = camera.id == currentCamera.id
                            val isExpanded = expandedCameraId == camera.id

                            CameraCardItem(
                                camera = camera,
                                isSelected = isCurrent,
                                isExpanded = isExpanded,
                                onSelect = {
                                    onSelectCamera(camera)
                                    expandedCameraId = camera.id
                                },
                                onToggleExpand = {
                                    expandedCameraId = if (isExpanded) null else camera.id
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Listo")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraCardItem(
    camera: SonyCameraModel,
    isSelected: Boolean,
    isExpanded: Boolean,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = camera.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Activa",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BadgePill(label = camera.family.label, color = MaterialTheme.colorScheme.secondary)
                        BadgePill(
                            label = camera.usbConnector.label,
                            color = if (camera.usbConnector == UsbConnectorType.USB_C) Color(0xFF00897B) else Color(0xFF5E35B1)
                        )
                        if (camera.supportsFtpWireless) {
                            BadgePill(label = "📡 WiFi FTP", color = Color(0xFF1E88E5))
                        }
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Colapsar" else "Expandir"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (!isSelected) {
                        Button(
                            onClick = onSelect,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Seleccionar como mi cámara")
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 1. CABLE
                    SectionTitle(icon = Icons.Default.Cable, title = "Cable Requerido")
                    Text(
                        text = camera.recommendedCable,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. PASO A PASO MENÚ
                    SectionTitle(icon = Icons.Default.Info, title = "Configuración en Menú de la Cámara")
                    camera.usbMenuSteps.forEachIndexed { i, step ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${i + 1}. ",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. DESTINO DE FOTOS
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "💾 Destino de Fotos (¡Clave!):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = camera.pcSaveDestinationTip,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 4. BATERÍA
                    Text(
                        text = "🔋 Batería: ${camera.powerUsbTip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (camera.supportsFtpWireless && camera.wirelessFtpSteps != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5).copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "📡 Modo Inalámbrico FTP Disponible:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E88E5)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                camera.wirelessFtpSteps.forEach { step ->
                                    Text(
                                        text = "• $step",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WirelessGuideContent(
    ftpState: FtpServerState,
    ptpIpState: PtpIpClientState,
    onToggleFtp: () -> Unit,
    onConnectPtpIp: (String) -> Unit,
    onDisconnectPtpIp: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (ftpState.isRunning) Color(0xFF00C853).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (ftpState.isRunning) Color(0xFF00C853) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (ftpState.isRunning) Color(0xFF00C853) else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (ftpState.isRunning) "Servidor FTP ACTIVO" else "Servidor FTP INACTIVO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (ftpState.isRunning) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = onToggleFtp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ftpState.isRunning) Color(0xFFD32F2F) else Color(0xFF1E88E5)
                            )
                        ) {
                            Text(if (ftpState.isRunning) "Detener" else "Activar")
                        }
                    }

                    if (ftpState.isRunning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Configurá estos datos exactos en el menú FTP de tu Sony:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("IP Servidor (Host):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(ftpState.localIp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Puerto FTP:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${ftpState.port}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Usuario:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(ftpState.username, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Contraseña:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(ftpState.password, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("📷 Cámaras: ${ftpState.connectedClientsCount}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("📥 Fotos recibidas: ${ftpState.totalReceivedCount}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item {
            var targetCameraIp by remember { mutableStateOf(ptpIpState.cameraIp.ifBlank { "192.168.43." }) }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (ptpIpState.isConnected) Color(0xFF00C853).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (ptpIpState.isConnected) Color(0xFF00C853) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (ptpIpState.isConnected) Color(0xFF00C853) else if (ptpIpState.isConnecting) Color(0xFFFF9800) else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (ptpIpState.isConnected) "A6700 PTP/IP CONECTADA" else "Disparo Remoto WiFi (A6700)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (ptpIpState.isConnected) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                if (ptpIpState.isConnected) {
                                    onDisconnectPtpIp()
                                } else {
                                    onConnectPtpIp(targetCameraIp.trim())
                                }
                            },
                            enabled = !ptpIpState.isConnecting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ptpIpState.isConnected) Color(0xFFD32F2F) else Color(0xFF5E35B1)
                            )
                        ) {
                            Text(if (ptpIpState.isConnected) "Desconectar" else if (ptpIpState.isConnecting) "Conectando..." else "Conectar")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Para cámaras sin FTP nativo como la A6700. La cámara y el teléfono deben estar en la misma red WiFi o Hotspot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!ptpIpState.isConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = targetCameraIp,
                            onValueChange = { targetCameraIp = it },
                            label = { Text("IP de la Cámara Sony (ej: 192.168.43.50)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("📷 ${ptpIpState.cameraName ?: "Sony A6700"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("📥 Fotos recibidas: ${ptpIpState.photosReceivedCount}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (!ptpIpState.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Error: ${ptpIpState.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "¿Cómo funciona el modo Inalámbrico?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E88E5)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "En fotografía deportiva y de eventos profesional, la forma estándar de trabajar sin cables con cámaras Sony es mediante el protocolo FTP nativo de la cámara:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Encendés el Punto de Acceso (Hotspot WiFi) de tu celular Android.\n" +
                               "2. Conectás la cámara al WiFi de tu teléfono.\n" +
                               "3. La cámara transfiere automáticamente cada disparo al servidor en segundo plano mientras te movés libremente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📷 Cámaras Sony con FTP integrado:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Serie A7: A7 III, A7 IV, A7R III, A7R IV, A7R V, A7S III, A7C, A7C II\n" +
                               "• Serie A9 / A1: A9, A9 II, A9 III, A1\n" +
                               "• Serie Cinema: FX3, FX30",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Estas cámaras incluyen un cliente FTP en su propio firmware capaz de transferir fotos por WiFi en segundo plano ininterrumpido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "¿Y las cámaras APS-C (A6400, A6100, A6000)?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sony no incluyó cliente de transferencia FTP en el firmware de la línea A6400/A6000. Por eso, para estas cámaras el método por cable USB OTG es el más rápido, seguro y sin latencia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BadgePill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
