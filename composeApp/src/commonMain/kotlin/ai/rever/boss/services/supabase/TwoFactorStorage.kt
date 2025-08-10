package ai.rever.boss.services.supabase

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Simple storage for 2FA enrollment status
 * In production, this would be stored in Supabase database
 */
object TwoFactorStorage {
    private val storageFile = File(System.getProperty("user.home"), ".boss/2fa_enrollments.json")
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    @Serializable
    data class UserEnrollment(
        val userId: String,
        val factors: List<TwoFactorInfo>
    )
    
    @Serializable
    data class EnrollmentData(
        val enrollments: MutableList<UserEnrollment> = mutableListOf()
    )
    
    init {
        // Ensure directory exists
        storageFile.parentFile?.mkdirs()
    }
    
    /**
     * Load enrollments from storage
     */
    fun loadEnrollments(): Map<String, List<TwoFactorInfo>> {
        return try {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                val data = json.decodeFromString<EnrollmentData>(content)
                data.enrollments.associate { it.userId to it.factors }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            println("Error loading 2FA enrollments: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Save enrollments to storage
     */
    fun saveEnrollments(enrollments: Map<String, List<TwoFactorInfo>>) {
        try {
            val data = EnrollmentData(
                enrollments = enrollments.map { (userId, factors) ->
                    UserEnrollment(userId, factors)
                }.toMutableList()
            )
            val content = json.encodeToString(data)
            storageFile.writeText(content)
            println("Saved 2FA enrollments for ${enrollments.size} users")
        } catch (e: Exception) {
            println("Error saving 2FA enrollments: ${e.message}")
        }
    }
    
    /**
     * Get factors for a specific user
     */
    fun getUserFactors(userId: String): List<TwoFactorInfo> {
        val enrollments = loadEnrollments()
        return enrollments[userId] ?: emptyList()
    }
    
    /**
     * Save factors for a specific user
     */
    fun saveUserFactors(userId: String, factors: List<TwoFactorInfo>) {
        val enrollments = loadEnrollments().toMutableMap()
        enrollments[userId] = factors
        saveEnrollments(enrollments)
    }
    
    /**
     * Add a factor for a user
     */
    fun addUserFactor(userId: String, factor: TwoFactorInfo) {
        val currentFactors = getUserFactors(userId).toMutableList()
        currentFactors.add(factor)
        saveUserFactors(userId, currentFactors)
    }
    
    /**
     * Remove a factor for a user
     */
    fun removeUserFactor(userId: String, factorId: String) {
        val currentFactors = getUserFactors(userId).toMutableList()
        currentFactors.removeAll { it.id == factorId }
        saveUserFactors(userId, currentFactors)
    }
}