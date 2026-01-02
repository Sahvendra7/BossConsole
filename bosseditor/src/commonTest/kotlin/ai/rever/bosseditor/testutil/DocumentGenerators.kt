package ai.rever.bosseditor.testutil

/**
 * Shared test utilities for generating large documents.
 *
 * These generators create realistic code documents for performance testing,
 * stress testing, and integration testing across the BossEditor test suite.
 */
object DocumentGenerators {

    /**
     * Creates a large Java document with realistic content including:
     * - Package and import statements
     * - Fields, methods, and comments
     * - Block comments at regular intervals
     *
     * @param lineCount Target number of lines (actual count may vary slightly)
     * @return A string containing valid Java code
     */
    fun createLargeJavaDocument(lineCount: Int): String {
        return buildString {
            appendLine("package com.example.large;")
            appendLine()
            appendLine("import java.util.*;")
            appendLine("import java.io.*;")
            appendLine()
            appendLine("public class LargeClass {")

            var currentLine = 6
            while (currentLine < lineCount - 2) {
                val methodNum = currentLine / 10

                // Add fields
                if (currentLine % 100 < 10) {
                    appendLine("    private int field${methodNum} = ${methodNum};")
                    currentLine++
                }
                // Add methods
                else if (currentLine % 50 < 20) {
                    appendLine("    public void method${methodNum}() {")
                    appendLine("        int x = ${methodNum};")
                    appendLine("        String s = \"value${methodNum}\";")
                    appendLine("        System.out.println(s + x);")
                    appendLine("    }")
                    appendLine()
                    currentLine += 6
                }
                // Add comments
                else if (currentLine % 30 < 5) {
                    appendLine("    // Comment line ${methodNum}")
                    currentLine++
                }
                // Add block comments
                else if (currentLine % 100 == 25) {
                    appendLine("    /*")
                    appendLine("     * Block comment ${methodNum}")
                    appendLine("     * More text here")
                    appendLine("     */")
                    currentLine += 4
                }
                // Regular code
                else {
                    appendLine("    int local${currentLine} = ${currentLine};")
                    currentLine++
                }
            }

            appendLine("}")
        }
    }

    /**
     * Creates a large Java document with significant bracket nesting.
     * Useful for testing bracket matching, rainbow brackets, and indent guides.
     *
     * @param lineCount Target number of lines (actual count may vary slightly)
     * @return A string containing valid Java code with deep nesting
     */
    fun createLargeJavaDocumentWithNesting(lineCount: Int): String {
        return buildString {
            appendLine("package com.example.nested;")
            appendLine()
            appendLine("public class NestedClass {")

            var currentLine = 3
            var depth = 1

            while (currentLine < lineCount - depth - 1) {
                val indent = "    ".repeat(depth)

                when {
                    // Start nested block
                    currentLine % 50 == 0 && depth < 5 -> {
                        appendLine("${indent}public void method${currentLine}() {")
                        depth++
                        currentLine++
                    }
                    // Add if statement
                    currentLine % 20 == 0 && depth < 6 -> {
                        appendLine("${indent}if (condition${currentLine}) {")
                        depth++
                        currentLine++
                    }
                    // Add for loop
                    currentLine % 15 == 0 && depth < 6 -> {
                        appendLine("${indent}for (int i = 0; i < ${currentLine}; i++) {")
                        depth++
                        currentLine++
                    }
                    // Close block
                    currentLine % 10 == 0 && depth > 2 -> {
                        depth--
                        val closeIndent = "    ".repeat(depth)
                        appendLine("${closeIndent}}")
                        currentLine++
                    }
                    // Regular code with brackets
                    else -> {
                        appendLine("${indent}int x${currentLine} = (${currentLine} + (${currentLine} * 2));")
                        currentLine++
                    }
                }
            }

            // Close all open blocks
            while (depth > 0) {
                depth--
                val indent = "    ".repeat(depth)
                appendLine("${indent}}")
            }
        }
    }

    /**
     * Creates a Kotlin document with multiline strings and various constructs.
     *
     * @param lineCount Target number of lines
     * @return A string containing valid Kotlin code
     */
    fun createKotlinDocument(lineCount: Int): String {
        return buildString {
            appendLine("package com.example.kotlin")
            appendLine()
            appendLine("class KotlinClass {")

            var currentLine = 3
            while (currentLine < lineCount - 2) {
                val num = currentLine / 5

                when {
                    // Add raw string
                    currentLine % 50 == 0 -> {
                        appendLine("    val text$num = \"\"\"")
                        appendLine("        Line 1 of raw string")
                        appendLine("        Line 2 of raw string")
                        appendLine("    \"\"\".trimIndent()")
                        currentLine += 4
                    }
                    // Add function
                    currentLine % 20 == 0 -> {
                        appendLine("    fun method$num(): Int {")
                        appendLine("        return $num")
                        appendLine("    }")
                        appendLine()
                        currentLine += 4
                    }
                    // Add property
                    else -> {
                        appendLine("    val field$currentLine = $currentLine")
                        currentLine++
                    }
                }
            }

            appendLine("}")
        }
    }

    /**
     * Creates a simple Java class with a specified number of fields.
     * Useful for simpler tests that don't need complex structure.
     *
     * @param fieldCount Number of fields to generate
     * @return A string containing a simple Java class
     */
    fun createSimpleJavaClass(fieldCount: Int): String {
        return buildString {
            appendLine("class SimpleClass {")
            for (i in 1..fieldCount) {
                appendLine("    int field$i = $i;")
            }
            appendLine("}")
        }
    }

    /**
     * Creates a Java document with an unclosed block comment starting at a specific line.
     * Useful for testing multi-line state handling.
     *
     * @param linesBeforeComment Lines of code before the comment
     * @param linesInComment Lines inside the unclosed comment
     * @return A string with an unclosed block comment
     */
    fun createJavaWithUnclosedBlockComment(linesBeforeComment: Int, linesInComment: Int): String {
        return buildString {
            appendLine("class Test {")
            for (i in 1..linesBeforeComment) {
                appendLine("    int x$i = $i;")
            }
            appendLine("    /*")
            for (i in 1..linesInComment) {
                appendLine("     * Comment line $i")
            }
            appendLine("}")
        }
    }
}
