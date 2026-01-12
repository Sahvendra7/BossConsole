package ai.rever.boss.utils

/**
 * Path utility functions for cross-platform file path handling.
 *
 * KNOWN ISSUES & LIMITATIONS:
 *
 * 1. CODE DUPLICATION:
 *    This file is duplicated in both composeApp and bosseditor modules because:
 *    - bosseditor is designed to be a standalone module with minimal dependencies
 *    - Creating a shared utility module would add complexity to the build structure
 *    - These utilities are simple enough that duplication is acceptable
 *    Future: Consider extracting to a common utilities module if more shared code emerges.
 *
 * 2. EDGE CASES NOT HANDLED:
 *    - UNC paths (\\server\share\file.txt): May not extract parent correctly
 *    - Root-level files (/file.txt or C:\file.txt): Returns empty or drive letter
 *    - Network paths with mixed separators: Behavior may be unpredictable
 *    - Paths with trailing separators: Not normalized automatically
 *
 * 3. NO UNIT TESTS:
 *    These utilities lack comprehensive test coverage. Manual testing has been done
 *    for common cases, but edge cases may have unexpected behavior.
 *    TODO: Add PathUtilsTest.kt with comprehensive test cases
 *
 * 4. SIMPLE IMPLEMENTATION:
 *    Uses basic string manipulation rather than File/Path APIs for simplicity and
 *    to avoid platform-specific behavior. This makes the code predictable but limited.
 */

/**
 * Extract file or folder name from a path, handling both Unix (/) and Windows (\) separators.
 *
 * Examples:
 * - "/path/to/file.txt" -> "file.txt"
 * - "C:\Users\file.txt" -> "file.txt"
 * - "C:/mixed\path/file.txt" -> "file.txt"
 *
 * Note: Does not handle edge cases like UNC paths (\\server\share) or root files correctly.
 */
fun String.extractFileName(): String = this.substringAfterLast('/').substringAfterLast('\\')

/**
 * Extract parent folder name from a path, handling both Unix (/) and Windows (\) separators.
 *
 * Examples:
 * - "/path/to/file.txt" -> "to"
 * - "C:\Users\Documents\file.txt" -> "Documents"
 * - "C:/mixed\path/file.txt" -> "path"
 *
 * Implementation: Normalizes to forward slashes, then extracts the parent folder name.
 * Returns the parent path itself if no parent folder can be determined.
 *
 * Note: Does not handle edge cases like UNC paths (\\server\share) or root files correctly.
 */
fun String.extractParentName(): String {
    val normalized = this.replace('\\', '/')
    val parentPath = normalized.substringBeforeLast('/')
    return parentPath.substringAfterLast('/').ifEmpty { parentPath }
}
