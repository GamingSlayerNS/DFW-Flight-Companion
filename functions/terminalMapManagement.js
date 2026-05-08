const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { db, rtdb } = require("./shared");

const MERGE_THRESHOLD = 1e-5;

const getOrCreateNode = (nodePool, lat, lng) => {
    const existing = nodePool.find((n) => {
        const dx = n.lng - lng;
        const dy = n.lat - lat;
        return dx * dx + dy * dy < MERGE_THRESHOLD * MERGE_THRESHOLD;
    });
    if (existing) return existing;
    const newNode = { lat, lng };
    nodePool.push(newNode);
    return newNode;
};

const rtdbKey = (lng, lat) => `${lng},${lat}`.replace(/\./g, "_");

exports.getMapBackgrounds = onCall(async (request) => {
    try {
        logger.info("Fetching MapBackgrounds from Firestore...");
        const snapshot = await db.collection("MapBackground").get();
        const backgrounds = [];

        snapshot.forEach((doc) => {
            const data = doc.data();
            backgrounds.push({
                id: doc.id,
                ...data,
                coordinates: data.coordinates
                    ? data.coordinates.map((p) => ({
                          latitude: p.latitude,
                          longitude: p.longitude,
                      }))
                    : [],
            });
        });

        return backgrounds;
    } catch (error) {
        logger.error("Error fetching MapBackgrounds:", error);
        throw new HttpsError("internal", "Failed to fetch MapBackgrounds.");
    }
});

exports.getPathEdges = onCall(async (request) => {
    try {
        logger.info("Fetching PathEdges from Firestore...");
        const snapshot = await db.collection("PathEdge").get();
        const edges = [];

        snapshot.forEach((doc) => {
            const data = doc.data();
            edges.push({
                id: doc.id,
                ...data,
                coordinates: data.coordinates
                    ? data.coordinates.map((p) => ({
                          latitude: p.latitude,
                          longitude: p.longitude,
                      }))
                    : [],
            });
        });

        return edges;
    } catch (error) {
        logger.error("Error fetching PathEdges:", error);
        throw new HttpsError("internal", "Failed to fetch PathEdges.");
    }
});

exports.getMapNodes = onCall(async (request) => {
    try {
        logger.info("Fetching MapNodes from Firestore...");
        const snapshot = await db.collection("MapNode").get();
        const nodes = [];

        snapshot.forEach((doc) => {
            const data = doc.data();
            nodes.push({
                id: doc.id,
                ...data,
                coordinates: data.coordinates
                    ? {
                          latitude: data.coordinates.latitude,
                          longitude: data.coordinates.longitude,
                      }
                    : null,
            });
        });

        logger.info(`Successfully fetched ${nodes.length} nodes.`);
        return nodes;
    } catch (error) {
        logger.error("Error fetching MapNodes:", error);
        throw new HttpsError("internal", "Failed to fetch MapNodes.");
    }
});

exports.publishNavigationGraph = onCall(async (request) => {
    try {
        logger.info("Publishing Navigation Graph...");
        const snapshot = await db.collection("PathEdge").get();

        const nodePool = [];
        const graph = {};

        snapshot.forEach((doc) => {
            const data = doc.data();
            if (data.type !== "path") return;
            const coords = data.coordinates || [];
            for (let i = 0; i < coords.length - 1; i++) {
                const nodeA = getOrCreateNode(nodePool, coords[i].latitude, coords[i].longitude);
                const nodeB = getOrCreateNode(nodePool, coords[i + 1].latitude, coords[i + 1].longitude);
                const keyA = `${nodeA.lng},${nodeA.lat}`;
                const keyB = `${nodeB.lng},${nodeB.lat}`;

                if (!graph[keyA]) graph[keyA] = { node: nodeA, neighbors: [] };
                if (!graph[keyB]) graph[keyB] = { node: nodeB, neighbors: [] };
                if (!graph[keyA].neighbors.some((n) => n.lat === nodeB.lat && n.lng === nodeB.lng)) {
                    graph[keyA].neighbors.push(nodeB);
                }
                if (!graph[keyB].neighbors.some((n) => n.lat === nodeA.lat && n.lng === nodeA.lng)) {
                    graph[keyB].neighbors.push(nodeA);
                }
            }
        });

        const graphData = Object.values(graph);
        await db.collection("MapData").doc("currentGraph").set({
            data: graphData,
            lastUpdated: Date.now(),
        });

        return { success: true, nodeCount: graphData.length };
    } catch (error) {
        logger.error("Error publishing navigation graph:", error);
        throw new HttpsError("internal", "Failed to publish graph.");
    }
});

exports.publishGraphToRealtime = onCall(async (request) => {
    try {
        logger.info("Publishing Navigation Graph to Realtime Database...");
        const snapshot = await db.collection("PathEdge").get();

        const nodePool = [];
        const graph = {};
        const pathEdges = {};

        snapshot.forEach((doc) => {
            const data = doc.data();
            if (data.type !== "path") return;
            const edgeId = doc.id;
            const coords = data.coordinates || [];

            pathEdges[edgeId] = {
                id: edgeId,
                name: data.name ?? null,
                isOpen: data.isOpen ?? true,
                congestion: 0,
                coordinates: coords.map((p) => ({ lat: p.latitude, lng: p.longitude })),
            };

            for (let i = 0; i < coords.length - 1; i++) {
                const nodeA = getOrCreateNode(nodePool, coords[i].latitude, coords[i].longitude);
                const nodeB = getOrCreateNode(nodePool, coords[i + 1].latitude, coords[i + 1].longitude);
                const keyA = `${nodeA.lng},${nodeA.lat}`;
                const keyB = `${nodeB.lng},${nodeB.lat}`;

                if (!graph[keyA]) graph[keyA] = { node: nodeA, neighbors: {} };
                if (!graph[keyB]) graph[keyB] = { node: nodeB, neighbors: {} };

                const rkA = rtdbKey(nodeA.lng, nodeA.lat);
                const rkB = rtdbKey(nodeB.lng, nodeB.lat);

                if (!graph[keyA].neighbors[rkB]) {
                    graph[keyA].neighbors[rkB] = { ...nodeB, congestion: 0, edge_id: edgeId };
                }
                if (!graph[keyB].neighbors[rkA]) {
                    graph[keyB].neighbors[rkA] = { ...nodeA, congestion: 0, edge_id: edgeId };
                }
            }
        });

        const rtdbPayload = {};
        Object.values(graph).forEach((entry) => {
            const nodeKey = rtdbKey(entry.node.lng, entry.node.lat);
            rtdbPayload[nodeKey] = {
                node: entry.node,
                neighbors: entry.neighbors,
            };
        });

        await rtdb.ref("MapData/CurrentGraph").set({
            data: rtdbPayload,
            edges: pathEdges,
            lastUpdated: Date.now(),
        });

        logger.info(`Graph published: ${Object.keys(graph).length} nodes, ${Object.keys(pathEdges).length} edges.`);
        return { success: true, nodeCount: Object.keys(graph).length, edgeCount: Object.keys(pathEdges).length };
    } catch (error) {
        logger.error("Error publishing navigation graph to Realtime Database:", error);
        throw new HttpsError("internal", "Failed to publish graph.");
    }
});

exports.getNavigationGraph = onCall(async (request) => {
    try {
        const doc = await db.collection("MapData").doc("currentGraph").get();
        if (!doc.exists) {
            throw new HttpsError("not-found", "Navigation graph has not been published.");
        }
        return doc.data().data;
    } catch (error) {
        logger.error("Error fetching navigation graph:", error);
        throw new HttpsError("internal", "Failed to fetch graph.");
    }
});
