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

    private lateinit var dynamoDBWriter: DynamoDBWriter
    private val classifier = ContextClassifier()

    private var lastHeartRate: Float = 0f
    private var lastValidHeartRate: Float = 70f // Cache last valid HR, default to 70 bpm
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
        const val ACTION_SENSOR_UPDATE = "com.cse118.watchsensor.SENSOR_UPDATE"
        const val EXTRA_CONTEXT_STATE = "context_state"
        const val EXTRA_HEART_RATE = "heart_rate"
        const val EXTRA_ACCEL_MAGNITUDE = "accel_magnitude"
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
            val configFile = File(filesDir, "aws-config.properties")
            if (configFile.exists()) {
                val props = Properties()
                FileInputStream(configFile).use { props.load(it) }

                dynamoDBWriter = DynamoDBWriter(
                    accessKeyId = props.getProperty("aws_access_key_id", ""),
                    secretKey = props.getProperty("aws_secret_key", ""),
                    regionName = props.getProperty("aws_region", "us-east-1"),
                    tableName = props.getProperty("dynamodb_table_name", "ContextMusicData")
                )
            } else {
                System.err.println("AWS config file not found at ${configFile.absolutePath}")
            }
        } catch (e: Exception) {
            System.err.println("Error loading AWS config: ${e.message}")
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
                // Cache valid heart rate readings (non-zero)
                if (lastHeartRate > 0) {
                    lastValidHeartRate = lastHeartRate
                }
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
                sendDataToDynamoDB()
                delay(SEND_INTERVAL_MS)
            }
        }
    }

    private fun sendDataToDynamoDB() {
        if (!::dynamoDBWriter.isInitialized) {
            System.err.println("DynamoDB writer not initialized, skipping write")
            return
        }

        serviceScope.launch {
            try {
                // Use cached heart rate if current reading is 0
                val heartRateToUse = if (lastHeartRate > 0) lastHeartRate else lastValidHeartRate

                // Classify context based on sensor data
                val contextState = classifier.classifyContext(
                    lastAccelX,
                    lastAccelY,
                    lastAccelZ,
                    heartRateToUse
                )

                // Create context object
                val context = WatchContext(
                    heartRate = heartRateToUse.toInt(),
                    movement = contextState,
                    timestamp = System.currentTimeMillis() / 1000
                )

                // Write to DynamoDB
                val success = dynamoDBWriter.writeContextData(context)
                if (success) {
                    println("Successfully wrote context to DynamoDB: HR=${context.heartRate}, Movement=${context.movement}")
                } else {
                    System.err.println("Failed to write context to DynamoDB")
                }

                // Broadcast sensor data to MainActivity
                val magnitude = kotlin.math.sqrt(lastAccelX * lastAccelX + lastAccelY * lastAccelY + lastAccelZ * lastAccelZ)
                val broadcastIntent = Intent(ACTION_SENSOR_UPDATE).apply {
                    putExtra(EXTRA_CONTEXT_STATE, contextState)
                    putExtra(EXTRA_HEART_RATE, heartRateToUse.toInt())
                    putExtra(EXTRA_ACCEL_MAGNITUDE, magnitude)
                }
                sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                System.err.println("Error sending data to DynamoDB: ${e.message}")
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
