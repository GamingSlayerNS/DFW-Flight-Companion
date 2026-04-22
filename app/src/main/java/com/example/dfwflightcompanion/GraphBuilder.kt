package com.example.dfwflightcompanion

object GraphBuilder {

    /**
     * Parses the navigation graph data returned from the Firebase Cloud Function.
     * The input is a list of maps, where each map contains a 'node' and its 'neighbors'.
     */
    fun fromFirebase(data: List<Map<String, Any>>): Map<Node, List<Node>> {
        val graph = mutableMapOf<Node, List<Node>>()

        data.forEach { entry ->
            val nodeMap = entry["node"] as? Map<String, Any> ?: return@forEach
            val neighborsList = entry["neighbors"] as? List<Map<String, Any>> ?: return@forEach

            val currentNode = Node(
                lng = (nodeMap["lng"] as? Number)?.toDouble() ?: 0.0,
                lat = (nodeMap["lat"] as? Number)?.toDouble() ?: 0.0
            )

            val neighbors = neighborsList.mapNotNull {
                val lng = (it["lng"] as? Number)?.toDouble()
                val lat = (it["lat"] as? Number)?.toDouble()
                if (lng != null && lat != null) {
                    Node(lng, lat)
                } else {
                    null
                }
            }

            graph[currentNode] = neighbors
        }
        return graph
    }
}
