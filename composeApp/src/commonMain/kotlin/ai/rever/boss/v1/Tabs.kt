package ai.rever.boss.v1

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun Tabs() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Tab 1", "Tab 2", "Tab 3")

    Scaffold(
        bottomBar = {
            TabRow(
                selectedTabIndex = selectedTab
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    ) {
                        Text(text = title)
                    }
                }
            }
        }
    ) {
        when (selectedTab) {
            0 -> TabContent1()
            1 -> TabContent2()
            2 -> TabContent3()
        }
    }
}

@Composable
fun TabContent1() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Content for Tab 1")
    }
}

@Composable
fun TabContent2() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Content for Tab 2")
    }
}

@Composable
fun TabContent3() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Content for Tab 3")
    }
}