const {setGlobalOptions} = require("firebase-functions");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");

// Initialize Admin SDK to access Firestore
initializeApp();
const db = getFirestore();

setGlobalOptions({maxInstances: 10});

/**
 * Fetches all amenities from the "Amenity" collection.
 */
exports.getAmenities = onCall(async (request) => {
  try {
    logger.info("Fetching amenities from Firestore...");
    const snapshot = await db.collection("Amenity").get();

    const amenities = [];
    snapshot.forEach(doc => {
      amenities.push({
        id: doc.id,
        ...doc.data()
      });
    });

    logger.info(`Successfully fetched ${amenities.length} amenities.`);
    return amenities;
  } catch (error) {
    logger.error("Error fetching amenities:", error);
    throw new HttpsError(
      "internal",
      "Failed to fetch amenities from the database."
    );
  }
});

/**
 * Updates the congestion level of an amenity and sets the last updated timestamp.
 */
exports.updateAmenityCongestion = onCall(async (request) => {
  const { amenityId, congestion } = request.data;

  if (!amenityId || !congestion) {
    throw new HttpsError("invalid-argument", "amenityId and congestion are required.");
  }

  try {
    logger.info(`Updating amenity ${amenityId} congestion to ${congestion}`);
    await db.collection("Amenity").doc(amenityId).update({
      Congestion: congestion,
      LastUpdated: Date.now()
    });
    return { success: true };
  } catch (error) {
    logger.error("Error updating amenity congestion:", error);
    throw new HttpsError("internal", "Failed to update amenity congestion.");
  }
});

/**
 * Fetches the first user profile from the "User" collection.
 */
exports.getUserProfile = onCall(async (request) => {
  try {
    logger.info("Fetching user profile from Firestore...");
    const snapshot = await db.collection("User").limit(1).get();

    if (snapshot.empty) {
      logger.info("No user profiles found.");
      return null;
    }

    const doc = snapshot.docs[0];
    const profile = {
      id: doc.id,
      ...doc.data()
    };

    logger.info(`Successfully fetched profile for user: ${profile.Username || profile.id}`);
    return profile;
  } catch (error) {
    logger.error("Error fetching user profile:", error);
    throw new HttpsError(
      "internal",
      "Failed to fetch user profile from the database."
    );
  }
});

/**
 * Fetches all features from the "MapBackground" collection.
 */
exports.getMapBackgrounds = onCall(async (request) => {
  try {
    logger.info("Fetching MapBackgrounds from Firestore...");
    const snapshot = await db.collection("MapBackground").get();
    const backgrounds = [];
    snapshot.forEach(doc => {
      const data = doc.data();
      backgrounds.push({
        id: doc.id,
        ...data,
        // Convert GeoPoint to simple object if necessary for JSON serialization
        coordinates: data.coordinates ? data.coordinates.map(p => ({
          latitude: p.latitude,
          longitude: p.longitude
        })) : []
      });
    });
    return backgrounds;
  } catch (error) {
    logger.error("Error fetching MapBackgrounds:", error);
    throw new HttpsError("internal", "Failed to fetch MapBackgrounds.");
  }
});

/**
 * Fetches all edges from the "PathEdge" collection.
 */
exports.getPathEdges = onCall(async (request) => {
  try {
    logger.info("Fetching PathEdges from Firestore...");
    const snapshot = await db.collection("PathEdge").get();
    const edges = [];
    snapshot.forEach(doc => {
      const data = doc.data();
      edges.push({
        id: doc.id,
        ...data,
        coordinates: data.coordinates ? data.coordinates.map(p => ({
          latitude: p.latitude,
          longitude: p.longitude
        })) : []
      });
    });
    return edges;
  } catch (error) {
    logger.error("Error fetching PathEdges:", error);
    throw new HttpsError("internal", "Failed to fetch PathEdges.");
  }
});

/**
 * Fetches all nodes from the "MapNode" collection.
 */
exports.getMapNodes = onCall(async (request) => {
  try {
    logger.info("Fetching MapNodes from Firestore...");
    const snapshot = await db.collection("MapNode").get();
    const nodes = [];
    snapshot.forEach(doc => {
      const data = doc.data();
      nodes.push({
        id: doc.id,
        ...data,
        // MapNode has a single GeoPoint coordinates field
        coordinates: data.coordinates ? {
          latitude: data.coordinates.latitude,
          longitude: data.coordinates.longitude
        } : null
      });
    });
    logger.info(`Successfully fetched ${nodes.length} nodes.`);
    return nodes;
  } catch (error) {
    logger.error("Error fetching MapNodes:", error);
    throw new HttpsError("internal", "Failed to fetch MapNodes.");
  }
});

/**
 * Admin Function: Pre-computes the navigation graph and saves it as a single document.
 * This should be called whenever the PathEdge collection is updated.
 */
exports.publishNavigationGraph = onCall(async (request) => {
  try {
    logger.info("Publishing Navigation Graph...");
    const snapshot = await db.collection("PathEdge").get();

    const nodePool = [];
    const graph = {};
    const MERGE_THRESHOLD = 1e-5;

    const getOrCreateNode = (lat, lng) => {
      const existing = nodePool.find(n => {
        const dx = n.lng - lng;
        const dy = n.lat - lat;
        return (dx * dx + dy * dy) < (MERGE_THRESHOLD * MERGE_THRESHOLD);
      });
      if (existing) return existing;
      const newNode = { lat, lng };
      nodePool.push(newNode);
      return newNode;
    };

    snapshot.forEach(doc => {
      const data = doc.data();
      if (data.type !== "path") return;
      const coords = data.coordinates || [];
      for (let i = 0; i < coords.length - 1; i++) {
        const nodeA = getOrCreateNode(coords[i].latitude, coords[i].longitude);
        const nodeB = getOrCreateNode(coords[i+1].latitude, coords[i+1].longitude);
        const keyA = `${nodeA.lng},${nodeA.lat}`;
        const keyB = `${nodeB.lng},${nodeB.lat}`;
        if (!graph[keyA]) graph[keyA] = { node: nodeA, neighbors: [] };
        if (!graph[keyB]) graph[keyB] = { node: nodeB, neighbors: [] };
        if (!graph[keyA].neighbors.some(n => n.lat === nodeB.lat && n.lng === nodeB.lng)) {
          graph[keyA].neighbors.push(nodeB);
        }
        if (!graph[keyB].neighbors.some(n => n.lat === nodeA.lat && n.lng === nodeA.lng)) {
          graph[keyB].neighbors.push(nodeA);
        }
      }
    });

    const graphData = Object.values(graph);
    await db.collection("MapData").doc("currentGraph").set({
      data: graphData,
      lastUpdated: Date.now()
    });

    return { success: true, nodeCount: graphData.length };
  } catch (error) {
    logger.error("Error publishing navigation graph:", error);
    throw new HttpsError("internal", "Failed to publish graph.");
  }
});

/**
 * Client Function: Fetches the pre-computed graph from Firestore.
 * This is very efficient (1 document read).
 */
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
