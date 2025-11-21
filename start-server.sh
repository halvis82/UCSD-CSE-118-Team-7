#!/bin/bash

# Start the Flask backend server for Galaxy Watch sensor data
# This server receives data from the watch app

cd "$(dirname "$0")/backend/server"

echo "Starting Flask server on port 3000..."
echo "Server will be available at http://localhost:3000"
echo ""
echo "Endpoints:"
echo "  - POST /sensor-data  (receives data from watch)"
echo "  - GET  /recent       (view last 100 data points)"
echo "  - GET  /health       (health check)"
echo ""

PORT=3000 ./venv/bin/python app.py
