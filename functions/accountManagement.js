const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { db } = require("./shared");

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
            ...doc.data(),
        };

        logger.info(`Successfully fetched profile for user: ${profile.Username || profile.id}`);
        return profile;
    } catch (error) {
        logger.error("Error fetching user profile:", error);
        throw new HttpsError("internal", "Failed to fetch user profile from the database.");
    }
});
