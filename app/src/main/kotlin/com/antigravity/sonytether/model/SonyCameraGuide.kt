package com.antigravity.sonytether.model

enum class CameraFamily(val label: String) {
    APS_C("APS-C"),
    FULL_FRAME("Full Frame"),
    CINEMA_VLOG("Cinema & Vlog")
}

enum class UsbConnectorType(val label: String, val iconDesc: String) {
    USB_C("USB Tipo-C", "Cable USB-C a USB-C de alta velocidad"),
    MICRO_USB("Micro-USB (Multi)", "Cable Micro-USB a USB-C / OTG")
}

enum class MenuGeneration(val label: String) {
    CLASSIC_PLAYMEMORIES("Menú Clásico Sony"),
    INTERMEDIATE_TABBED("Menú Pestañas (Color)"),
    MODERN_TOUCH_BIONZ_XR("Menú Moderno Táctil (BIONZ XR)")
}

data class SonyCameraModel(
    val id: String,
    val displayName: String,
    val shortName: String,
    val family: CameraFamily,
    val usbConnector: UsbConnectorType,
    val menuGeneration: MenuGeneration,
    val supportsUsbPcRemote: Boolean = true,
    val supportsFtpWireless: Boolean = false,
    val recommendedCable: String,
    val usbMenuSteps: List<String>,
    val pcSaveDestinationTip: String,
    val powerUsbTip: String,
    val wirelessFtpSteps: List<String>? = null
)

object SonyCameraDatabase {

    val cameras: List<SonyCameraModel> = listOf(
        // === APS-C ===
        SonyCameraModel(
            id = "a6400",
            displayName = "Sony A6400 (ILCE-6400)",
            shortName = "A6400",
            family = CameraFamily.APS_C,
            usbConnector = UsbConnectorType.MICRO_USB,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = false,
            recommendedCable = "Cable Micro-USB con adaptador USB OTG para conectar al celular (o cable Micro-USB a USB-C con soporte de datos).",
            usbMenuSteps = listOf(
                "Enciende la cámara y presiona [MENU].",
                "Ve a la pestaña [Red 1] o [Ajustes / Maletín 4].",
                "Busca [Función Control PC] y selecciónala.",
                "Pon [Control Remoto PC] en [ACTIVADO].",
                "En [Conexión USB], asegúrate de que esté en [Control remoto PC]."
            ),
            pcSaveDestinationTip = "En [Destino guardado Control PC], selecciona [PC + Cámara]. De esta forma, cada foto quedará guardada en tu tarjeta SD y simultáneamente viajará por cable a tu teléfono.",
            powerUsbTip = "Menú [Ajustes 3] > [Alimentación USB]: Puedes ponerlo en [Desactivado] si no quieres que la cámara consuma la batería de tu teléfono."
        ),
        SonyCameraModel(
            id = "a6700",
            displayName = "Sony A6700 (ILCE-6700)",
            shortName = "A6700",
            family = CameraFamily.APS_C,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.MODERN_TOUCH_BIONZ_XR,
            supportsUsbPcRemote = true,
            supportsFtpWireless = false,
            recommendedCable = "Cable USB-C a USB-C directo de alta velocidad (USB 3.2 Gen 2 recomendado).",
            usbMenuSteps = listOf(
                "Al conectar el cable USB-C a tu teléfono, en la pantalla de la cámara selecciona [Disparo remoto (PC Remote)].",
                "O desde el menú: [MENU] > [Red] > [Conexión/PC Remote] > [Disparo remoto].",
                "Establece [Disparo remoto] en [Activado].",
                "Método de conexión: [USB]."
            ),
            pcSaveDestinationTip = "En [Destino de guardado], elige [Ranura + PC] para guardar en tu tarjeta SD interna y en el celular al mismo tiempo.",
            powerUsbTip = "Menú [Ajustes] > [Opciones USB] > [Alimentación USB]: Elige según prefieras cargar la cámara desde el teléfono o ahorrar batería del móvil."
        ),
        SonyCameraModel(
            id = "a6600",
            displayName = "Sony A6600 (ILCE-6600)",
            shortName = "A6600",
            family = CameraFamily.APS_C,
            usbConnector = UsbConnectorType.MICRO_USB,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = false,
            recommendedCable = "Cable Micro-USB con adaptador OTG o cable Micro-USB a USB-C con transferencia de datos.",
            usbMenuSteps = listOf(
                "Ve a [MENU] > pestaña [Red 1].",
                "Entra a [Función Control PC] > [Control Remoto PC] > [Activado].",
                "En pestaña [Ajustes] > [Conexión USB] > [Control remoto PC]."
            ),
            pcSaveDestinationTip = "Selecciona [PC + Cámara] en destino guardado para asegurar copia local en tarjeta SD.",
            powerUsbTip = "Controla [Alimentación USB] en la pestaña de Ajustes según tu necesidad de batería."
        ),
        SonyCameraModel(
            id = "a6100",
            displayName = "Sony A6100 (ILCE-6100)",
            shortName = "A6100",
            family = CameraFamily.APS_C,
            usbConnector = UsbConnectorType.MICRO_USB,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = false,
            recommendedCable = "Cable Micro-USB con adaptador OTG.",
            usbMenuSteps = listOf(
                "Ve a [MENU] > [Red 1] > [Función Control PC] > [Activado].",
                "Ajustes de conexión USB: [Control remoto PC]."
            ),
            pcSaveDestinationTip = "Configura destino de fotos en [PC + Cámara].",
            powerUsbTip = "Desactiva la alimentación USB si notas consumo en el teléfono."
        ),
        SonyCameraModel(
            id = "a6000",
            displayName = "Sony A6000 (ILCE-6000)",
            shortName = "A6000",
            family = CameraFamily.APS_C,
            usbConnector = UsbConnectorType.MICRO_USB,
            menuGeneration = MenuGeneration.CLASSIC_PLAYMEMORIES,
            supportsUsbPcRemote = true,
            supportsFtpWireless = false,
            recommendedCable = "Cable Micro-USB con adaptador OTG.",
            usbMenuSteps = listOf(
                "Ve a [MENU] > pestaña [Configuración / Maletín 4].",
                "Selecciona [Conexión USB].",
                "Elige la opción [Control remoto PC]."
            ),
            pcSaveDestinationTip = "Nota para A6000: La A6000 guarda únicamente en PC/teléfono durante el disparo remoto por USB; verifica tener suficiente espacio en el celular.",
            powerUsbTip = "La A6000 se alimenta principalmente de su batería interna."
        ),
        SonyCameraModel(
            id = "a6300",
            displayName = "Sony A6300 (ILCE-6300)",
            shortName = "A6300",
            family = CameraFamily.APS_C,
            usbConnector = UsbConnectorType.MICRO_USB,
            menuGeneration = MenuGeneration.CLASSIC_PLAYMEMORIES,
            supportsUsbPcRemote = true,
            supportsFtpWireless = false,
            recommendedCable = "Cable Micro-USB con adaptador OTG.",
            usbMenuSteps = listOf(
                "Ve a [MENU] > [Configuración 4] > [Conexión USB] > [Control remoto PC]."
            ),
            pcSaveDestinationTip = "En modelos con firmware actualizado, permite guardar en PC + Cámara.",
            powerUsbTip = "Configura alimentación USB en Ajustes si necesitas ahorrar batería del móvil."
        ),
        SonyCameraModel(
            id = "a6500",
            displayName = "Sony A6500 (ILCE-6500)",
            shortName = "A6500",
            family = CameraFamily.APS_C,
            usbConnector = UsbConnectorType.MICRO_USB,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = false,
            recommendedCable = "Cable Micro-USB con adaptador OTG.",
            usbMenuSteps = listOf(
                "Ve a [MENU] > [Red 1] > [Función Control PC] > [Activado].",
                "Conexión USB: [Control remoto PC]."
            ),
            pcSaveDestinationTip = "Destino guardado: [PC + Cámara].",
            powerUsbTip = "Revisa Alimentación USB en Ajustes."
        ),

        // === FULL FRAME ===
        SonyCameraModel(
            id = "a7iii",
            displayName = "Sony A7 III (ILCE-7M3)",
            shortName = "A7 III",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C directo de alta velocidad conectado al puerto USB-C (SuperSpeed) de la cámara.",
            usbMenuSteps = listOf(
                "Enciende la cámara y presiona [MENU].",
                "Ve a [Red 1] > [Función Control PC].",
                "Pon [Control Remoto PC] en [ACTIVADO].",
                "Método de conexión: [USB].",
                "En [Ajustes 4], verifica que [Conexión USB] esté en [Control remoto PC]."
            ),
            pcSaveDestinationTip = "En [Destino guardado Control PC], selecciona [PC + Cámara]. ¡Esencial para que tus fotos queden en tu tarjeta SD!",
            powerUsbTip = "En [Ajustes 4] > [Alimentación USB], desactívalo si no deseas que la cámara consuma la batería de tu teléfono durante una jornada larga.",
            wirelessFtpSteps = listOf(
                "Enciende el Punto de Acceso WiFi (Hotspot) en tu teléfono Android.",
                "En la A7 III: [MENU] > [Red 1] > [Función Transf. FTP] > [Activado].",
                "En [Ajustes de servidor], añade la IP del teléfono (ej: 192.168.43.1) y puerto FTP 2121.",
                "Configura [Transferir durante el disparo] en [Activado]. Cada foto viajará por WiFi sin ningún cable."
            )
        ),
        SonyCameraModel(
            id = "a7iv",
            displayName = "Sony A7 IV (ILCE-7M4)",
            shortName = "A7 IV",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.MODERN_TOUCH_BIONZ_XR,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C SuperSpeed (10 Gbps) directo al puerto USB-C.",
            usbMenuSteps = listOf(
                "Al conectar el cable USB-C, en la pantalla táctil elige [Disparo remoto (PC Remote)].",
                "O en [MENU] > [Red] > [Conexión/PC Remote] > [Disparo remoto] > [Activado].",
                "Método de conexión: [USB]."
            ),
            pcSaveDestinationTip = "En [Destino de guardado], elige [Ranura + PC] (o Ranura 1/2 + PC).",
            powerUsbTip = "Menú [Ajustes] > [Opciones USB] > [Alimentación USB] desactivada para preservar batería del teléfono en coberturas de muchas horas.",
            wirelessFtpSteps = listOf(
                "Conecta la cámara al WiFi de tu móvil o a la red del evento.",
                "Ve a [MENU] > [Red] > [Transferencia FTP] > [Activado].",
                "Configura el servidor FTP con los datos provistos en la app.",
                "Cada disparo se transferirá automáticamente a máxima velocidad WiFi (5 GHz)."
            )
        ),
        SonyCameraModel(
            id = "a7riii",
            displayName = "Sony A7R III (ILCE-7RM3)",
            shortName = "A7R III",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C directo.",
            usbMenuSteps = listOf(
                "Ve a [MENU] > [Red 1] > [Función Control PC] > [Activado].",
                "Conexión USB: [Control remoto PC]."
            ),
            pcSaveDestinationTip = "Destino guardado: [PC + Cámara].",
            powerUsbTip = "Ajusta Alimentación USB en la pestaña de Ajustes.",
            wirelessFtpSteps = listOf(
                "Usa [Función Transf. FTP] en [Red 1] apuntando a la IP de tu teléfono."
            )
        ),
        SonyCameraModel(
            id = "a7riv",
            displayName = "Sony A7R IV (ILCE-7RM4)",
            shortName = "A7R IV",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C de alta velocidad.",
            usbMenuSteps = listOf(
                "Ve a [MENU] > [Red 1] > [Función Control PC] > [Activado].",
                "Conexión USB: [Control remoto PC]."
            ),
            pcSaveDestinationTip = "Destino guardado: [PC + Cámara].",
            powerUsbTip = "Revisa Alimentación USB en Ajustes.",
            wirelessFtpSteps = listOf(
                "Soporta WiFi 5 GHz y FTP integrado de alta velocidad."
            )
        ),
        SonyCameraModel(
            id = "a7rv",
            displayName = "Sony A7R V (ILCE-7RM5)",
            shortName = "A7R V",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.MODERN_TOUCH_BIONZ_XR,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C USB 3.2 Gen 2.",
            usbMenuSteps = listOf(
                "Elige [Disparo remoto (PC Remote)] en la pantalla o en [Red] > [Disparo remoto]."
            ),
            pcSaveDestinationTip = "Destino: [Ranura + PC].",
            powerUsbTip = "Alimentación USB configurable desde el menú de Ajustes.",
            wirelessFtpSteps = listOf(
                "Compatible con FTP automático sobre WiFi de 5 GHz."
            )
        ),
        SonyCameraModel(
            id = "a7c",
            displayName = "Sony A7C (ILCE-7C)",
            shortName = "A7C",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C directo.",
            usbMenuSteps = listOf(
                "Ve a [MENU] > [Red 1] > [Función Control PC] > [Activado].",
                "Conexión USB: [Control remoto PC]."
            ),
            pcSaveDestinationTip = "Destino guardado: [PC + Cámara].",
            powerUsbTip = "Alimentación USB en Ajustes.",
            wirelessFtpSteps = listOf(
                "Soporta transferencia FTP inalámbrica al hotspot del teléfono."
            )
        ),
        SonyCameraModel(
            id = "a7cii",
            displayName = "Sony A7C II (ILCE-7CM2)",
            shortName = "A7C II",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.MODERN_TOUCH_BIONZ_XR,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C.",
            usbMenuSteps = listOf(
                "Selecciona [Disparo remoto] al conectar o en [Red] > [Disparo remoto]."
            ),
            pcSaveDestinationTip = "Destino: [Ranura + PC].",
            powerUsbTip = "Alimentación USB en Ajustes.",
            wirelessFtpSteps = listOf(
                "Soporta transferencia FTP sobre WiFi."
            )
        ),
        SonyCameraModel(
            id = "a9",
            displayName = "Sony A9 / A9 II / A9 III",
            shortName = "Serie A9",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.INTERMEDIATE_TABBED,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C de alta velocidad.",
            usbMenuSteps = listOf(
                "Configurar [Función Control PC] en [Activado] o [Disparo remoto].",
                "Destino en [PC + Cámara]."
            ),
            pcSaveDestinationTip = "Guarda en Ranura 1/2 + PC.",
            powerUsbTip = "Desactiva alimentación USB para eventos largos.",
            wirelessFtpSteps = listOf(
                "Las cámaras serie A9 son las reinas del FTP deportivo: alta velocidad y transferencia en segundo plano ininterrumpida."
            )
        ),
        SonyCameraModel(
            id = "a1",
            displayName = "Sony A1 (ILCE-1)",
            shortName = "A1",
            family = CameraFamily.FULL_FRAME,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.MODERN_TOUCH_BIONZ_XR,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C 10 Gbps.",
            usbMenuSteps = listOf(
                "Selecciona [Disparo remoto (PC Remote)] en el menú Red."
            ),
            pcSaveDestinationTip = "Destino: [Ranura + PC].",
            powerUsbTip = "Alimentación USB configurable.",
            wirelessFtpSteps = listOf(
                "FTP de máxima velocidad con MIMO 2x2 sobre WiFi."
            )
        ),

        // === CINEMA & VLOG ===
        SonyCameraModel(
            id = "fx3",
            displayName = "Sony FX3 / FX30",
            shortName = "FX3 / FX30",
            family = CameraFamily.CINEMA_VLOG,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.MODERN_TOUCH_BIONZ_XR,
            supportsUsbPcRemote = true,
            supportsFtpWireless = true,
            recommendedCable = "Cable USB-C a USB-C.",
            usbMenuSteps = listOf(
                "En el menú de pantalla completa, ve a [Red] > [Conexión/PC Remote] > [Disparo remoto] > [Activado]."
            ),
            pcSaveDestinationTip = "Destino: [Ranura + PC].",
            powerUsbTip = "Alimentación USB configurable.",
            wirelessFtpSteps = listOf(
                "Soporta servidor FTP inalámbrico para transferencias rápidas."
            )
        ),
        SonyCameraModel(
            id = "zve10",
            displayName = "Sony ZV-E10 / ZV-E10 II",
            shortName = "ZV-E10",
            family = CameraFamily.CINEMA_VLOG,
            usbConnector = UsbConnectorType.USB_C,
            menuGeneration = MenuGeneration.MODERN_TOUCH_BIONZ_XR,
            supportsUsbPcRemote = true,
            supportsFtpWireless = false,
            recommendedCable = "Cable USB-C a USB-C directo.",
            usbMenuSteps = listOf(
                "Al enchufar el cable, selecciona [Disparo remoto (PC Remote)].",
                "O desde menú: [Red] > [Función Control PC / Disparo remoto] > [Activado]."
            ),
            pcSaveDestinationTip = "Destino: [PC + Cámara] o [Ranura + PC].",
            powerUsbTip = "Alimentación USB configurable."
        )
    )

    fun findById(id: String): SonyCameraModel {
        return cameras.find { it.id.equals(id, ignoreCase = true) } ?: cameras.first()
    }

    fun matchFromProductName(productName: String?): SonyCameraModel? {
        if (productName == null) return null
        val lower = productName.lowercase()
        return cameras.find { cam ->
            lower.contains(cam.shortName.lowercase()) ||
            lower.contains(cam.id.lowercase()) ||
            lower.contains(cam.displayName.lowercase())
        }
    }
}
