package com.example.dfwflightcompanion.navigation

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RoutingNode(
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val level: Int = 0,
    val wait_time: Double = 0.0
)

data class RoutingEdge(
    val distance: Double = 0.0,
    val type: String = "",
    val congestion: Double = 0.0
)

data class RoutingGraph(
    val nodes: Map<String, RoutingNode> = emptyMap(),
    // Edges are a Map of Node ID -> Map of Connected Node ID -> Edge Details
    val edges: Map<String, Map<String, RoutingEdge>> = emptyMap()
)

class Graph {
    private val databaseRef = FirebaseDatabase.getInstance().getReference("routing_graph")

    // StateFlow holds the latest graph and emits updates to any observers (like your UI or routing engine)
    private val _graphState = MutableStateFlow(RoutingGraph())
    val graphState: StateFlow<RoutingGraph> = _graphState.asStateFlow()

    private var listener: ValueEventListener? = null

    fun startListening() {
        if (listener != null) return // Prevent multiple listeners

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    // Firebase maps the JSON directly into our Kotlin data class
                    val graph = snapshot.getValue(RoutingGraph::class.java)

                    if (graph != null) {
                        // Update the flow with the fresh data
                        _graphState.value = graph
                        Log.d("GraphRepository", "Graph updated: ${graph.nodes.size} nodes loaded.")
                    } else {
                        Log.w("GraphRepository", "Snapshot exists but graph is null.")
                    }
                } catch (e: Exception) {
                    Log.e("GraphRepository", "Error parsing graph data", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GraphRepository", "Database listen cancelled: ${error.message}")
            }
        }

        // addValueEventListener keeps the connection open for real-time updates
        databaseRef.addValueEventListener(listener!!)
    }

    fun stopListening() {
        listener?.let {
            databaseRef.removeEventListener(it)
            listener = null
        }
    }
}