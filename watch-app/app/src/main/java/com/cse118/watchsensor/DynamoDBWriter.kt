package com.cse118.watchsensor

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

class DynamoDBWriter(
    private val accessKeyId: String,
    private val secretKey: String,
    private val regionName: String,
    private val tableName: String
) {

    /**
     * Writes watch context data to DynamoDB
     * @return true if successful, false otherwise
     */
    fun writeContextData(context: WatchContext): Boolean {
        return try {
            // Configure credentials
            val credentials = AwsBasicCredentials.create(accessKeyId, secretKey)
            val credentialsProvider = StaticCredentialsProvider.create(credentials)

            // Initialize DynamoDB client with URL connection HTTP client (Android compatible)
            DynamoDbClient.builder()
                .region(Region.of(regionName))
                .credentialsProvider(credentialsProvider)
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build().use { ddb ->

                    // Prepare item attributes
                    val item = mapOf(
                        "UserID" to AttributeValue.builder().s(WatchContext.USER_ID).build(),
                        "HeartRate" to AttributeValue.builder().n(context.heartRate.toString()).build(),
                        "Movement" to AttributeValue.builder().s(context.movement).build(),
                        "Timestamp" to AttributeValue.builder().n(context.timestamp.toString()).build()
                    )

                    // Create PutItem request
                    val request = PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .build()

                    // Execute request
                    ddb.putItem(request)
                    println("Successfully wrote to DynamoDB: $context")
                    true
                }
        } catch (e: Exception) {
            System.err.println("Error writing to DynamoDB: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
