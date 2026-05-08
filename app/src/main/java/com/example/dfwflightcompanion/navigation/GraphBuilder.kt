package com.example.dfwflightcompanion.navigation

import org.json.JSONObject
import android.content.Context
import android.util.Log
import com.example.dfwflightcompanion.helpers.haversine

import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GraphBuilder {
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
    fun fromNavigationGraph(navigationGraph: NavigationGraph): Map<Node, List<Edge>> {
        val graph = mutableMapOf<Node, List<Edge>>()

        for ((_, entry) in navigationGraph.data) {
            val node = Node(entry.node.lng, entry.node.lat)
            val edges = entry.neighbors.values.map { neighbor ->
                Edge(
                    target = Node(neighbor.lng, neighbor.lat),
                    congestion = neighbor.congestion
                )
            }
            graph[node] = edges
        }

        return graph
    }
}
