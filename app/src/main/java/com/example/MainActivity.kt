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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
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
import com.example.ui.screens.CapturesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RunScreen
import com.example.ui.screens.SafetyScreen
import com.example.ui.screens.ScriptsScreen
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.StoryTestTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class AppTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    SCRIPTS("Scripts", Icons.Filled.Layers, Icons.Outlined.Layers),
    RUN("Run", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow),
    CAPTURES("Captures", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt),
    SAFETY("Safety", Icons.Filled.Security, Icons.Outlined.Security)
}

class MainActivity : ComponentActivity() {

    private val viewModel: StoryTestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StoryTestTheme {
                StoryTestAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkServiceStatus()
    }
}

@Composable
fun StoryTestAppRoot(viewModel: StoryTestViewModel) {
    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var preselectedScriptId by remember { mutableStateOf<Long?>(null) }

    // If not on Home, back button returns to Home
    if (currentTab != AppTab.HOME) {
        BackHandler {
            currentTab = AppTab.HOME
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
                        onClick = { currentTab = tab },
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
            when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToRun = { currentTab = AppTab.RUN },
                    onNavigateToScripts = { currentTab = AppTab.SCRIPTS },
                    onNavigateToCaptures = { currentTab = AppTab.CAPTURES },
                    onNavigateToSafety = { currentTab = AppTab.SAFETY }
                )
                AppTab.SCRIPTS -> ScriptsScreen(
                    viewModel = viewModel,
                    onRunScript = { script ->
                        preselectedScriptId = script.id
                        currentTab = AppTab.RUN
                    }
                )
                AppTab.RUN -> RunScreen(
                    viewModel = viewModel,
                    preselectedScriptId = preselectedScriptId
                )
                AppTab.CAPTURES -> CapturesScreen(
                    viewModel = viewModel
                )
                AppTab.SAFETY -> SafetyScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
