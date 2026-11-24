#!/bin/sh
# ==============================================================================
# watcher.sh - Vigilante y transmisor HTTP para Sony a6400 (BusyBox / ash)
# ==============================================================================
# Restricciones estrictas:
# - Solo comandos estándar de BusyBox (ash / sh)
# - SIN curl, SIN bash, SIN inotifywait
# - Optimizado para carpetas con >2.000 fotos JPG de 24 MP
# - Doble verificación de tamaño antes de transmitir
# - Transmisión HTTP POST cruda vía netcat (nc)
# ==============================================================================

DCIM_BASE="/mnt/sd/DCIM"
SENT_LOG="/tmp/sent_files.txt"
RESP_LOG="/tmp/nc_resp.txt"
DISCOVER_LOG="/tmp/discovery.txt"

# Servidor por defecto (Gateway estándar del Hotspot Wi-Fi de Android)
DEFAULT_SERVER_IP="192.168.43.1"
DEFAULT_SERVER_PORT="8080"
UDP_DISCOVERY_PORT="8081"

SERVER_IP="$DEFAULT_SERVER_IP"
SERVER_PORT="$DEFAULT_SERVER_PORT"

touch "$SENT_LOG"

echo "=== [$(date '+%Y-%m-%d %H:%M:%S')] Iniciando vigilante Wi-Fi Sony a6400 ==="

# ------------------------------------------------------------------------------
# Función: Descubrir IP del teléfono mediante broadcast UDP
# ------------------------------------------------------------------------------
discover_phone_ip() {
    # Intentar obtener la IP de la puerta de enlace (Gateway) de la ruta activa
    GATEWAY_IP=$(route -n 2>/dev/null | grep '^0.0.0.0' | awk '{print $2}' | head -n 1)
    if [ -n "$GATEWAY_IP" ] && [ "$GATEWAY_IP" != "0.0.0.0" ]; then
        SERVER_IP="$GATEWAY_IP"
    else
        SERVER_IP="$DEFAULT_SERVER_IP"
    fi

    # Intentar descubrimiento dinámico por UDP broadcast en puerto 8081
    rm -f "$DISCOVER_LOG"
    (
        echo "A6400_DISCOVER" | nc -u -w 2 255.255.255.255 "$UDP_DISCOVERY_PORT" 2>/dev/null
    ) > "$DISCOVER_LOG" 2>&1

    if [ -s "$DISCOVER_LOG" ]; then
        DISCOVERED_IP=$(grep 'A6400_SERVER_IP:' "$DISCOVER_LOG" | head -n 1 | sed 's/.*A6400_SERVER_IP://' | cut -d':' -f1 | tr -d '\r\n ')
        if [ -n "$DISCOVERED_IP" ]; then
            SERVER_IP="$DISCOVERED_IP"
            echo "IP del teléfono descubierta por UDP: $SERVER_IP"
        fi
    fi
}

# Realizar primer intento de descubrimiento
discover_phone_ip

# ------------------------------------------------------------------------------
# Bucle principal de vigilancia
# ------------------------------------------------------------------------------
LOOP_COUNTER=0

while true; do
    # Cada 60 iteraciones (~30 segundos), revalidar IP de la red si cambia
    LOOP_COUNTER=$((LOOP_COUNTER + 1))
    if [ "$LOOP_COUNTER" -ge 60 ]; then
        LOOP_COUNTER=0
        discover_phone_ip
    fi

    # Si no existe la carpeta DCIM todavía, esperar
    if [ ! -d "$DCIM_BASE" ]; then
        sleep 1
        continue
    fi

    # OPTIMIZACIÓN >2.000 FOTOS:
    # Identificar las carpetas más recientes de DCIM (últimas 2 por orden temporal).
    # Las cámaras Sony agrupan fotos en subcarpetas de hasta 9.999 fotos (ej. 100MSDCF, 101MSDCF).
    RECENT_DIRS=$(ls -td "$DCIM_BASE"/*/ 2>/dev/null | head -n 2)

    if [ -z "$RECENT_DIRS" ]; then
        sleep 1
        continue
    fi

    for TARGET_DIR in $RECENT_DIRS; do
        # Listar archivos JPG y jpg en la carpeta actual
        for FILE_PATH in "$TARGET_DIR"*.JPG "$TARGET_DIR"*.jpg; do
            # Verificar si el patrón coincidió con un archivo real
            if [ ! -f "$FILE_PATH" ]; then
                continue
            fi

            FILE_NAME=$(basename "$FILE_PATH")

            # 1. Comprobación rápida en memoria si ya fue enviado
            if grep -q -F "$FILE_NAME" "$SENT_LOG" 2>/dev/null; then
                continue
            fi

            # 2. DOBLE VERIFICACIÓN DE TAMAÑO (Evitar archivos incompletos durante ráfagas)
            SIZE1=$(wc -c < "$FILE_PATH" 2>/dev/null | tr -d ' ')
            if [ -z "$SIZE1" ] || [ "$SIZE1" -le 0 ]; then
                continue
            fi

            sleep 0.3

            SIZE2=$(wc -c < "$FILE_PATH" 2>/dev/null | tr -d ' ')
            if [ "$SIZE1" != "$SIZE2" ]; then
                # La cámara sigue escribiendo el archivo en la SD; esperar próxima pasada
                continue
            fi

            echo "[$(date '+%H:%M:%S')] Nueva foto lista: $FILE_NAME ($SIZE2 bytes). Enviando a $SERVER_IP:$SERVER_PORT..."

            # 3. ENVÍO HTTP POST CRUDO VÍA NETCAT
            # Se genera la cabecera HTTP exacta seguida del flujo binario del archivo
            rm -f "$RESP_LOG"
            (
                printf "POST /upload?filename=%s HTTP/1.1\r\n" "$FILE_NAME"
                printf "Host: %s:%s\r\n" "$SERVER_IP" "$SERVER_PORT"
                printf "X-Filename: %s\r\n" "$FILE_NAME"
                printf "Content-Length: %s\r\n" "$SIZE2"
                printf "Connection: close\r\n\r\n"
                cat "$FILE_PATH"
            ) | nc -w 12 "$SERVER_IP" "$SERVER_PORT" > "$RESP_LOG" 2>/dev/null
            NC_STATUS=$?

            # Verificar respuesta del servidor HTTP
            if [ "$NC_STATUS" -eq 0 ] && grep -q -E "200 OK|201 Created|200" "$RESP_LOG" 2>/dev/null; then
                echo "$FILE_NAME" >> "$SENT_LOG"
                echo "[$(date '+%H:%M:%S')] ENVIADA CON ÉXITO: $FILE_NAME"
            else
                echo "[$(date '+%H:%M:%S')] Reintento pendiente para: $FILE_NAME (nc status: $NC_STATUS)"
                # Pequeña pausa si hubo error de conexión antes de reintentar
                sleep 0.5
            fi
        done
    done

    # Pausa de bajo consumo para no saturar la CPU de la cámara (<1% CPU)
    sleep 0.5
done
