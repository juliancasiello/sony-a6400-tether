# Payload SD para Sony a6400 (Tethering Wi-Fi Hotspot)

Archivos para habilitar el envío automático inalámbrico de fotos JPG desde la Sony a6400 hacia la aplicación Android mediante el Hotspot Wi-Fi del celular.

---

### 📋 Requisitos Previos en la Cámara
1. **Sony a6400 con parche `DevCrashDay/a6400` instalado** (habilita la ejecución de `autoexec.sh` al encender la cámara).
2. **Tarjeta SD formateada en FAT32**.
3. **Calidad de imagen:** Cámara configurada en **JPG** (L: 24M, Calidad `Standard`, `Fine` o `Extra Fine`).

---

### 🚀 Instrucciones de Instalación
1. **Configurar tu red Wi-Fi:**
   - Abrí el archivo `wpa.conf` con un editor de texto (Notepad, VS Code, etc.).
   - Reemplazá `TU_SSID_HOTSPOT` por el nombre exacto de tu red de Zona Wi-Fi (Hotspot) del teléfono.
   - Reemplazá `TU_PASSWORD_HOTSPOT` por la contraseña de tu Hotspot.
   - Guardá los cambios.

2. **Copiar a la Tarjeta SD:**
   Copiá los 3 archivos directamente a la **raíz** de tu tarjeta SD:
   ```
   SD_CARD/
   ├── autoexec.sh
   ├── wpa.conf
   └── watcher.sh
   ```

3. **Uso:**
   - Encendé la **Zona Wi-Fi (Hotspot)** en tu teléfono Android.
   - Abrí la app **Sony Alpha Tether** en tu teléfono (el servidor HTTP se iniciará en el puerto 8080).
   - Colocá la tarjeta SD en la cámara y encendela.
   - La cámara se conectará automáticamente al Wi-Fi de tu teléfono y comenzará a transmitir cada disparo en segundo plano.

---

### 🛠️ En caso de problemas (Modo Emergencia / Fallback)
- **Para volver al modo cable USB:** Simplemente enchufá el cable USB OTG entre la cámara y el teléfono. La app seguirá recibiendo por USB inmediatamente.
- **Para desactivar el script temporalmente:** Apagá la Zona Wi-Fi del teléfono o usá una tarjeta SD sin el archivo `autoexec.sh`.
