package com.example.dfwflightcompanion

object RestroomData {
    private val congestionLevels = listOf("Low", "Medium", "High")
    // We have 4 restroom areas defined in floorplan.geojson: nw, sw, ne, se.
    // Each area will have 3 distinct map nodes (M, F, H) placed near each other.

    val restroomSets = listOf(
        // North West (restroom_nw polygon bounds approx: Lat 32.8985-32.8990, Lng -97.0450 to -97.0448)
        createSet("nw", 32.89885, -97.04495, "North West"),

        // South West (restroom_sw polygon bounds approx: Lat 32.8960-32.8965, Lng -97.0450 to -97.0448)
        createSet("sw", 32.89635, -97.04495, "South West"),

        // North East (restroom_ne polygon bounds approx: Lat 32.8960-32.8965, Lng -97.0444 to -97.0442)
        createSet("ne", 32.89635, -97.04425, "North East"),

        // South East (restroom_se polygon bounds approx: Lat 32.8970-32.8975, Lng -97.0444 to -97.0442)
        createSet("se", 32.89735, -97.04425, "South East")
    ).flatten()

    private fun createSet(
        suffix: String,
        baseLat: Double,
        baseLng: Double,
        locationName: String
    ): List<RestroomInfo> {
        val offset = 0.00005 // Approx 5 meters offset to keep them separate but close
        return listOf(
            RestroomInfo(
                id = "RR_${suffix.uppercase()}_M",
                name = "Male Restroom - $locationName",
                type = "Male",
                congestion = congestionLevels.random(),
                isAccessible = true,
                nodeId = "node_rr_${suffix}_m"
            ),
            RestroomInfo(
                id = "RR_${suffix.uppercase()}_F",
                name = "Female Restroom - $locationName",
                type = "Female",
                congestion = congestionLevels.random(),
                isAccessible = true,
                nodeId = "node_rr_${suffix}_f"
            ),
            RestroomInfo(
                id = "RR_${suffix.uppercase()}_H",
                name = "Handicap Restroom - $locationName",
                type = "Handicap",
                congestion = congestionLevels.random(),
                isAccessible = true,
                nodeId = "node_rr_${suffix}_h"
            )
        )
    }
}

data class RestroomInfo(
    val id: String,
    val name: String,
    val type: String,
    val congestion: String,
    val isAccessible: Boolean,
    val nodeId: String
)
