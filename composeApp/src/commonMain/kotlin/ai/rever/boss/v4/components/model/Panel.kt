package ai.rever.boss.v4.components.model

import kotlinx.serialization.Serializable

@Serializable
open class Panel() {
    @Serializable
    data class TOP(val panel: Panel? = null): Panel()

    @Serializable
    data class LEFT(val panel: Panel? = null): Panel()

    @Serializable
    data class RIGHT(val panel: Panel? = null): Panel()

    @Serializable
    data class BOTTOM(val panel: Panel? = null): Panel()

    companion object {
        val top = TOP()
        val left = LEFT()
        val right = RIGHT()
        val bottom = BOTTOM()

        val TOP.top: TOP
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.top)
                is LEFT -> this.copy(panel = this.panel.top)
                is RIGHT -> this.copy(panel = this.panel.top)
                is BOTTOM -> this.copy(panel = this.panel.top)
                else -> this.copy(panel = Panel.top)
            }

        val TOP.left: TOP
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.left)
                is LEFT -> this.copy(panel = this.panel.left)
                is RIGHT -> this.copy(panel = this.panel.left)
                is BOTTOM -> this.copy(panel = this.panel.left)
                else -> this.copy(panel = Panel.left)
            }

        val TOP.right: TOP
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.right)
                is LEFT -> this.copy(panel = this.panel.right)
                is RIGHT -> this.copy(panel = this.panel.right)
                is BOTTOM -> this.copy(panel = this.panel.right)
                else -> this.copy(panel = Panel.right)
            }

        val TOP.bottom: TOP
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.bottom)
                is LEFT -> this.copy(panel = this.panel.bottom)
                is RIGHT -> this.copy(panel = this.panel.bottom)
                is BOTTOM -> this.copy(panel = this.panel.bottom)
                else -> this.copy(panel = Panel.bottom)
            }

        val LEFT.top: LEFT
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.top)
                is LEFT -> this.copy(panel = this.panel.top)
                is RIGHT -> this.copy(panel = this.panel.top)
                is BOTTOM -> this.copy(panel = this.panel.top)
                else -> this.copy(panel = Panel.top)
            }

        val LEFT.left: LEFT
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.left)
                is LEFT -> this.copy(panel = this.panel.left)
                is RIGHT -> this.copy(panel = this.panel.left)
                is BOTTOM -> this.copy(panel = this.panel.left)
                else -> this.copy(panel = Panel.left)
            }

        val LEFT.right: LEFT
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.right)
                is LEFT -> this.copy(panel = this.panel.right)
                is RIGHT -> this.copy(panel = this.panel.right)
                is BOTTOM -> this.copy(panel = this.panel.right)
                else -> this.copy(panel = Panel.right)
            }

        val LEFT.bottom: LEFT
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.bottom)
                is LEFT -> this.copy(panel = this.panel.bottom)
                is RIGHT -> this.copy(panel = this.panel.bottom)
                is BOTTOM -> this.copy(panel = this.panel.bottom)
                else -> this.copy(panel = Panel.bottom)
            }

        val RIGHT.top: RIGHT
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.top)
                is LEFT -> this.copy(panel = this.panel.top)
                is RIGHT -> this.copy(panel = this.panel.top)
                is BOTTOM -> this.copy(panel = this.panel.top)
                else -> this.copy(panel = Panel.top)
            }

        val RIGHT.left: RIGHT
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.left)
                is LEFT -> this.copy(panel = this.panel.left)
                is RIGHT -> this.copy(panel = this.panel.left)
                is BOTTOM -> this.copy(panel = this.panel.left)
                else -> this.copy(panel = Panel.left)
            }

        val RIGHT.right: RIGHT
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.right)
                is LEFT -> this.copy(panel = this.panel.right)
                is RIGHT -> this.copy(panel = this.panel.right)
                is BOTTOM -> this.copy(panel = this.panel.right)
                else -> this.copy(panel = Panel.right)
            }

        val RIGHT.bottom: RIGHT
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.bottom)
                is LEFT -> this.copy(panel = this.panel.bottom)
                is RIGHT -> this.copy(panel = this.panel.bottom)
                is BOTTOM -> this.copy(panel = this.panel.bottom)
                else -> this.copy(panel = Panel.bottom)
            }

        val BOTTOM.top: BOTTOM
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.top)
                is LEFT -> this.copy(panel = this.panel.top)
                is RIGHT -> this.copy(panel = this.panel.top)
                is BOTTOM -> this.copy(panel = this.panel.top)
                else -> this.copy(panel = Panel.top)
            }

        val BOTTOM.left: BOTTOM
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.left)
                is LEFT -> this.copy(panel = this.panel.left)
                is RIGHT -> this.copy(panel = this.panel.left)
                is BOTTOM -> this.copy(panel = this.panel.left)
                else -> this.copy(panel = Panel.left)
            }

        val BOTTOM.right: BOTTOM
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.right)
                is LEFT -> this.copy(panel = this.panel.right)
                is RIGHT -> this.copy(panel = this.panel.right)
                is BOTTOM -> this.copy(panel = this.panel.right)
                else -> this.copy(panel = Panel.right)
            }

        val BOTTOM.bottom: BOTTOM
            get() = when (this.panel) {
                is TOP -> this.copy(panel = this.panel.bottom)
                is LEFT -> this.copy(panel = this.panel.bottom)
                is RIGHT -> this.copy(panel = this.panel.bottom)
                is BOTTOM -> this.copy(panel = this.panel.bottom)
                else -> this.copy(panel = Panel.bottom)
            }
    }
}

