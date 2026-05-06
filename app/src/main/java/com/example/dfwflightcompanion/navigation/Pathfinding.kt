package com.example.dfwflightcompanion.navigation

import kotlin.math.sqrt
import java.util.PriorityQueue

// Data Model
data class Node(val lng: Double, val lat: Double)

data class Edge(val target: Node, val congestion: Double = 0.0)

// Utility Functions
object Pathfinding {

    private const val CONGESTION_WEIGHT = 1.5 // Tune this to control how much congestion affects routing

    fun distance(a: Node, b: Node): Double {
        val dx = a.lng - b.lng
        val dy = a.lat - b.lat
        return sqrt(dx * dx + dy * dy)
    }

    // Congestion-aware edge cost
    private fun edgeCost(a: Node, edge: Edge): Double {
        val dist = distance(a, edge.target)
        return dist * (1.0 + CONGESTION_WEIGHT * edge.congestion)
    }

    // Find nearest node to a point
    fun findNearestNode(lng: Double, lat: Double, nodes: Set<Node>): Node {
        return nodes.minByOrNull {
            val dx = it.lng - lng
            val dy = it.lat - lat
            dx * dx + dy * dy
        } ?: throw IllegalStateException("Graph has no nodes")
    }

    // A* Algorithm — now uses Map<Node, List<Edge>> for congestion-aware routing
    fun aStar(graph: Map<Node, List<Edge>>, start: Node, end: Node): List<Node> {
        val gScore = HashMap<Node, Double>().withDefault { Double.MAX_VALUE }
        val fScore = HashMap<Node, Double>().withDefault { Double.MAX_VALUE }
        val prev = HashMap<Node, Node?>()
        val visited = HashSet<Node>()

        val pq = PriorityQueue(compareBy<Pair<Node, Double>> { it.second })

        gScore[start] = 0.0
        fScore[start] = distance(start, end)
        pq.add(start to fScore.getValue(start))

        while (pq.isNotEmpty()) {
            val (current, _) = pq.poll()

            if (current == end) break
            if (!visited.add(current)) continue // Skip already-settled nodes

            for (edge in graph[current].orEmpty()) {
                val neighbor = edge.target
                if (neighbor in visited) continue

                val tentativeG = gScore.getValue(current) + edgeCost(current, edge)

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
    private fun reconstructPath(prev: Map<Node, Node?>, end: Node): List<Node> {
        val path = ArrayDeque<Node>()
        var current: Node? = end

        while (current != null) {
            path.addFirst(current)
            current = prev[current]
        }

        return path.toList()
    }

    fun closestPointOnSegment(p: Node, a: Node, b: Node): Node {
        val abx = b.lng - a.lng
        val aby = b.lat - a.lat
        val apx = p.lng - a.lng
        val apy = p.lat - a.lat

        val abLenSq = abx * abx + aby * aby
        if (abLenSq == 0.0) return a // a and b are the same point

        val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)

        return Node(lng = a.lng + t * abx, lat = a.lat + t * aby)
    }

    fun findClosestPointOnGraph(
        user: Node,
        graph: Map<Node, List<Edge>>
    ): Triple<Node, Node, Node> {
        var closestPoint = user
        var bestA = user
        var bestB = user
        var minDistSq = Double.MAX_VALUE

        for ((a, edges) in graph) {
            for (edge in edges) {
                val b = edge.target
                val projected = closestPointOnSegment(user, a, b)

                val dx = projected.lng - user.lng
                val dy = projected.lat - user.lat
                val distSq = dx * dx + dy * dy

                if (distSq < minDistSq) {
                    minDistSq = distSq
                    closestPoint = projected
                    bestA = a
                    bestB = b
                }
            }
        }

        return Triple(closestPoint, bestA, bestB)
    }

    fun insertTemporaryNode(
        graph: Map<Node, List<Edge>>,
        a: Node,
        b: Node,
        snapped: Node
    ): Map<Node, List<Edge>> {
        // If snapped landed exactly on an existing node, no insertion needed
        if (snapped == a || snapped == b) return graph

        val newGraph = graph.mapValues { it.value.toMutableList() }.toMutableMap()

        // Remove original edge in both directions
        newGraph[a]?.removeAll { it.target == b }
        newGraph[b]?.removeAll { it.target == a }

        // Connect snapped node to both endpoints (no congestion for temporary node)
        newGraph[snapped] = mutableListOf(Edge(a), Edge(b))
        newGraph[a]?.add(Edge(snapped))
        newGraph[b]?.add(Edge(snapped))

        return newGraph
    }
}