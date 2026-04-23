package com.example.dfwflightcompanion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

object Routes {
    const val AMENITY_DETAILS = "amenity_details/{amenityId}"
    fun amenityDetails(amenityId: String) = "amenity_details/$amenityId"
    const val DISABILITY_PROFILE_FORM = "disability_profile_form"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Destination,
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel,
    disabilityProfileViewModel: DisabilityProfileViewModel
) {
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
            ProfileScreen(
                navController = navController,
                disabilityProfileViewModel = disabilityProfileViewModel
            )
        }
        composable(
            route = Routes.AMENITY_DETAILS,
            arguments = listOf(navArgument("amenityId") { type = NavType.StringType })
        ) { backStackEntry ->
            val amenityId = backStackEntry.arguments?.getString("amenityId") ?: return@composable
            AmenityDetailsScreen(
                amenityId = amenityId,
                navController = navController,
                mapViewModel = mapViewModel
            )
        }
        composable(Routes.DISABILITY_PROFILE_FORM) {
            DisabilityProfileFormScreen(
                navController = navController,
                disabilityProfileViewModel = disabilityProfileViewModel
            )
        }
    }
}