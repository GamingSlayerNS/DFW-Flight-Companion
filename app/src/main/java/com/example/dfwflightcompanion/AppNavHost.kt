package com.example.dfwflightcompanion

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.dfwflightcompanion.amenities.AmenitiesScreen
import com.example.dfwflightcompanion.amenities.AmenityDetailsScreen
import com.example.dfwflightcompanion.amenities.UserReportScreen
import com.example.dfwflightcompanion.map.MapScreen
import com.example.dfwflightcompanion.map.MapViewModel
import com.example.dfwflightcompanion.navigation.Destination
import com.example.dfwflightcompanion.profile.DisabilityProfileFormScreen
import com.example.dfwflightcompanion.profile.DisabilityProfileViewModel
import com.example.dfwflightcompanion.profile.ProfileScreen

object Routes {
    const val AMENITY_DETAILS = "amenity_details/{amenityId}"
    fun amenityDetails(amenityId: String) = "amenity_details/$amenityId"
    const val DISABILITY_PROFILE_FORM = "disability_profile_form"
    const val USER_REPORT_SCREEN = "user_report_screen/{amenityId}/{amenityName}"
    fun userReport(amenityId: String?, amenityName: String?) =
        "user_report_screen/${Uri.encode(amenityId ?: "")}/${Uri.encode(amenityName ?: "")}"
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
        composable(
            route = Routes.USER_REPORT_SCREEN,
            arguments = listOf(
                navArgument("amenityId") { type = NavType.StringType },
                navArgument("amenityName") { type = NavType.StringType }
            )
        ){ backStackEntry ->
            val amenityId = backStackEntry.arguments?.getString("amenityId") ?: return@composable
            val amenityName = backStackEntry.arguments?.getString("amenityName") ?: return@composable

            UserReportScreen(
                navController = navController,
                selectedAmenityId = amenityId,
                selectedAmenityName = amenityName
            )
        }
    }
}