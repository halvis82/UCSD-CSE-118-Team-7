package com.cse118.watchsensor

import kotlin.math.sqrt
import kotlin.math.abs

/**
 * Context classifier to determine user activity state from sensor data
 * States: SLEEPING, RESTING, ACTIVE, WORKOUT
 *
 * Timing:
 * - WORKOUT: Instant (no delay)
 * - SLEEPING: 2.5 minutes with 65% low HR + mostly still
 * - ACTIVE/RESTING: 30 seconds majority vote
 */
class ContextClassifier {

    private val recentClassifications = ArrayDeque<String>(6).apply {
        // Initialize with RESTING as default startup state
        repeat(6) { add("RESTING") }
    }
    private val HISTORY_SIZE = 6

    // Sleep detection tracking (2.5 minutes = 30 readings)
    private val sleepHistorySize = 30
    private val recentHeartRates = ArrayDeque<Float>(sleepHistorySize)
    private val recentMovements = ArrayDeque<Float>(sleepHistorySize)

    companion object {
        // Movement thresholds (m/s² deviation from gravity)
        private const val VERY_STILL_THRESHOLD = 0.5f
        private const val LOW_MOVEMENT_THRESHOLD = 1.5f
        private const val MODERATE_MOVEMENT_THRESHOLD = 3.5f

        // Heart rate thresholds (bpm)
        private const val LOW_HR_THRESHOLD = 65f
        private const val RESTING_HR_THRESHOLD = 85f
        private const val ACTIVE_HR_THRESHOLD = 115f
    }

    /**
     * Classifies user context based on movement and heart rate with temporal smoothing
     * @param accelX X-axis acceleration (m/s²)
     * @param accelY Y-axis acceleration (m/s²)
     * @param accelZ Z-axis acceleration (m/s²)
     * @param heartRate Current heart rate (bpm)
     * @return Context: "WORKOUT", "ACTIVE", "RESTING", or "SLEEPING"
     */
    fun classifyContext(
        accelX: Float,
        accelY: Float,
        accelZ: Float,
        heartRate: Float
    ): String {
        // Calculate acceleration magnitude (subtract gravity ~9.8)
        val magnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
        val movementIntensity = abs(magnitude - 9.8f)

        // Track heart rate and movement history for sleep detection
        recentHeartRates.addLast(heartRate)
        if (recentHeartRates.size > sleepHistorySize) {
            recentHeartRates.removeFirst()
        }

        recentMovements.addLast(movementIntensity)
        if (recentMovements.size > sleepHistorySize) {
            recentMovements.removeFirst()
        }

        // Determine raw classification (instantaneous)
        val rawClassification = classifyRaw(movementIntensity, heartRate)

        // WORKOUT is always instant - return immediately
        if (rawClassification == "WORKOUT") {
            recentClassifications.clear()  // Clear history when working out
            return "WORKOUT"
        }

        // Add to history buffer
        recentClassifications.addLast(rawClassification)
        if (recentClassifications.size > HISTORY_SIZE) {
            recentClassifications.removeFirst()
        }

        // Check for SLEEPING using percentage-based detection
        if (checkSleepingConditions()) {
            return "SLEEPING"
        }

        // For ACTIVE and RESTING: use majority vote from last 30 seconds
        return getMajorityClassification()
    }

    /**
     * Raw classification without temporal smoothing
     */
    private fun classifyRaw(movementIntensity: Float, heartRate: Float): String {
        // Priority 1: WORKOUT - high movement or elevated heart rate
        if (movementIntensity > MODERATE_MOVEMENT_THRESHOLD || heartRate > ACTIVE_HR_THRESHOLD) {
            return "WORKOUT"
        }

        // Priority 2: ACTIVE - moderate movement or moderate heart rate
        if (movementIntensity > LOW_MOVEMENT_THRESHOLD || heartRate > RESTING_HR_THRESHOLD) {
            return "ACTIVE"
        }

        // Priority 3: RESTING - default for low activity
        return "RESTING"
    }

    /**
     * Check if sleeping conditions are met using percentage-based detection
     * Requires 2.5 minutes of data (30 readings)
     */
    private fun checkSleepingConditions(): Boolean {
        // Need full history (2.5 minutes of data)
        if (recentHeartRates.size < sleepHistorySize || recentMovements.size < sleepHistorySize) {
            return false
        }

        // Count readings with low heart rate (< 65 bpm)
        val lowHrCount = recentHeartRates.count { it < LOW_HR_THRESHOLD }
        val lowHrPercentage = lowHrCount.toFloat() / sleepHistorySize

        // Count readings with very little movement (< 0.5 m/s²)
        val stillCount = recentMovements.count { it < VERY_STILL_THRESHOLD }
        val stillPercentage = stillCount.toFloat() / sleepHistorySize

        // SLEEPING: 65% of readings with low HR AND 80% of readings very still
        // Movement is more important - people should be mostly motionless when sleeping
        return lowHrPercentage >= 0.65f && stillPercentage >= 0.80f
    }

    /**
     * Returns the most common classification from recent history
     */
    private fun getMajorityClassification(): String {
        if (recentClassifications.isEmpty()) {
            return "RESTING"
        }

        // Count occurrences
        val counts = recentClassifications.groupingBy { it }.eachCount()

        // Return the most frequent classification
        return counts.maxByOrNull { it.value }?.key ?: "RESTING"
    }
}
