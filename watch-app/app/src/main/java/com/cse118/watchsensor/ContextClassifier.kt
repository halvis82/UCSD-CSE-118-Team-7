package com.cse118.watchsensor

import kotlin.math.sqrt

/**
 * Simple rule-based classifier to determine user context from sensor data
 */
class ContextClassifier {

    /**
     * Classifies movement intensity based on accelerometer magnitude
     * @param accelX X-axis acceleration (m/s²)
     * @param accelY Y-axis acceleration (m/s²)
     * @param accelZ Z-axis acceleration (m/s²)
     * @param heartRate Current heart rate (bpm)
     * @return Movement classification: "HIGH", "MEDIUM", "LOW", or "SEDENTARY"
     */
    fun classifyMovement(
        accelX: Float,
        accelY: Float,
        accelZ: Float,
        heartRate: Float
    ): String {
        // Calculate acceleration magnitude (subtract gravity ~9.8)
        val magnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
        val movementIntensity = kotlin.math.abs(magnitude - 9.8f)

        // Classify based on movement intensity and heart rate
        return when {
            // High intensity: vigorous movement or high HR
            movementIntensity > 3.0f || heartRate > 120 -> "HIGH"

            // Medium intensity: moderate movement or elevated HR
            movementIntensity > 1.5f || heartRate > 90 -> "MEDIUM"

            // Low intensity: slight movement, normal HR
            movementIntensity > 0.5f -> "LOW"

            // Sedentary: minimal movement, low/resting HR
            else -> "SEDENTARY"
        }
    }
}
