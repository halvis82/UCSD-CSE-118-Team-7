import os

from flask import Flask, request, jsonify

app = Flask(__name__)

# Simple health check
@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"}), 200


# Endpoint to receive context from the watch/bridge
@app.route("/context", methods=["POST"])
def context():
    data = request.get_json(force=True, silent=True) or {}

    # Expected payload example:
    # {
    #   "state": "relaxed",     # "sleeping", "relaxed", "active", "workout"
    #   "heart_rate": 72,
    #   "source": "watch"
    # }

    state = data.get("state", "unknown")
    heart_rate = data.get("heart_rate")

    print(f"Received context: state={state}, hr={heart_rate}, raw={data}")

    # TODO: here is where you'll later call Alexa / trigger audio

    return jsonify({"received": True, "state": state}), 200


if __name__ == "__main__":
    # Runs on all interfaces so phone/watch on same Wi-Fi can hit it
    port = int(os.environ.get("PORT", os.environ.get("APP_PORT", 5000)))
    app.run(host="0.0.0.0", port=port, debug=True)
