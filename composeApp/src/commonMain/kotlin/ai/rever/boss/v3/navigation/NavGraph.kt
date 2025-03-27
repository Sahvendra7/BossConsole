package ai.rever.boss.v3.navigation

sealed class Screen(val route: String) {
    // Lighthouse screens
    object Worklist : Screen("worklist")
    object SystemOfRecords : Screen("system_of_records")
    object OrgValues : Screen("org_values")
    
    // Lanager screens
    object GlobalLanager : Screen("global_lanager")
    object MasteryRegistry : Screen("mastery_registry")
    object TaskResolverRegistry : Screen("task_resolver_registry")

    companion object {
        fun fromRoute(route: String?): Screen {
            return when (route?.substringBefore("/")) {
                Worklist.route -> Worklist
                SystemOfRecords.route -> SystemOfRecords
                OrgValues.route -> OrgValues
                GlobalLanager.route -> GlobalLanager
                MasteryRegistry.route -> MasteryRegistry
                TaskResolverRegistry.route -> TaskResolverRegistry
                null -> Worklist
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }
        }
    }
} 