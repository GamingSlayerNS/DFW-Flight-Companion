package com.example.dfwflightcompanion.navigation

import kotlin.math.sqrt
import java.util.PriorityQueue
import kotlin.collections.iterator

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

    fun closestPointOnSegment(p: Node, a: Node, b: Node): Node {
        val ax = a.lng
        val ay = a.lat
        val bx = b.lng
        val by = b.lat
        val px = p.lng
        val py = p.lat

        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay

        val abLenSq = abx * abx + aby * aby
        val dot = apx * abx + apy * aby
        val t = (dot / abLenSq).coerceIn(0.0, 1.0)

        return Node(
            lng = ax + t * abx,
            lat = ay + t * aby
        )
    }

    fun findClosestPointOnGraph(
        user: Node,
        graph: Map<Node, List<Node>>
    ): Triple<Node, Node, Node> {

        var closestPoint = user
        var bestA = user
        var bestB = user
        var minDist = Double.MAX_VALUE

        for ((a, neighbors) in graph) {
            for (b in neighbors) {

                val projected = closestPointOnSegment(user, a, b)

                val dx = projected.lng - user.lng
                val dy = projected.lat - user.lat
                val dist = dx * dx + dy * dy

                if (dist < minDist) {
                    minDist = dist
                    closestPoint = projected
                    bestA = a
                    bestB = b
                }
            }
        }

        return Triple(closestPoint, bestA, bestB)
    }

    fun insertTemporaryNode(
        graph: Map<Node, List<Node>>,
        a: Node,
        b: Node,
        snapped: Node
    ): Map<Node, List<Node>> {

        val newGraph = graph.mapValues { it.value.toMutableList() }.toMutableMap()

        // Remove original edge
        newGraph[a]?.remove(b)
        newGraph[b]?.remove(a)

        // Add snapped node
        newGraph[snapped] = mutableListOf()

        // Connect snapped point to both ends
        newGraph[a]?.add(snapped)
        newGraph[b]?.add(snapped)
        newGraph[snapped]?.add(a)
        newGraph[snapped]?.add(b)

        return newGraph
    }
}