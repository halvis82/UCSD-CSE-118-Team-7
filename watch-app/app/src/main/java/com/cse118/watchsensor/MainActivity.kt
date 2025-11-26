package com.cse118.watchsensor

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var contextStateText: TextView
    private lateinit var heartRateText: TextView
    private lateinit var accelText: TextView
    private lateinit var toggleButton: Button
    private var isMonitoring = false

    private val sensorUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val contextState = it.getStringExtra(SensorService.EXTRA_CONTEXT_STATE) ?: "UNKNOWN"
                val heartRate = it.getIntExtra(SensorService.EXTRA_HEART_RATE, 0)
                val accelMagnitude = it.getFloatExtra(SensorService.EXTRA_ACCEL_MAGNITUDE, 0f)

                updateSensorDisplay(contextState, heartRate, accelMagnitude)
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contextStateText = findViewById(R.id.context_state)
        heartRateText = findViewById(R.id.heart_rate_text)
        accelText = findViewById(R.id.accel_text)
        toggleButton = findViewById(R.id.toggle_button)

        toggleButton.setOnClickListener {
            if (checkPermissions()) {
                toggleMonitoring()
            } else {
                requestPermissions()
            }
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(SensorService.ACTION_SENSOR_UPDATE)
        registerReceiver(sensorUpdateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(sensorUpdateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                toggleMonitoring()
            }
        }
    }

    private fun toggleMonitoring() {
        if (isMonitoring) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        val intent = Intent(this, SensorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isMonitoring = true
        updateUI()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, SensorService::class.java)
        stopService(intent)
        isMonitoring = false
        updateUI()
    }

    private fun updateUI() {
        if (isMonitoring) {
            toggleButton.text = getString(R.string.stop_monitoring)
        } else {
            toggleButton.text = getString(R.string.start_monitoring)
        }
    }

    private fun updateSensorDisplay(contextState: String, heartRate: Int, accelMagnitude: Float) {
        runOnUiThread {
            contextStateText.text = contextState
            heartRateText.text = "HR: $heartRate bpm"
            accelText.text = String.format("Accel: %.1f m/s²", accelMagnitude)

            // Update context state color based on state
            val color = when (contextState) {
                "WORKOUT" -> android.graphics.Color.parseColor("#FF5722") // Red-orange
                "ACTIVE" -> android.graphics.Color.parseColor("#FFC107") // Amber
                "RESTING" -> android.graphics.Color.parseColor("#00BCD4") // Cyan
                "SLEEPING" -> android.graphics.Color.parseColor("#9C27B0") // Purple
                else -> android.graphics.Color.parseColor("#607D8B") // Blue-grey
            }
            contextStateText.setTextColor(color)
        }
    }
}
