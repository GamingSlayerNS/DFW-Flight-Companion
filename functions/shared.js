const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getDatabase } = require("firebase-admin/database");

initializeApp();
const db = getFirestore();
const rtdb = getDatabase();

module.exports = {
    db,
    rtdb,
};
