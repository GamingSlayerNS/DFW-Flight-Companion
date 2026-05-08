package com.example.dfwflightcompanion.navigation

import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GraphNode(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

data class GraphNeighbor(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val congestion: Double = 0.0
)

data class GraphEntry(
    val node: GraphNode = GraphNode(),
    val neighbors: Map<String, GraphNeighbor> = emptyMap()
)

data class NavigationGraph(
    val data: Map<String, GraphEntry> = emptyMap(),
    val lastUpdated: Long = 0L
)

object NavigationGraphRepository {

    private val database = FirebaseDatabase.getInstance()
    private val graphRef = database.getReference("MapData/CurrentGraph")

    private val _navigationGraph = MutableStateFlow<NavigationGraph?>(null)
    val navigationGraph: StateFlow<NavigationGraph?> = _navigationGraph

    private val _edgeList= MutableStateFlow<String>("{}")
    val edgeList: StateFlow<String> = _edgeList

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var listener: ValueEventListener? = null

    fun startListening() {
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val lastUpdated = snapshot.child("lastUpdated").getValue(Long::class.java) ?: 0L

                    val dataSnapshot = snapshot.child("data")
                    val graphEntries = mutableMapOf<String, GraphEntry>()

                    for (entrySnapshot in dataSnapshot.children) {
                        val nodeSnapshot = entrySnapshot.child("node")
                        val node = GraphNode(
                            lat = nodeSnapshot.child("lat").getValue(Double::class.java) ?: 0.0,
                            lng = nodeSnapshot.child("lng").getValue(Double::class.java) ?: 0.0
                        )

                        val neighbors = mutableMapOf<String, GraphNeighbor>()
                        for (neighborSnapshot in entrySnapshot.child("neighbors").children) {
                            val neighbor = GraphNeighbor(
                                lat = neighborSnapshot.child("lat").getValue(Double::class.java) ?: 0.0,
                                lng = neighborSnapshot.child("lng").getValue(Double::class.java) ?: 0.0,
                                congestion = neighborSnapshot.child("congestion").getValue(Double::class.java) ?: 0.0
                            )
                            neighbors[neighborSnapshot.key ?: continue] = neighbor
                        }

                        graphEntries[entrySnapshot.key ?: continue] = GraphEntry(node, neighbors)
                    }

                    _navigationGraph.value = NavigationGraph(
                        data = graphEntries,
                        lastUpdated = lastUpdated
                    )

                    val edgeSnapshot = snapshot.child("edges")
                    val edgeEntries = mutableListOf<String>()
                    for (edge in edgeSnapshot.children) {
                        val name = edge.child("name").getValue(String::class.java) ?: ""
                        val id = edge.child("id").getValue(String::class.java) ?: ""
                        val type = "path"
                        val level = 1
                        val congestion =
                            edge.child("congestion").getValue(Double::class.java) ?: 0.0
                        val isOpen = edge.child("isOpen").getValue(Boolean::class.java) ?: false
                        val coords = mutableListOf<String>()
                        for (coordSnapshot in edge.child("coordinates").children) {
                            val lat = coordSnapshot.child("lat").getValue(Double::class.java) ?: 0.0
                            val lng = coordSnapshot.child("lng").getValue(Double::class.java) ?: 0.0
                            coords.add("[${lng}, ${lat}, $level]")
                        }
                        val coordString = coords.joinToString(",")
                        edgeEntries.add("""{"type": "Feature", "properties": {"type": "$type", "name": "$name", 
                            |"id": "$id", "level": "$level", "weight": $congestion, "isOpen": "$isOpen" }, 
                            |"geometry": {"type": "LineString", "coordinates": [$coordString]}}""".trimMargin())
                    }

                    val edgeGeoJson = """{"type": "FeatureCollection", "features": [${edgeEntries.joinToString(",")}]}"""
                    _edgeList.value = edgeGeoJson
                    _isLoading.value = false
                    _error.value = null

                } catch (e: Exception) {
                    _error.value = "Failed to parse graph: ${e.message}"
                    _isLoading.value = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = error.message
                _isLoading.value = false
            }
        }

        graphRef.addValueEventListener(listener!!)
    }

    fun stopListening() {
        listener?.let { graphRef.removeEventListener(it) }
        listener = null
    }


}