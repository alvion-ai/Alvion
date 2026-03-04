package com.qualcomm.alvion.feature.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qualcomm.alvion.feature.history.HistoryScreen
import com.qualcomm.alvion.feature.history.HistoryViewModel
import com.qualcomm.alvion.feature.home.HomeTab
import com.qualcomm.alvion.feature.profile.ProfileTab

@Composable
fun AppShell(
    onSettings: () -> Unit = {},
    onSummary: () -> Unit = {},
    onSignOut: () -> Unit = {},
    // Scope the ViewModel to AppShell so it survives tab switches
    historyViewModel: HistoryViewModel = viewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf("Home", "History", "Profile")
    val icons =
        listOf(
            Icons.Default.Home,
            Icons.AutoMirrored.Filled.List,
            Icons.Default.Person,
        )

    val selectedBlue = Color(0xFF2563EB)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(label) },
                        icon = { Icon(icons[index], contentDescription = label) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = selectedBlue,
                                selectedTextColor = selectedBlue,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
            }
        },
    ) { inner ->
        Box(modifier = Modifier.padding(inner).fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeTab(onSettings, onSummary, historyViewModel)
                1 -> HistoryScreen(historyViewModel)
                2 -> ProfileTab(onSignOut)
            }
        }
    }
}
