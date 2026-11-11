package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.ui.StoryTestViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PopupsScreen
import com.example.ui.screens.SafetyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.StoryTestTheme
import com.example.ui.theme.TextSecondary

enum class AppTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

/** Pages opened from Settings. */
private enum class SubPage { NONE, POPUPS, SAFETY }

class MainActivity : ComponentActivity() {

    private val viewModel: StoryTestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StoryTestTheme {
                StoryBotRoot(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkServiceStatus()
    }
}

@Composable
fun StoryBotRoot(viewModel: StoryTestViewModel) {
    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var subPage by remember { mutableStateOf(SubPage.NONE) }

    // Back closes a sub page first, then returns to Home.
    if (subPage != SubPage.NONE || currentTab != AppTab.HOME) {
        BackHandler {
            if (subPage != SubPage.NONE) subPage = SubPage.NONE else currentTab = AppTab.HOME
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            currentTab = tab
                            subPage = SubPage.NONE
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.ui.graphics.Color.Black,
                            selectedTextColor = NeonGreen,
                            indicatorColor = NeonGreen,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                subPage == SubPage.POPUPS -> PopupsScreen(viewModel = viewModel)
                subPage == SubPage.SAFETY -> SafetyScreen(viewModel = viewModel)
                currentTab == AppTab.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onOpenSettings = { currentTab = AppTab.SETTINGS }
                )
                else -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenPopups = { subPage = SubPage.POPUPS },
                    onOpenSafety = { subPage = SubPage.SAFETY }
                )
            }
        }
    }
}
