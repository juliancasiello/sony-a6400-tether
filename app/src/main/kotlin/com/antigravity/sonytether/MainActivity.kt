package com.antigravity.sonytether

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.antigravity.sonytether.service.TetherForegroundService
import com.antigravity.sonytether.ui.theme.SonyTetherTheme

class MainActivity : ComponentActivity() {

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    // Si se concedieron los permisos o si ya están listos, arrancar el servicio en segundo plano
    TetherForegroundService.start(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Mantiene la pantalla siempre encendida mientras la app esté visible en pantalla
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    requestRequiredPermissions()

    // Iniciar servicio en primer plano para asegurar funcionamiento en segundo plano / mochila / Garmin
    TetherForegroundService.start(this)

    enableEdgeToEdge()
    setContent {
      SonyTetherTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MainNavigation()
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    TetherForegroundService.start(this)
  }

  private fun requestRequiredPermissions() {
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
      permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
      permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    if (permissions.isNotEmpty()) {
      permissionLauncher.launch(permissions.toTypedArray())
    }
  }
}
