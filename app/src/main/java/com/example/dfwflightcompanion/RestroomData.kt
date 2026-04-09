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
    ).flatten()

    val congestionLevels = listOf("Low", "Medium", "High")

    private fun createSet(setNum: Int, nodeId: String, location: String): List<RestroomInfo> {
        return listOf(
            RestroomInfo(
                id = "RR_${setNum}_M",
                name = "Male Restroom - $location",
                type = "Male",
                congestion = congestionLevels.random(),
                isAccessible = true,
                nodeId = nodeId
            ),
            RestroomInfo(
                id = "RR_${setNum}_F",
                name = "Female Restroom - $location",
                type = "Female",
                congestion = congestionLevels.random(),
                isAccessible = true,
                nodeId = nodeId
            ),
            RestroomInfo(
                id = "RR_${setNum}_H",
                name = "Handicap Restroom - $location",
                type = "Handicap",
                congestion = congestionLevels.random(),
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
    val congestion: String,
    val isAccessible: Boolean,
    val nodeId: String
)
