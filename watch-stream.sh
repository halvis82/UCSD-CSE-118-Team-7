#!/bin/bash

# Live sensor data stream from Galaxy Watch
# Run this script to see real-time sensor data updates

echo "=========================================="
echo "  LIVE SENSOR DATA FROM GALAXY WATCH"
echo "  Press Ctrl+C to stop"
echo "=========================================="
echo ""

LAST_TIMESTAMP=""

while true; do
  RESULT=$(curl -s http://localhost:3000/recent | python3 -c "
import sys, json
from datetime import datetime

try:
    data = json.load(sys.stdin)['data']
    if data:
        latest = data[-1]
        timestamp = latest.get('server_timestamp', 'N/A')
        print(f\"{timestamp}|[{datetime.now().strftime('%H:%M:%S')}] HR: {latest.get('heartRate', 0):>5.1f} bpm | Accel: X={latest.get('accelX', 0):>6.2f} Y={latest.get('accelY', 0):>6.2f} Z={latest.get('accelZ', 0):>6.2f} | Gyro: X={latest.get('gyroX', 0):>6.3f} Y={latest.get('gyroY', 0):>6.3f} Z={latest.get('gyroZ', 0):>6.3f} | Steps: {latest.get('steps', 0)}\")
except:
    pass
" 2>/dev/null)

  if [ -n "$RESULT" ]; then
    NEW_TIMESTAMP=$(echo "$RESULT" | cut -d'|' -f1)
    OUTPUT=$(echo "$RESULT" | cut -d'|' -f2-)

    if [ "$NEW_TIMESTAMP" != "$LAST_TIMESTAMP" ]; then
      echo "$OUTPUT"
      LAST_TIMESTAMP="$NEW_TIMESTAMP"
    fi
  fi

  sleep 1
done
