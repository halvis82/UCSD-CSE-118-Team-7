package com.cse118.watchsensor

/**
 * Data model for watch context to be written to DynamoDB
 */
data class WatchContext(
    val heartRate: Int,
    val movement: String,  // "HIGH", "MEDIUM", "LOW", "SEDENTARY"
    val timestamp: Long    // Epoch time in seconds
) {
    companion object {
        const val USER_ID = "MY_ALEXA_USER"  // Fixed partition key
    }
}
