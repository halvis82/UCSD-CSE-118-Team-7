package com.cse118.watchsensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val httpClient = OkHttpClient()
    private var backendUrl: String = ""

    private var lastHeartRate: Float = 0f
    private var lastAccelX: Float = 0f
    private var lastAccelY: Float = 0f
    private var lastAccelZ: Float = 0f
    private var lastGyroX: Float = 0f
    private var lastGyroY: Float = 0f
    private var lastGyroZ: Float = 0f
    private var lastStepCount: Float = 0f

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "sensor_monitoring"
        private const val NOTIFICATION_ID = 1
        private const val SEND_INTERVAL_MS = 5000L // Send data every 5 seconds
    }

    override fun onCreate() {
        super.onCreate()

        // Load configuration
        loadConfig()

        // Initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Acquire wake lock to keep service running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CSE118::SensorWakeLock"
        )
        wakeLock.acquire()

        // Create notification channel
        createNotificationChannel()

        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Register sensor listeners
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Start periodic data sending
        startDataSending()
    }

    private fun loadConfig() {
        try {
            val configFile = File(filesDir, "config.properties")
            if (configFile.exists()) {
                val props = Properties()
                FileInputStream(configFile).use { props.load(it) }
                backendUrl = props.getProperty("backend_url", "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Sensor Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                lastHeartRate = event.values[0]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelX = event.values[0]
                lastAccelY = event.values[1]
                lastAccelZ = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0]
                lastGyroY = event.values[1]
                lastGyroZ = event.values[2]
            }
            Sensor.TYPE_STEP_COUNTER -> {
                lastStepCount = event.values[0]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not needed for this implementation
    }

    private fun startDataSending() {
        serviceScope.launch {
            while (isActive) {
                sendDataToBackend()
                delay(SEND_INTERVAL_MS)
            }
        }
    }

    private fun sendDataToBackend() {
        if (backendUrl.isEmpty()) return

        serviceScope.launch {
            try {
                val jsonObject = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("heartRate", lastHeartRate)
                    put("accelX", lastAccelX)
                    put("accelY", lastAccelY)
                    put("accelZ", lastAccelZ)
                    put("gyroX", lastGyroX)
                    put("gyroY", lastGyroY)
                    put("gyroZ", lastGyroZ)
                    put("steps", lastStepCount.toInt())
                }

                val requestBody = jsonObject.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(backendUrl)
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Log error but continue monitoring
                        println("Failed to send data: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
