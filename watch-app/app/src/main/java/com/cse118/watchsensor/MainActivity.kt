package com.cse118.watchsensor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private var isMonitoring = false

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

        statusText = findViewById(R.id.status_text)
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
            statusText.text = getString(R.string.status_running)
            toggleButton.text = getString(R.string.stop_monitoring)
        } else {
            statusText.text = getString(R.string.status_stopped)
            toggleButton.text = getString(R.string.start_monitoring)
        }
    }
}
