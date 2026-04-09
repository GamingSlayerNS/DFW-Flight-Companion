package com.example.dfwflightcompanion

object RestroomData {
    val restroomSets = listOf(
        // Set 1: North West
        createSet(1, "poi_rr_nw", "North West"),
        // Set 2: South West
        createSet(2, "poi_rr_sw", "South West"),
        // Set 3: North East
        createSet(3, "poi_rr_ne", "North East"),
        // Set 4: South East
        createSet(4, "poi_rr_se", "South East")
        // Note: For now, I'm mapping the 4 POIs available in routing.geojson.
        // If you add more POIs to routing.geojson, add them here too.
    ).flatten()

    private fun createSet(setNum: Int, nodeId: String, location: String): List<RestroomInfo> {
        return listOf(
            RestroomInfo(
                id = "RR_${setNum}_M",
                name = "Male Restroom - $location",
                type = "Male",
                isAccessible = true,
                nodeId = nodeId
            ),
            RestroomInfo(
                id = "RR_${setNum}_F",
                name = "Female Restroom - $location",
                type = "Female",
                isAccessible = true,
                nodeId = nodeId
            ),
            RestroomInfo(
                id = "RR_${setNum}_H",
                name = "Handicap Restroom - $location",
                type = "Handicap",
                isAccessible = true,
                nodeId = nodeId
            )
        )
    }
}

data class RestroomInfo(
    val id: String,
    val name: String,
    val type: String,
    val isAccessible: Boolean,
    val nodeId: String
)
