package ai.rever.boss.downloads

import ai.rever.boss.plugin.api.TransferInfo
import ai.rever.boss.plugin.api.TransferKind
import ai.rever.boss.plugin.api.TransferPhase

/**
 * The wording for the download center's two surfaces, kept pure so tests pin it.
 *
 * Same reasoning as `engineDownloadStatus`: these strings are the only thing that
 * tells the user which of several transfers is which, and a phase whose verb is
 * wrong ("Installing" while an update is still downloading) is how a bar that
 * looks stuck gets reported as a hang.
 */

/** The verb for one transfer, given where it has got to. */
internal fun transferVerb(info: TransferInfo): String =
    when (info.phase) {
        TransferPhase.PREPARING, TransferPhase.DOWNLOADING -> {
            "Downloading"
        }

        TransferPhase.INSTALLING -> {
            if (info.kind == TransferKind.PLUGIN_UPDATE) "Updating" else "Installing"
        }

        TransferPhase.READY_TO_INSTALL -> {
            "Ready to install"
        }
    }

/**
 * The status line under a row in the dialog, e.g. "Downloading 72%".
 *
 * A determinate percentage is shown only while downloading: the fraction is a
 * download fraction, and repeating the last one next to "Installing" would read
 * as an install that is 72% done and stuck there.
 */
internal fun transferStatusLine(info: TransferInfo): String {
    val verb = transferVerb(info)
    val percent = info.progress?.takeIf { info.phase == TransferPhase.DOWNLOADING }
    return when {
        info.phase == TransferPhase.READY_TO_INSTALL -> verb
        percent != null -> "$verb ${(percent * 100).toInt()}%"
        else -> "$verb…"
    }
}

/**
 * The bottom-bar label for everything in flight.
 *
 * Deliberately not "N plugins": the application's own update shares this bar
 * with plugin transfers, so a count of plugins would be a lie exactly when two
 * different kinds are running.
 */
internal fun transferBarLabel(items: List<TransferInfo>): String =
    when (items.size) {
        0 -> {
            ""
        }

        1 -> {
            val info = items.first()
            val line = transferStatusLine(info)
            // "Downloading 72% Toolbox" reads backwards, so the name goes between
            // the verb and the number: "Downloading Toolbox 72%".
            val verb = transferVerb(info)
            line.replaceFirst(verb, "$verb ${info.title}")
        }

        else -> {
            "${items.size} downloads…"
        }
    }

/**
 * The one fraction the bar can draw, or null when it must stay indeterminate.
 *
 * Determinate only when EVERY transfer knows its size: averaging a known 90%
 * with an unknown would draw a bar that jumps backwards the moment the unknown
 * one starts reporting.
 */
internal fun overallProgress(items: List<TransferInfo>): Float? {
    // A downloaded update is finished as far as this bar is concerned. Left as
    // null it would animate an indeterminate bar next to "Ready to install",
    // which reads as work still happening.
    val fractions =
        items.map { it.progress ?: if (it.phase == TransferPhase.READY_TO_INSTALL) 1f else null }
    return if (fractions.isEmpty() || fractions.any { it == null }) {
        null
    } else {
        fractions.filterNotNull().average().toFloat()
    }
}
