package ai.rever.bosseditor.print

/**
 * Settings for printing editor content.
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 *
 * @property showLineNumbers Whether to print line numbers
 * @property syntaxHighlighting Whether to use syntax highlighting colors
 * @property fontSize Font size in points for printed text
 * @property pageSize The paper size to use
 * @property orientation Page orientation
 * @property margins Page margins in points
 * @property headerText Optional header text (supports %f for filename, %p for page, %d for date)
 * @property footerText Optional footer text
 * @property printBackground Whether to print background colors
 */
data class PrintSettings(
    val showLineNumbers: Boolean = true,
    val syntaxHighlighting: Boolean = true,
    val fontSize: Float = 10f,
    val pageSize: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val margins: PageMargins = PageMargins.DEFAULT,
    val headerText: String? = "%f",
    val footerText: String? = "Page %p",
    val printBackground: Boolean = false
) {
    companion object {
        /**
         * Default print settings.
         */
        val DEFAULT = PrintSettings()

        /**
         * Settings optimized for code review (larger font, no colors).
         */
        val CODE_REVIEW = PrintSettings(
            fontSize = 12f,
            syntaxHighlighting = false,
            showLineNumbers = true
        )

        /**
         * Settings optimized for presentation (no line numbers, syntax colors).
         */
        val PRESENTATION = PrintSettings(
            showLineNumbers = false,
            syntaxHighlighting = true,
            fontSize = 14f
        )
    }
}

/**
 * Standard paper sizes.
 */
enum class PageSize(
    val widthPoints: Float,
    val heightPoints: Float,
    val displayName: String
) {
    /** A4 paper (210mm x 297mm) */
    A4(595f, 842f, "A4"),

    /** US Letter (8.5in x 11in) */
    LETTER(612f, 792f, "Letter"),

    /** US Legal (8.5in x 14in) */
    LEGAL(612f, 1008f, "Legal"),

    /** A3 paper (297mm x 420mm) */
    A3(842f, 1191f, "A3"),

    /** A5 paper (148mm x 210mm) */
    A5(420f, 595f, "A5");

    /** Width in inches */
    val widthInches: Float get() = widthPoints / 72f

    /** Height in inches */
    val heightInches: Float get() = heightPoints / 72f
}

/**
 * Page orientation.
 */
enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Page margins in points (1/72 inch).
 */
data class PageMargins(
    val top: Float,
    val right: Float,
    val bottom: Float,
    val left: Float
) {
    companion object {
        /** Default margins (0.75 inch all around) */
        val DEFAULT = PageMargins(54f, 54f, 54f, 54f)

        /** Narrow margins (0.5 inch) */
        val NARROW = PageMargins(36f, 36f, 36f, 36f)

        /** Wide margins (1 inch) */
        val WIDE = PageMargins(72f, 72f, 72f, 72f)

        /** No margins */
        val NONE = PageMargins(0f, 0f, 0f, 0f)
    }

    /** Total horizontal margins */
    val horizontalTotal: Float get() = left + right

    /** Total vertical margins */
    val verticalTotal: Float get() = top + bottom
}

/**
 * Print job status.
 */
enum class PrintStatus {
    /** Print job not started */
    IDLE,

    /** Preparing print data */
    PREPARING,

    /** Sending to printer */
    PRINTING,

    /** Print completed successfully */
    COMPLETED,

    /** Print was cancelled */
    CANCELLED,

    /** Print failed with error */
    FAILED
}

/**
 * Result of a print operation.
 */
sealed class PrintResult {
    /** Print completed successfully */
    data class Success(val pagesPrinted: Int) : PrintResult()

    /** User cancelled the print dialog */
    object Cancelled : PrintResult()

    /** Print failed with an error */
    data class Error(val message: String, val cause: Throwable? = null) : PrintResult()
}
