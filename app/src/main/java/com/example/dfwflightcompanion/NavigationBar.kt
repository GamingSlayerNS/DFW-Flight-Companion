package com.example.dfwflightcompanion

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel


@Composable
fun BottomNavigationBar(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val startDestination = Destination.MAP
    var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination.ordinal) }
    val mapViewModel: MapViewModel = viewModel() // Shared ViewModel between screens
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                Destination.entries.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedDestination == index,
                        onClick = {
                            if (currentRoute == destination.route) {
                                if (destination == Destination.MAP) {
                                    mapViewModel.selectAmenity(null)
                                }
                            }
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                            selectedDestination = index
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.contentDescription
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { contentPadding ->
        AppNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(contentPadding),
            mapViewModel = mapViewModel
        )
    }
}