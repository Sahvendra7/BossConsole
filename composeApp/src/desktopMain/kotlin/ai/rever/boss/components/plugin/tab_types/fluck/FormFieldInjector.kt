package ai.rever.boss.components.plugin.tab_types.fluck

import com.teamdev.jxbrowser.browser.Browser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Injects secret values into browser form fields for auto-fill.
 *
 * This injector uses JavaScript execution to:
 * - Fill specific fields by reference
 * - Find and fill username/password fields intelligently
 * - Trigger input events for framework compatibility (React, Vue, Angular)
 * - Handle different field types (input, textarea)
 *
 * Used by Issue #56 - Secret Access Integration with Fluck Browser
 */
object FormFieldInjector {

    /**
     * Fill modes for credential injection
     */
    enum class FillMode {
        USERNAME_ONLY,      // Fill only username/email field
        PASSWORD_ONLY,      // Fill only password field
        BOTH,              // Fill both username and password (recommended)
        COPY_USERNAME,     // Copy username to clipboard
        COPY_PASSWORD      // Copy password to clipboard
    }

    /**
     * Result of fill operation
     */
    sealed class FillResult {
        data class Success(val message: String) : FillResult()
        data class PartialSuccess(val message: String) : FillResult()
        data class Error(val message: String) : FillResult()
    }

    /**
     * Fill a specific form field with a value.
     *
     * @param browser JxBrowser instance
     * @param fieldInfo Information about the field to fill
     * @param value Value to inject into the field
     * @return FillResult indicating success or failure
     */
    suspend fun fillField(
        browser: Browser,
        fieldInfo: FormFieldDetector.FormFieldInfo,
        value: String
    ): FillResult {
        return try {
            val result = CompletableDeferred<FillResult>()

            browser.mainFrame().ifPresent { frame ->
                try {
                    // Escape special characters in value for JavaScript
                    val escapedValue = value.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")

                    val script = """
                        (function() {
                            const field = window.__BOSS_FOCUSED_FIELD || document.activeElement;

                            if (!field || (field.tagName !== 'INPUT' && field.tagName !== 'TEXTAREA')) {
                                return 'ERROR: No field focused';
                            }

                            // Set the value
                            field.value = '$escapedValue';

                            // Trigger events for framework compatibility
                            field.dispatchEvent(new Event('input', { bubbles: true }));
                            field.dispatchEvent(new Event('change', { bubbles: true }));

                            // For React compatibility
                            const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
                                window.HTMLInputElement.prototype, 'value'
                            ).set;
                            nativeInputValueSetter.call(field, '$escapedValue');

                            const event = new Event('input', { bubbles: true });
                            field.dispatchEvent(event);

                            return 'SUCCESS';
                        })();
                    """.trimIndent()

                    val outcome = frame.executeJavaScript<String>(script)

                    result.complete(
                        if (outcome?.contains("SUCCESS", ignoreCase = false) == true) {
                            FillResult.Success("Field filled successfully")
                        } else {
                            FillResult.Error("Failed to fill field: $outcome")
                        }
                    )
                } catch (e: Exception) {
                    result.complete(FillResult.Error("Exception: ${e.message}"))
                }
            }

            withTimeout(2.seconds) {
                result.await()
            }
        } catch (e: Exception) {
            println("❌ [FormFieldInjector] Failed to fill field: ${e.message}")
            FillResult.Error("Timeout or exception: ${e.message}")
        }
    }

    /**
     * Fill credentials (username and password) intelligently.
     *
     * Automatically finds username and password fields on the page
     * and fills them with provided values.
     *
     * @param browser JxBrowser instance
     * @param username Username/email to fill
     * @param password Password to fill
     * @param mode Fill mode (both, username only, or password only)
     * @return FillResult indicating success or failure
     */
    suspend fun fillCredentials(
        browser: Browser,
        username: String,
        password: String,
        mode: FillMode = FillMode.BOTH
    ): FillResult {
        return when (mode) {
            FillMode.USERNAME_ONLY -> findAndFillUsername(browser, username)
            FillMode.PASSWORD_ONLY -> findAndFillPassword(browser, password)
            FillMode.BOTH -> {
                val usernameResult = findAndFillUsername(browser, username)
                val passwordResult = findAndFillPassword(browser, password)

                when {
                    usernameResult is FillResult.Success && passwordResult is FillResult.Success ->
                        FillResult.Success("✅ Username and password filled successfully")

                    usernameResult is FillResult.Success || passwordResult is FillResult.Success ->
                        FillResult.PartialSuccess("⚠️ Only one field filled successfully")

                    else -> FillResult.Error("❌ Failed to fill both fields")
                }
            }
            FillMode.COPY_USERNAME -> {
                copyToClipboard(username)
                FillResult.Success("Username copied to clipboard")
            }
            FillMode.COPY_PASSWORD -> {
                copyToClipboard(password)
                FillResult.Success("Password copied to clipboard")
            }
        }
    }

    /**
     * Find and fill username/email field.
     *
     * Uses multiple strategies to locate the username field:
     * 1. Look for focused field if it's username-like
     * 2. Find by autocomplete="username" or autocomplete="email"
     * 3. Find by input type="email"
     * 4. Find by field name/id containing "user", "login", "email"
     * 5. Find first text input in form
     *
     * @param browser JxBrowser instance
     * @param username Username to fill
     * @return FillResult indicating success or failure
     */
    suspend fun findAndFillUsername(browser: Browser, username: String): FillResult {
        return try {
            val result = CompletableDeferred<FillResult>()

            browser.mainFrame().ifPresent { frame ->
                try {
                    val escapedUsername = username.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")

                    val script = """
                        (function() {
                            // Strategy 1: Check if focused field is username field
                            const focused = document.activeElement;
                            if (focused && focused.tagName === 'INPUT' &&
                                (focused.type === 'email' || focused.type === 'text')) {
                                const name = (focused.name || focused.id || '').toLowerCase();
                                if (name.includes('user') || name.includes('email') ||
                                    name.includes('login') || name.includes('account')) {
                                    focused.value = '$escapedUsername';
                                    focused.dispatchEvent(new Event('input', { bubbles: true }));
                                    focused.dispatchEvent(new Event('change', { bubbles: true }));
                                    return 'SUCCESS: Filled focused username field';
                                }
                            }

                            // Strategy 2: Find by autocomplete attribute
                            let field = document.querySelector('[autocomplete="username"], [autocomplete="email"]');

                            // Strategy 3: Find by type="email"
                            if (!field) {
                                field = document.querySelector('input[type="email"]');
                            }

                            // Strategy 4: Find by name/id containing keywords
                            if (!field) {
                                const inputs = document.querySelectorAll('input[type="text"], input[type="email"]');
                                for (const input of inputs) {
                                    const name = (input.name || input.id || '').toLowerCase();
                                    if (name.includes('user') || name.includes('email') ||
                                        name.includes('login') || name.includes('account')) {
                                        field = input;
                                        break;
                                    }
                                }
                            }

                            // Strategy 5: Find first text input in form with password field
                            if (!field) {
                                const forms = document.querySelectorAll('form');
                                for (const form of forms) {
                                    const hasPassword = form.querySelector('input[type="password"]');
                                    if (hasPassword) {
                                        field = form.querySelector('input[type="text"], input[type="email"]');
                                        if (field) break;
                                    }
                                }
                            }

                            if (field) {
                                field.value = '$escapedUsername';
                                field.dispatchEvent(new Event('input', { bubbles: true }));
                                field.dispatchEvent(new Event('change', { bubbles: true }));
                                return 'SUCCESS: Username field filled';
                            }

                            return 'ERROR: Username field not found';
                        })();
                    """.trimIndent()

                    val outcome = frame.executeJavaScript<String>(script)

                    result.complete(
                        if (outcome?.contains("SUCCESS", ignoreCase = false) == true) {
                            FillResult.Success("Username filled")
                        } else {
                            FillResult.Error("Username field not found")
                        }
                    )
                } catch (e: Exception) {
                    result.complete(FillResult.Error("Exception: ${e.message}"))
                }
            }

            withTimeout(2.seconds) {
                result.await()
            }
        } catch (e: Exception) {
            FillResult.Error("Failed to fill username: ${e.message}")
        }
    }

    /**
     * Find and fill password field.
     *
     * Uses multiple strategies to locate the password field:
     * 1. Look for focused field if it's password type
     * 2. Find by autocomplete="current-password"
     * 3. Find by input type="password"
     * 4. Find by field name/id containing "pass"
     *
     * @param browser JxBrowser instance
     * @param password Password to fill
     * @return FillResult indicating success or failure
     */
    suspend fun findAndFillPassword(browser: Browser, password: String): FillResult {
        return try {
            val result = CompletableDeferred<FillResult>()

            browser.mainFrame().ifPresent { frame ->
                try {
                    val escapedPassword = password.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")

                    val script = """
                        (function() {
                            // Strategy 1: Check if focused field is password field
                            const focused = document.activeElement;
                            if (focused && focused.tagName === 'INPUT' && focused.type === 'password') {
                                focused.value = '$escapedPassword';
                                focused.dispatchEvent(new Event('input', { bubbles: true }));
                                focused.dispatchEvent(new Event('change', { bubbles: true }));
                                return 'SUCCESS: Filled focused password field';
                            }

                            // Strategy 2: Find by autocomplete attribute
                            let field = document.querySelector('[autocomplete="current-password"]');

                            // Strategy 3: Find by type="password"
                            if (!field) {
                                field = document.querySelector('input[type="password"]');
                            }

                            // Strategy 4: Find by name/id containing "pass"
                            if (!field) {
                                const inputs = document.querySelectorAll('input[type="password"]');
                                for (const input of inputs) {
                                    const name = (input.name || input.id || '').toLowerCase();
                                    if (name.includes('pass') || name.includes('pwd')) {
                                        field = input;
                                        break;
                                    }
                                }
                            }

                            if (field) {
                                field.value = '$escapedPassword';
                                field.dispatchEvent(new Event('input', { bubbles: true }));
                                field.dispatchEvent(new Event('change', { bubbles: true }));
                                return 'SUCCESS: Password field filled';
                            }

                            return 'ERROR: Password field not found';
                        })();
                    """.trimIndent()

                    val outcome = frame.executeJavaScript<String>(script)

                    result.complete(
                        if (outcome?.contains("SUCCESS", ignoreCase = false) == true) {
                            FillResult.Success("Password filled")
                        } else {
                            FillResult.Error("Password field not found")
                        }
                    )
                } catch (e: Exception) {
                    result.complete(FillResult.Error("Exception: ${e.message}"))
                }
            }

            withTimeout(2.seconds) {
                result.await()
            }
        } catch (e: Exception) {
            FillResult.Error("Failed to fill password: ${e.message}")
        }
    }

    /**
     * Copy text to system clipboard.
     *
     * @param text Text to copy
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = java.awt.datatransfer.StringSelection(text)
            clipboard.setContents(stringSelection, null)
            println("✅ [FormFieldInjector] Copied to clipboard")
        } catch (e: Exception) {
            println("❌ [FormFieldInjector] Failed to copy to clipboard: ${e.message}")
        }
    }
}
