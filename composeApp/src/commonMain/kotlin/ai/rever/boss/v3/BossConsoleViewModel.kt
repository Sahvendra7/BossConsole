package ai.rever.boss.v3

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel

sealed class Screen {
    // Lighthouse screens
    object Worklist : Screen()
    object SystemOfRecords : Screen()
    object OrgValues : Screen()
    
    // Lanager screens
    object GlobalLanager : Screen()
    object MasteryRegistry : Screen()
    object TaskResolverRegistry : Screen()
}

enum class Section {
    LIGHTHOUSE,
    LANAGER
}

data class NavigationItem(
    val title: String,
    val screen: Screen,
    val icon: ImageVector,
    val section: Section
)

class BossConsoleViewModel : ViewModel() {
    var currentScreen by mutableStateOf<Screen>(Screen.Worklist)
        private set
        
    var expandedSections by mutableStateOf(setOf(Section.LIGHTHOUSE))
        private set

    val navigationItems = listOf(
        // Lighthouse section
        NavigationItem("Worklist", Screen.Worklist, Icons.Default.WorkHistory, Section.LIGHTHOUSE),
        NavigationItem("System of Records", Screen.SystemOfRecords, Icons.Default.DatasetLinked, Section.LIGHTHOUSE),
        NavigationItem("Org Values", Screen.OrgValues, Icons.Default.GraphicEq, Section.LIGHTHOUSE),
        
        // Lanager section
        NavigationItem("Global Lanager", Screen.GlobalLanager, Icons.Default.RocketLaunch, Section.LANAGER),
        NavigationItem("Mastery Registry", Screen.MasteryRegistry, Icons.Default.MilitaryTech, Section.LANAGER),
        NavigationItem("TaskResolver Registry", Screen.TaskResolverRegistry, Icons.Default.AppRegistration, Section.LANAGER)
    )
    
    fun navigateTo(screen: Screen) {
        currentScreen = screen
        // Ensure the section for this screen is expanded
        val section = navigationItems.first { it.screen == screen }.section
        if (!expandedSections.contains(section)) {
            expandedSections = expandedSections + section
        }
    }
    
    fun toggleSection(section: Section) {
        expandedSections = if (expandedSections.contains(section)) {
            expandedSections - section
        } else {
            expandedSections + section
        }
    }
    
    fun getItemsBySection(section: Section): List<NavigationItem> {
        return navigationItems.filter { it.section == section }
    }
} 