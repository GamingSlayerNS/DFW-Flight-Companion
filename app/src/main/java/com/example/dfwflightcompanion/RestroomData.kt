package com.example.dfwflightcompanion

object RestroomData {
    val restroomSets = (1..15).map { setNum ->
        listOf(
            RestroomInfo(
                id = "RR_${setNum}_M",
                name = "Male Restroom - Set $setNum",
                type = "Male",
                isAccessible = true,
                nodeId = "NODE_RR_$setNum"
            ),
            RestroomInfo(
                id = "RR_${setNum}_F",
                name = "Female Restroom - Set $setNum",
                type = "Female",
                isAccessible = true,
                nodeId = "NODE_RR_$setNum"
            ),
            RestroomInfo(
                id = "RR_${setNum}_H",
                name = "Handicap Restroom - Set $setNum",
                type = "Handicap",
                isAccessible = true,
                nodeId = "NODE_RR_$setNum"
            )
        )
    }.flatten()
}

data class RestroomInfo(
    val id: String,
    val name: String,
    val type: String,
    val isAccessible: Boolean,
    val nodeId: String
)
