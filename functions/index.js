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
 * Updates the congestion level of an amenity.
 */
exports.updateAmenityCongestion = onCall(async (request) => {
  const { amenityId, congestion } = request.data;

  if (!amenityId || !congestion) {
    throw new HttpsError("invalid-argument", "amenityId and congestion are required.");
  }

  try {
    logger.info(`Updating amenity ${amenityId} congestion to ${congestion}`);
    await db.collection("Amenity").doc(amenityId).update({
      Congestion: congestion
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
