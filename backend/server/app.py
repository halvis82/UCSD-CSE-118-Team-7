import os
from datetime import datetime
from flask import Flask, request, jsonify

app = Flask(__name__)

# Store recent sensor data for display
recent_data = []
MAX_RECENT = 100

# Simple health check
@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"}), 200


# Endpoint to receive raw sensor data from watch
@app.route("/sensor-data", methods=["POST"])
def sensor_data():
    data = request.get_json(force=True, silent=True) or {}

    # Add server timestamp
    data['server_timestamp'] = datetime.now().isoformat()

    # Store for display
    recent_data.append(data)
    if len(recent_data) > MAX_RECENT:
        recent_data.pop(0)

    # Print live data to console
    timestamp = data.get('timestamp', 0)
    hr = data.get('heartRate', 0)
    accel_x = data.get('accelX', 0)
    accel_y = data.get('accelY', 0)
    accel_z = data.get('accelZ', 0)
    gyro_x = data.get('gyroX', 0)
    gyro_y = data.get('gyroY', 0)
    gyro_z = data.get('gyroZ', 0)
    steps = data.get('steps', 0)

    # Calculate acceleration magnitude
    accel_mag = (accel_x**2 + accel_y**2 + accel_z**2)**0.5

    print(f"\n{'='*80}")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] SENSOR DATA RECEIVED")
    print(f"{'='*80}")
    print(f"  Heart Rate:     {hr:.1f} bpm")
    print(f"  Accelerometer:  X={accel_x:7.3f}  Y={accel_y:7.3f}  Z={accel_z:7.3f}  |Mag|={accel_mag:7.3f}")
    print(f"  Gyroscope:      X={gyro_x:7.3f}  Y={gyro_y:7.3f}  Z={gyro_z:7.3f}")
    print(f"  Steps:          {steps}")
    print(f"  Watch Time:     {timestamp}")
    print(f"{'='*80}\n")

    return jsonify({"received": True, "count": len(recent_data)}), 200


# Endpoint to view recent data
@app.route("/recent", methods=["GET"])
def get_recent():
    return jsonify({"data": recent_data, "count": len(recent_data)}), 200


# Endpoint to receive context from the watch/bridge (legacy, for classifier)
@app.route("/context", methods=["POST"])
def context():
    data = request.get_json(force=True, silent=True) or {}

    state = data.get("state", "unknown")
    heart_rate = data.get("heart_rate")

    print(f"\n[CONTEXT] State: {state}, HR: {heart_rate}")

    # TODO: here is where you'll later call Alexa / trigger audio

    return jsonify({"received": True, "state": state}), 200


if __name__ == "__main__":
    # Runs on all interfaces so phone/watch on same Wi-Fi can hit it
    port = int(os.environ.get("PORT", os.environ.get("APP_PORT", 5000)))
    print(f"\n{'='*80}")
    print(f"  SENSOR DATA SERVER STARTING")
    print(f"  Listening on: http://0.0.0.0:{port}")
    print(f"  Endpoints:")
    print(f"    POST /sensor-data  - Receive raw sensor data from watch")
    print(f"    GET  /recent       - View recent sensor data")
    print(f"    POST /context      - Receive classified context")
    print(f"    GET  /health       - Health check")
    print(f"{'='*80}\n")
    app.run(host="0.0.0.0", port=port, debug=True)
