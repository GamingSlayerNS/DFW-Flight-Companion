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
    private val TAG = "RoutingGraph"
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

    // Function to Init firebase with geojson
    fun toFirebase(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geoJsonString = context.assets.open("mapdata/routing.geojson").bufferedReader().use { it.readText() }
                val geoJson = JSONObject(geoJsonString)
                val features = geoJson.getJSONArray("features")

                val nodes = JSONObject()
                val edges = JSONObject()

                val coordToId = HashMap<Triple<Double, Double, Int>, String>()

                //  Extract Semantic Nodes (Points of Interest)
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val geometry = feature.getJSONObject("geometry")
                    val properties = feature.getJSONObject("properties")

                    if (geometry.getString("type") == "Point") {
                        val coords = geometry.getJSONArray("coordinates")
                        val lng = coords.getDouble(0)
                        val lat = coords.getDouble(1)
                        //  Use optInt with a fallback, or getDouble and round, to avoid silent truncation
                        val level = coords.optDouble(2, 0.0).toInt()

                        // Null-safe property access to prevent hard crashes
                        val id = properties.optString("id").takeIf { it.isNotBlank() } ?: run {
                            Log.w(TAG, "Point feature at index $i missing 'id', skipping.")
                            continue
                        }
                        val name = properties.optString("name", "unnamed")

                        val nodeObj = JSONObject().apply {
                            put("name", name)
                            put("lat", lat)
                            put("lng", lng)
                            put("level", level)
                            put("wait_time", 0.0)
                        }
                        nodes.put(id, nodeObj)
                        coordToId[Triple(lng, lat, level)] = id
                    }
                }

                // Pass 2 & 3: Generate Waypoints & Build Adjacency List (Edges)
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val geometry = feature.getJSONObject("geometry")
                    val properties = feature.getJSONObject("properties")

                    if (geometry.getString("type") == "LineString") {
                        val coords = geometry.getJSONArray("coordinates")
                        val pathType = properties.optString("id", "path")
                        // Respect one-way directional property from GeoJSON
                        val isOneWay = properties.optBoolean("oneway", false)

                        for (j in 0 until coords.length() - 1) {
                            val p1 = coords.getJSONArray(j)
                            val p2 = coords.getJSONArray(j + 1)

                            val lng1 = p1.getDouble(0)
                            val lat1 = p1.getDouble(1)
                            val level1 = p1.optDouble(2, 0.0).toInt()

                            val lng2 = p2.getDouble(0)
                            val lat2 = p2.getDouble(1)
                            val level2 = p2.optDouble(2, 0.0).toInt()

                            // lookup via HashMap
                            val id1 = coordToId.getOrPut(Triple(lng1, lat1, level1)) {
                                // Sanitize the ID by replacing periods with underscores
                                val newId = "waypoint_${lat1}:${lng1}:${level1}".replace(".", "_")
                                val nodeObj = JSONObject().apply {
                                    put("name", "waypoint")
                                    put("lat", lat1)
                                    put("lng", lng1)
                                    put("level", level1)
                                    put("wait_time", 0.0)
                                }
                                nodes.put(newId, nodeObj)
                                newId
                            }

                            val id2 = coordToId.getOrPut(Triple(lng2, lat2, level2)) {
                                // Sanitize the ID by replacing periods with underscores
                                val newId = "waypoint_${lat1}:${lng1}:${level1}".replace(".", "_")
                                val nodeObj = JSONObject().apply {
                                    put("name", "waypoint")
                                    put("lat", lat2)
                                    put("lng", lng2)
                                    put("level", level2)
                                    put("wait_time", 0.0)
                                }
                                nodes.put(newId, nodeObj)
                                newId
                            }

                            Log.d(TAG, "id1: $id1, id2: $id2")

                            // Haversine distance for geographic coordinates (returns meters)
                            val distanceMeters = haversine(lat1, lng1, lat2, lng2)

                            if (!edges.has(id1)) edges.put(id1, JSONObject())
                            if (!edges.has(id2)) edges.put(id2, JSONObject())

                            // Forward edge (always added)
                            edges.getJSONObject(id1).put(id2, JSONObject().apply {
                                put("distance", distanceMeters)
                                put("type", pathType)
                                put("congestion", 0.0)
                            })

                            // Only add reverse edge if not one-way
                            if (!isOneWay) {
                                edges.getJSONObject(id2).put(id1, JSONObject().apply {
                                    put("distance", distanceMeters)
                                    put("type", pathType)
                                    put("congestion", 0.0)
                                })
                            }
                        }
                    }
                }

                val firebaseGraph = JSONObject().apply {
                    put("nodes", nodes)
                    put("edges", edges)
                }


                val graphString = firebaseGraph.toString(4)
                val mapType = object : TypeToken<Map<String, Any>>() {}.type
                val graphMap: Map<String, Any> = Gson().fromJson(graphString, mapType)

                val databaseRef = FirebaseDatabase.getInstance().getReference("routing_graph")

                Log.d(TAG, "ATTEMPTING PUSH TO GRAPH")
                databaseRef.setValue(graphMap)
                    .addOnSuccessListener {
                        // Success! The data is now live and synchronized.
                        println("Successfully pushed the routing graph to Firebase.")
                    }
                    .addOnFailureListener { error ->
                        // Handle any permission or network issues
                        println("Failed to push graph: ${error.message}")
                    }
                Log.d(TAG, "GRAPH PUSH ATTEMPTED")


            } catch (e: Exception) {
                Log.e(TAG, "Error building or pushing routing graph", e)
            }
        }
    }

}
