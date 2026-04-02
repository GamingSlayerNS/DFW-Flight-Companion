package com.example.dfwflightcompanion

import android.content.Context
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point

object GraphBuilder {

    // Adjust this if your coordinates are slightly off between segments
    private const val MERGE_THRESHOLD = 1e-5

    fun fromGeoJson(context: Context): Map<Node, List<Node>> {

        val graph = mutableMapOf<Node, MutableList<Node>>()
        val nodePool = mutableListOf<Node>() // used to merge close nodes

        // Load GeoJSON from assets
        val geoJson = context.assets.open("routing.geojson")
            .bufferedReader()
            .use { it.readText() }

        val featureCollection = FeatureCollection.fromJson(geoJson)

        // Process each feature
        featureCollection.features()?.forEach { feature ->

            if (feature.getStringProperty("type") != "path") return@forEach

            val geometry = feature.geometry()
            if (geometry !is LineString) return@forEach

            val coords = geometry.coordinates()

            for (i in 0 until coords.size - 1) {

                val rawA = coords[i]
                val rawB = coords[i + 1]

                val nodeA = getOrCreateNode(rawA, nodePool)
                val nodeB = getOrCreateNode(rawB, nodePool)

                // Add edge A -> B
                graph.getOrPut(nodeA) { mutableListOf() }.add(nodeB)

                // Add edge B -> A (bidirectional)
                graph.getOrPut(nodeB) { mutableListOf() }.add(nodeA)
            }
        }

        return graph
    }

    // Node merging
    private fun getOrCreateNode(point: Point, pool: MutableList<Node>): Node {

        val lng = point.longitude()
        val lat = point.latitude()

        // Try to find an existing nearby node
        val existing = pool.find {
            val dx = it.lng - lng
            val dy = it.lat - lat
            (dx * dx + dy * dy) < MERGE_THRESHOLD * MERGE_THRESHOLD
        }

        if (existing != null) return existing

        // Otherwise create new node
        val newNode = Node(lng, lat)
        pool.add(newNode)
        return newNode
    }
}