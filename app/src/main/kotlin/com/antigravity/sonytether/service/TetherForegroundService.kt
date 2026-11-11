package com.antigravity.sonytether.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.antigravity.sonytether.MainActivity
import com.antigravity.sonytether.R
import com.antigravity.sonytether.engine.TetherEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "TetherForegroundService"
private const val NOTIFICATION_ID_ONGOING = 1001
private const val NOTIFICATION_ID_HEARTBEAT = 1002
private const val NOTIFICATION_ID_ALERT = 1003

private const val CHANNEL_ONGOING_ID = "tether_service_ongoing"
private const val CHANNEL_GARMIN_ID = "tether_garmin_alerts"

const val ACTION_STOP_SERVICE = "com.antigravity.sonytether.STOP_SERVICE"

class TetherForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var heartbeatJob: Job? = null
    private var wasUsbConnected: Boolean? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, TetherForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TetherForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Deteniendo servicio por petición de usuario (ACTION_STOP_SERVICE)")
            releaseWakeLock()
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancelAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        val engine = TetherEngine.getInstance(this)
        engine.startUsbTethering()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()

        val initialNotification = buildOngoingNotification(
            title = "Sony a6400 Tether Activo",
            content = "Listo para disparar y transferir fotos en segundo plano"
        )

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID_ONGOING, initialNotification, serviceType)
            Log.i(TAG, "TetherForegroundService iniciado en primer plano")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando startForeground: ${e.message}", e)
        }

        observeEngineState()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SonyTether::BackgroundSyncWakeLock").apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 horas máximo de salvaguarda
            }
            Log.i(TAG, "WakeLock adquirido para mantener CPU activa con pantalla apagada")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo adquirir WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "WakeLock liberado")
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            // 1. Canal silencioso para servicio persistente
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_ID,
                "Sony Tether - Estado Continuo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la aplicación activa con la pantalla apagada"
                setShowBadge(false)
            }

            // 2. Canal de alertas y avisos para el Smartwatch / Garmin (con vibración)
            val garminChannel = NotificationChannel(
                CHANNEL_GARMIN_ID,
                "Sony Tether - Avisos Garmin & Alertas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones periódicas y alertas inmediatas si se desconecta el cable"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
            }

            manager.createNotificationChannel(ongoingChannel)
            manager.createNotificationChannel(garminChannel)
        }
    }

    private fun observeEngineState() {
        val engine = TetherEngine.getInstance(this)

        // Monitoreo de estado de conexión USB
        serviceScope.launch {
            engine.isUsbCameraConnected.collectLatest { connected ->
                if (wasUsbConnected != null && wasUsbConnected != connected) {
                    if (!connected) {
                        // ¡ALERTA CRÍTICA PARA GARMIN! Cable desconectado
                        sendGarminAlert(
                            title = "⚠️ Sony a6400: Cable Desconectado",
                            message = "Se interrumpió la conexión USB con la cámara. Revisá el cable."
                        )
                    } else {
                        // Aviso de reconexión exitosa
                        sendGarminAlert(
                            title = "⚡ Sony a6400: Conectada",
                            message = "Conexión USB restablecida. Sesión de tethering activa."
                        )
                    }
                }
                wasUsbConnected = connected
                updateOngoingNotification()
            }
        }

        // Monitoreo de subidas y fotos para actualizar la barra
        serviceScope.launch {
            engine.momentiaManager.state.collectLatest {
                updateOngoingNotification()
            }
        }

        serviceScope.launch {
            engine.latestPhoto.collectLatest {
                updateOngoingNotification()
            }
        }

        // Iniciar loop de Heartbeat periódico para Garmin
        serviceScope.launch {
            engine.garminHeartbeatMinutes.collectLatest { minutes ->
                startHeartbeatTimer(minutes)
            }
        }
    }

    private fun startHeartbeatTimer(intervalMinutes: Int) {
        heartbeatJob?.cancel()
        if (intervalMinutes <= 0) {
            Log.i(TAG, "Heartbeat para Garmin desactivado")
            return
        }

        Log.i(TAG, "Iniciando Heartbeat para Garmin cada $intervalMinutes minutos")
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(intervalMinutes * 60 * 1000L)

                val engine = TetherEngine.getInstance(this@TetherForegroundService)
                val usbOk = engine.isUsbCameraConnected.value
                val uploaded = engine.momentiaManager.state.value.uploadedCount
                val pending = engine.pendingCountdownSeconds.value
                val batteryPct = getBatteryPercentage()

                val (title, statusText) = if (usbOk) {
                    "✅ Sony Tether: Todo OK" to "USB Conectado"
                } else {
                    "⚠️ Sony Tether: Cable Desconectado" to "Esperando USB"
                }
                val extraInfo = if (pending != null) " · 1 en espera" else ""

                sendGarminNotification(
                    title = title,
                    message = "$statusText · $uploaded subidas · Batería: $batteryPct%$extraInfo"
                )
            }
        }
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) {
            -1
        }
    }

    private fun updateOngoingNotification() {
        val engine = TetherEngine.getInstance(this)
        val isUsb = engine.isUsbCameraConnected.value
        val momentiaState = engine.momentiaManager.state.value
        val eventName = engine.activeEventName.value
        val latest = engine.latestPhoto.value

        val title = if (isUsb) "📷 Sony a6400 Conectada" else "📷 Sony Tether Activo"
        val content = buildString {
            append("Evento: $eventName")
            if (momentiaState.isLoggedIn && momentiaState.isAutoUploadEnabled) {
                append(" · ${momentiaState.uploadedCount} subidas a Momentia")
            }
            if (latest != null) {
                append(" · Última: ${latest.fileName}")
            }
        }

        val notification = buildOngoingNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID_ONGOING, notification)
    }

    private fun buildOngoingNotification(title: String, content: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TetherForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ONGOING_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener Tether", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun sendGarminNotification(title: String, message: String) {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_GARMIN_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID_HEARTBEAT, notification)
        Log.i(TAG, "Notificación Garmin emitida: $title - $message")
    }

    private fun sendGarminAlert(title: String, message: String) {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_GARMIN_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 600))
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID_ALERT, notification)
        Log.w(TAG, "Alerta emitida: $title - $message")
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.cancelAll()
        Log.i(TAG, "TetherForegroundService destruido y notificaciones canceladas")
    }
}
