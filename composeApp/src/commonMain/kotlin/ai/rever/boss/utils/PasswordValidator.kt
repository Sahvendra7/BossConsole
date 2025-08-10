package ai.rever.boss.utils

/**
 * Password validation utility for medium-level security policy
 */
object PasswordValidator {
    
    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 128
    
    /**
     * Medium-level password policy requirements:
     * - At least 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character
     * - No more than 128 characters
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
    
    fun validatePassword(password: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check minimum length
        if (password.length < MIN_LENGTH) {
            errors.add("Password must be at least $MIN_LENGTH characters long")
        }
        
        // Check maximum length
        if (password.length > MAX_LENGTH) {
            errors.add("Password must not exceed $MAX_LENGTH characters")
        }
        
        // Check for uppercase letter
        if (!password.any { it.isUpperCase() }) {
            errors.add("Password must contain at least one uppercase letter")
        }
        
        // Check for lowercase letter
        if (!password.any { it.isLowerCase() }) {
            errors.add("Password must contain at least one lowercase letter")
        }
        
        // Check for digit
        if (!password.any { it.isDigit() }) {
            errors.add("Password must contain at least one digit")
        }
        
        // Check for special character
        if (!password.any { isSpecialCharacter(it) }) {
            errors.add("Password must contain at least one special character (!@#$%^&*)")
        }
        
        // Check for common weak patterns
        if (hasWeakPatterns(password)) {
            errors.add("Password contains weak patterns (avoid sequential or repeated characters)")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Get password strength score (0-100)
     */
    fun getPasswordStrength(password: String): Int {
        var score = 0
        
        // Length bonus
        score += minOf(password.length * 2, 20)
        
        // Character variety bonus
        if (password.any { it.isUpperCase() }) score += 10
        if (password.any { it.isLowerCase() }) score += 10
        if (password.any { it.isDigit() }) score += 10
        if (password.any { isSpecialCharacter(it) }) score += 15
        
        // Length bonus for longer passwords
        if (password.length >= 12) score += 10
        if (password.length >= 16) score += 10
        
        // Variety bonus for multiple character types
        val types = listOf(
            password.any { it.isUpperCase() },
            password.any { it.isLowerCase() },
            password.any { it.isDigit() },
            password.any { isSpecialCharacter(it) }
        ).count { it }
        
        if (types >= 4) score += 15
        
        // Penalty for weak patterns
        if (hasWeakPatterns(password)) score -= 20
        
        return maxOf(0, minOf(100, score))
    }
    
    /**
     * Get password strength description
     */
    fun getPasswordStrengthDescription(score: Int): String {
        return when {
            score < 30 -> "Weak"
            score < 60 -> "Fair"
            score < 80 -> "Good"
            else -> "Strong"
        }
    }
    
    /**
     * Get color for password strength indicator
     */
    fun getPasswordStrengthColor(score: Int): androidx.compose.ui.graphics.Color {
        return when {
            score < 30 -> androidx.compose.ui.graphics.Color.Red
            score < 60 -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
            score < 80 -> androidx.compose.ui.graphics.Color(0xFFFFC107) // Yellow
            else -> androidx.compose.ui.graphics.Color.Green
        }
    }
    
    private fun isSpecialCharacter(char: Char): Boolean {
        return "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(char)
    }
    
    private fun hasWeakPatterns(password: String): Boolean {
        val lowercasePassword = password.lowercase()
        
        // Check for sequential characters
        for (i in 0 until password.length - 2) {
            val first = password[i].code
            val second = password[i + 1].code
            val third = password[i + 2].code
            
            if ((second == first + 1 && third == second + 1) || 
                (second == first - 1 && third == second - 1)) {
                return true
            }
        }
        
        // Check for repeated characters (3+ in a row)
        for (i in 0 until password.length - 2) {
            if (password[i] == password[i + 1] && password[i + 1] == password[i + 2]) {
                return true
            }
        }
        
        // Check for common weak passwords
        val weakPatterns = listOf(
            "password", "123456", "qwerty", "abc123", 
            "admin", "user", "guest", "test"
        )
        
        return weakPatterns.any { lowercasePassword.contains(it) }
    }
}