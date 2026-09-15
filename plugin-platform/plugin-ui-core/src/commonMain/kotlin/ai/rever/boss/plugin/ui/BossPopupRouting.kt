package ai.rever.boss.plugin.ui

/**
 * Resolves whether a popup should use the heavyweight native window renderer.
 *
 * This explicitly separates the routing decision from the Compose UI layer.
 *
 * @param forceHeavyweight True if forced by LocalForceHeavyweightPopups.
 * @param useHeavyweightOverlays Global configuration for heavyweight overlays.
 * @param hasRenderer True if the host has registered a heavyweight popup renderer.
 * @param hostNeedsHeavyweight True if the host window requires heavyweight overlays.
 */
internal fun resolvePopupHeavyweightRouting(
    forceHeavyweight: Boolean,
    useHeavyweightOverlays: Boolean,
    hasRenderer: Boolean,
    hostNeedsHeavyweight: Boolean,
): Boolean =
    shouldRouteHeavyweight(
        useHeavyweightOverlays = forceHeavyweight || useHeavyweightOverlays,
        hasRenderer = hasRenderer,
        hostNeedsHeavyweight = hostNeedsHeavyweight,
    )
