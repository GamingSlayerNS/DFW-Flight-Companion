package com.example.dfwflightcompanion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Destination,
    modifier: Modifier = Modifier
) {
    // Shared ViewModel between screens
    val mapViewModel: MapViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(Destination.MAP.route) {
            MapScreen(
                navController = navController,
                mapViewModel = mapViewModel
            )
        }
        composable(Destination.AMENITIES.route) {
            AmenitiesScreen(
                navController = navController,
                mapViewModel = mapViewModel
            )
        }
        composable(Destination.PROFILE.route) {
            ProfileScreen()
        }
    }
}