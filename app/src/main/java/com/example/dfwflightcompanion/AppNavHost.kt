package com.example.dfwflightcompanion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Destination,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        composable(Destination.MAP.route) {
            MapScreen()
        }
        composable(Destination.AMENITIES.route) {
            AmenitiesScreen()
        }
        composable(Destination.PROFILE.route) {
            ProfileScreen()
        }
    }
}