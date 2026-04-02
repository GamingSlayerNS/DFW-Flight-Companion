package com.example.dfwflightcompanion

import kotlin.math.sqrt
import java.util.PriorityQueue

// Data Model
data class Node(val lng: Double, val lat: Double)

// Utility Functions
object Pathfinding {

    fun distance(a: Node, b: Node): Double {
        val dx = a.lng - b.lng
        val dy = a.lat - b.lat
        return sqrt(dx * dx + dy * dy)
    }

    // Find nearest node to a point
    fun findNearestNode(
        lng: Double,
        lat: Double,
        nodes: Set<Node>
    ): Node {
        return nodes.minByOrNull {
            val dx = it.lng - lng
            val dy = it.lat - lat
            dx * dx + dy * dy
        } ?: throw IllegalStateException("Graph has no nodes")
    }

    // A* Algorithm
    fun aStar(
        graph: Map<Node, List<Node>>,
        start: Node,
        end: Node
    ): List<Node> {

        val gScore = mutableMapOf<Node, Double>().withDefault { Double.MAX_VALUE }
        val fScore = mutableMapOf<Node, Double>().withDefault { Double.MAX_VALUE }
        val prev = mutableMapOf<Node, Node?>()

        val pq = PriorityQueue(compareBy<Pair<Node, Double>> { it.second })

        gScore[start] = 0.0
        fScore[start] = distance(start, end)

        pq.add(start to fScore.getValue(start))

        while (pq.isNotEmpty()) {
            val (current, _) = pq.poll()

            if (current == end) break

            for (neighbor in graph[current].orEmpty()) {
                val tentativeG = gScore.getValue(current) + distance(current, neighbor)

                if (tentativeG < gScore.getValue(neighbor)) {
                    prev[neighbor] = current
                    gScore[neighbor] = tentativeG
                    fScore[neighbor] = tentativeG + distance(neighbor, end)

                    pq.add(neighbor to fScore.getValue(neighbor))
                }
            }
        }

        return reconstructPath(prev, end)
    }

    // Path reconstruction
    private fun reconstructPath(
        prev: Map<Node, Node?>,
        end: Node
    ): List<Node> {

        val path = mutableListOf<Node>()
        var current: Node? = end

        while (current != null) {
            path.add(current)
            current = prev[current]
        }

        return path.reversed()
    }
}