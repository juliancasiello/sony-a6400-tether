#!/bin/sh
# ==============================================================================
# autoexec.sh - Arranque automático para Sony a6400 (DevCrashDay/a6400)
# ==============================================================================
# Este script se ejecuta automáticamente al encender la cámara con la tarjeta SD.
# Montaje esperado: /mnt/sd/
# ==============================================================================

LOG_FILE="/tmp/autoexec.log"
echo "=== [$(date '+%Y-%m-%d %H:%M:%S')] Iniciando autoexec.sh Sony a6400 ===" > "$LOG_FILE"

SD_ROOT="/mnt/sd"
PAYLOAD_DIR="$SD_ROOT/sd_payload"

# Localizar archivo de configuración WPA (raíz de la SD o en /sd_payload/)
WPA_CONF=""
if [ -f "$SD_ROOT/wpa.conf" ]; then
    WPA_CONF="$SD_ROOT/wpa.conf"
elif [ -f "$PAYLOAD_DIR/wpa.conf" ]; then
    WPA_CONF="$PAYLOAD_DIR/wpa.conf"
fi

# Localizar script vigilante de fotos (watcher.sh)
WATCHER_SCRIPT=""
if [ -f "$SD_ROOT/watcher.sh" ]; then
    WATCHER_SCRIPT="$SD_ROOT/watcher.sh"
elif [ -f "$PAYLOAD_DIR/watcher.sh" ]; then
    WATCHER_SCRIPT="$PAYLOAD_DIR/watcher.sh"
fi

if [ -z "$WPA_CONF" ]; then
    echo "ERROR: No se encontró wpa.conf en $SD_ROOT ni en $PAYLOAD_DIR" >> "$LOG_FILE"
    exit 1
fi

if [ -z "$WATCHER_SCRIPT" ]; then
    echo "ERROR: No se encontró watcher.sh en $SD_ROOT ni en $PAYLOAD_DIR" >> "$LOG_FILE"
    exit 1
fi

chmod +x "$WATCHER_SCRIPT" 2>/dev/null

# Levantar interfaz de red Wi-Fi de la cámara
echo "Levantando interfaz wlan0..." >> "$LOG_FILE"
ifconfig wlan0 up >> "$LOG_FILE" 2>&1
sleep 1

# Matar instancias previas si existieran
killall -9 wpa_supplicant 2>/dev/null
killall -9 udhcpc 2>/dev/null
sleep 1

# Iniciar wpa_supplicant para conectarse al Hotspot
echo "Iniciando wpa_supplicant con $WPA_CONF..." >> "$LOG_FILE"
wpa_supplicant -B -i wlan0 -c "$WPA_CONF" >> "$LOG_FILE" 2>&1
sleep 2

# Solicitar IP por DHCP al teléfono en segundo plano
echo "Solicitando concesión DHCP en wlan0..." >> "$LOG_FILE"
udhcpc -i wlan0 -b -p /tmp/udhcpc.pid >> "$LOG_FILE" 2>&1

# Lanzar vigilante de fotos desacoplado en segundo plano
echo "Lanzando watcher.sh: $WATCHER_SCRIPT..." >> "$LOG_FILE"
sh "$WATCHER_SCRIPT" > /tmp/watcher.log 2>&1 &

echo "=== autoexec.sh completado exitosamente ===" >> "$LOG_FILE"
exit 0
