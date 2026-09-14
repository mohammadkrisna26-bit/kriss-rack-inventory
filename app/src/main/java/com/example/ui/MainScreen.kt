package com.example.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.DataEntryScreen
import com.example.ui.screens.DepartmentsSectionsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImportExportScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.viewmodel.InventoryViewModel
import kotlinx.coroutines.flow.collectLatest

sealed class Screen(val title: String, val icon: ImageVector, val tag: String) {
    object Home : Screen("Beranda", Icons.Default.Home, "nav_home")
    object DataEntry : Screen("Pendataan", Icons.Default.QrCodeScanner, "nav_data_entry")
    object Products : Screen("Katalog", Icons.Default.Inventory2, "nav_products")
    object Sections : Screen("Section", Icons.Default.Layers, "nav_sections")
    object DataTransfer : Screen("Data", Icons.Default.ImportExport, "nav_data")
}

@Composable
fun MainScreen(
    viewModel: InventoryViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Listen to ViewModel user messages
    LaunchedEffect(viewModel) {
        viewModel.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                val screens = listOf(
                    Screen.Home,
                    Screen.DataEntry,
                    Screen.Products,
                    Screen.Sections,
                    Screen.DataTransfer
                )

                screens.forEach { screen ->
                    val isSelected = currentScreen == screen
                    val isDataEntry = screen == Screen.DataEntry

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(if (isDataEntry) 26.dp else 22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (isDataEntry) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            selectedTextColor = if (isDataEntry) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            indicatorColor = if (isDataEntry) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(screen.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            Screen.Home -> HomeScreen(
                viewModel = viewModel,
                onNavigateToDataEntry = { currentScreen = Screen.DataEntry },
                onNavigateToProducts = { currentScreen = Screen.Products },
                onNavigateToSections = { currentScreen = Screen.Sections },
                onNavigateToImportExport = { currentScreen = Screen.DataTransfer },
                modifier = Modifier.padding(innerPadding)
            )
            Screen.DataEntry -> DataEntryScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = Screen.Home },
                modifier = Modifier.padding(innerPadding)
            )
            Screen.Products -> ProductsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            Screen.Sections -> DepartmentsSectionsScreen(
                viewModel = viewModel,
                onActivateSectionForEntry = { sec ->
                    viewModel.selectEntrySection(sec)
                    currentScreen = Screen.DataEntry
                },
                modifier = Modifier.padding(innerPadding)
            )
            Screen.DataTransfer -> ImportExportScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
