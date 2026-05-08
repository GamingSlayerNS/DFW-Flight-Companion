const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { db } = require("./shared");

exports.getAmenities = onCall(async (request) => {
    try {
        logger.info("Fetching amenities from Firestore...");
        const snapshot = await db.collection("Amenity").get();

        const amenities = [];
        snapshot.forEach((doc) => {
            amenities.push({
                id: doc.id,
                ...doc.data(),
            });
        });

        logger.info(`Successfully fetched ${amenities.length} amenities.`);
        return amenities;
    } catch (error) {
        logger.error("Error fetching amenities:", error);
        throw new HttpsError("internal", "Failed to fetch amenities from the database.");
    }
});

exports.updateAmenityCongestion = onCall(async (request) => {
    const { amenityId, congestion } = request.data;

    if (!amenityId || !congestion) {
        throw new HttpsError("invalid-argument", "amenityId and congestion are required.");
    }

    try {
        logger.info(`Updating amenity ${amenityId} congestion to ${congestion}`);
        await db.collection("Amenity").doc(amenityId).update({
            Congestion: congestion,
            LastUpdated: Date.now(),
        });
        return { success: true };
    } catch (error) {
        logger.error("Error updating amenity congestion:", error);
        throw new HttpsError("internal", "Failed to update amenity congestion.");
    }
});

exports.getAmenityById = onCall(async (request) => {
    const { amenityId } = request.data;

    if (!amenityId) {
        throw new HttpsError("invalid-argument", "amenityId is required.");
    }

    try {
        logger.info(`Fetching amenity ${amenityId} from Firestore...`);
        const doc = await db.collection("Amenity").doc(amenityId).get();

        if (!doc.exists) {
            throw new HttpsError("not-found", `Amenity ${amenityId} not found.`);
        }

        return {
            id: doc.id,
            ...doc.data(),
        };
    } catch (error) {
        if (error instanceof HttpsError) throw error;
        logger.error("Error fetching amenity by id:", error);
        throw new HttpsError("internal", "Failed to fetch amenity.");
    }
});
