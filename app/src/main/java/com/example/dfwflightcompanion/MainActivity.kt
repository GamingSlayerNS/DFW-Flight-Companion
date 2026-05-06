package com.example.dfwflightcompanion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.dfwflightcompanion.navigation.BottomNavigationBar
import com.example.dfwflightcompanion.ui.theme.DFWFlightCompanionTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.functions.functions
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("FirestoreDB", "MainActivity onCreate started")

        enableEdgeToEdge()
        setContent {
            DFWFlightCompanionTheme {
                var statusMessage by remember { mutableStateOf("Ready") }

                // SET TO TRUE TO WIPE AND RE-POPULATE FROM GEOJSON, THEN SET FALSE
                val shouldInitializeDb = false
                // SET TO TRUE TO WIPE AND RE-POPULATE RESTROOMS, THEN SET FALSE
                val shouldInitializeRestrooms = false
                // SET TO TRUE TO RE-PUBLISH NAVIGATION GRAPH IN BACKEND, THEN SET FALSE
                val shouldPublishGraph = false

                LaunchedEffect(Unit) {
                    val db = FirebaseFirestore.getInstance()
                    if (shouldPublishGraph) {
                        statusMessage = "Publishing Navigation Graph..."
                        Firebase.functions.getHttpsCallable("publishNavigationGraph").call()
                            .addOnSuccessListener {
                                Log.d("FirestoreDB", "Navigation graph published successfully")
                                statusMessage = "Graph Published!"
                            }
                            .addOnFailureListener { e ->
                                Log.e("FirestoreDB", "Failed to publish navigation graph", e)
                                statusMessage = "Graph Publication Failed!"
                            }
                    }

                    if (shouldInitializeDb) {
                        statusMessage = "Wiping and Initializing from GeoJSON..."
                        wipeAndSeedFirestore(db) {
                            Log.d("FirestoreDB", "GeoJSON Seed Finished")
                            if (shouldInitializeRestrooms) {
                                statusMessage = "Populating Restrooms..."
                                populateRestrooms(db) {
                                    statusMessage = "Full Initialization Complete!"
                                }
                            } else {
                                statusMessage = "Database Population Complete!"
                            }
                        }
                    } else if (shouldInitializeRestrooms) {
                        statusMessage = "Populating Restrooms..."
                        populateRestrooms(db) {
                            statusMessage = "Restroom Population Complete!"
                        }
                    }
                }

                BottomNavigationBar()
            }
        }
    }

    private fun populateRestrooms(db: FirebaseFirestore, onComplete: () -> Unit) {
        Log.d("FirestoreDB", "Populating restrooms...")



        // 1. First, delete all existing amenities to avoid duplicates
        db.collection("Amenity").get().addOnSuccessListener { result ->
            val batch = db.batch()

            result.forEach { batch.delete(it.reference) }


            batch.commit().addOnSuccessListener {
                Log.d("FirestoreDB", "Wiped old amenities. Adding new ones...")
                
                // 2. Add the new restrooms from RestroomData
                val addBatch = db.batch()
                try {


                    addBatch.commit().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("FirestoreDB", "Successfully added restrooms.")
                        } else {
                            Log.e("FirestoreDB", "Failed to add restrooms", task.exception)
                        }
                        onComplete()
                    }
                } catch (e: Exception) {
                    Log.e("FirestoreDB", "Error during restroom population", e)
                    onComplete()
                }
            }.addOnFailureListener { e ->
                Log.e("FirestoreDB", "Failed to wipe amenities", e)
                onComplete()
            }
            val routingJson = assets.open("mapdata/routing.geojson").bufferedReader().use { it.readText() }
            val routingObj = JSONObject(routingJson)
            val routingFeatures = routingObj.getJSONArray("features")
            val congestionLevels = listOf("Low", "Medium", "High")

            for (i in 0 until routingFeatures.length()) {
                val feature = routingFeatures.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                if (props.optString("type") == "poi") {
                    val id = props.optString("id")
                    val name = props.optString("name")
                    val subtype = props.optString("gender")
                    val isAccessible = (props.optString("gender") == "neutral")
                    val nodeId = props.optString("id")

                    db.collection("Amenity").document(id).set(
                        hashMapOf(
                            "AmenityID" to id,
                            "Name" to name,
                            "AmenityType" to "Restroom",
                            "SubTypeName" to subtype,
                            "Congestion" to congestionLevels.random(),
                            "IsAccessible" to isAccessible,
                            "NodeID" to nodeId
                        )
                    )
                }

            }
        }
    }

    private fun wipeAndSeedFirestore(db: FirebaseFirestore, onComplete: () -> Unit) {
        val collections = listOf(
            "Terminal", "MapNode", "PathEdge", "Amenity", 
            "AmenityUnit", "AmenitySchedule", "Sensor", "User", "UserReports", "MapBackground"
        )

        var collectionsProcessed = 0
        collections.forEach { collectionName ->
            db.collection(collectionName).get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val batch = db.batch()
                    task.result?.forEach { document -> batch.delete(document.reference) }
                    batch.commit().addOnCompleteListener {
                        collectionsProcessed++
                        if (collectionsProcessed == collections.size) {
                            seedFromGeoJson(db, onComplete)
                        }
                    }
                } else {
                    collectionsProcessed++
                    if (collectionsProcessed == collections.size) {
                        seedFromGeoJson(db, onComplete)
                    }
                }
            }
        }
    }

    // Build MapBackground, MapNode, PathEdge, and Amenity
    private fun seedFromGeoJson(db: FirebaseFirestore, onComplete: () -> Unit) {
        try {
            Log.d("FirestoreDB", "Starting GeoJSON Seed...")
            
            // 1. Seed Terminal Root
            db.collection("Terminal").add(hashMapOf(
                "Name" to "Terminal D",
                "Description" to "DFW International Terminal",
                "Center" to GeoPoint(32.8974, -97.0446)
            ))

            // 2. Parse routing.geojson for Nodes, Edges and Amenities
            val routingJson = assets.open("mapdata/routing.geojson").bufferedReader().use { it.readText() }
            val routingObj = JSONObject(routingJson)
            val routingFeatures = routingObj.getJSONArray("features")

            for (i in 0 until routingFeatures.length()) {
                val feature = routingFeatures.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val geom = feature.getJSONObject("geometry")
                val type = props.optString("type")
                val id = props.optString("id")

                if (type == "poi") {
                    val coords = geom.getJSONArray("coordinates")
                    db.collection("MapNode").document(id).set(hashMapOf(
                        "id" to id,
                        "terminalId" to "Terminal D",
                        "type" to "poi",
                        "name" to props.optString("name"),
                        "level" to props.optInt("level"),
                        "gender" to props.optString("gender"),
                        "coordinates" to GeoPoint(coords.getDouble(1), coords.getDouble(0))
                    ))
                } else if (type == "path") {
                    val coordsArray = geom.getJSONArray("coordinates")
                    val pathPoints = mutableListOf<GeoPoint>()
                    for (j in 0 until coordsArray.length()) {
                        val pt = coordsArray.getJSONArray(j)
                        pathPoints.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                    }
                    db.collection("PathEdge").document(id).set(hashMapOf(
                        "id" to id,
                        "type" to "path",
                        "name" to props.optString("name"),
                        "coordinates" to pathPoints,
                        "isOpen" to true
                    ))
                }
            }


            // 3. Parse floorplan.geojson for MapBackgrounds (Polygons)
            val floorplanJson = assets.open("mapdata/floorplan.geojson").bufferedReader().use { it.readText() }
            val floorplanObj = JSONObject(floorplanJson)
            val floorplanFeatures = floorplanObj.getJSONArray("features")

            for (i in 0 until floorplanFeatures.length()) {
                val feature = floorplanFeatures.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val geom = feature.getJSONObject("geometry")
                val id = props.optString("id")

                if (geom.getString("type") == "Polygon") {
                    val rings = geom.getJSONArray("coordinates")
                    val exteriorRing = rings.getJSONArray(0)
                    val points = mutableListOf<GeoPoint>()
                    for (j in 0 until exteriorRing.length()) {
                        val pt = exteriorRing.getJSONArray(j)
                        points.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                    }
                    
                    db.collection("MapBackground").document(id).set(hashMapOf(
                        "id" to id,
                        "type" to props.optString("type"),
                        "name" to props.optString("name"),
                        "level" to props.optInt("level"),
                        "gender" to props.optString("gender"),
                        "coordinates" to points

                    ))
                }
            }

            //4. Add remaining Collections
            // AmenityUnit
            db.collection("AmenityUnit").add(hashMapOf(
                "StatusID" to "S1",
                "AmenityID" to "A1",
                "SensorID" to "SN1",
                "UnitStatus" to "Open",
                "LastUpdated" to System.currentTimeMillis()
            ))

            // AmenitySchedule
            db.collection("AmenitySchedule").add(hashMapOf(
                "AmenityScheduleID" to "AS1",
                "AmenityID" to "A1",
                "OperatingHours" to "24/7"
            ))

            // Sensor
            db.collection("Sensor").add(hashMapOf(
                "SensorID" to "SN1",
                "SensorType" to "Occupancy",
                "Status" to "Active",
                "LastUpdate" to System.currentTimeMillis()
            ))

            // User
            db.collection("User").add(hashMapOf(
                "UserID" to "U1",
                "Email" to "example@email.com",
                "Username" to "testUser",
                "CreatedAt" to System.currentTimeMillis()
            ))

            // UserReports
            db.collection("UserReports").add(hashMapOf(
                "ReportID" to "R1",
                "UserID" to "U1",
                "NodeID" to "N1",
                "Description" to "Broken restroom",
                "ReportType" to "Maintenance"
            ))

            Log.d("FirestoreDB", "GeoJSON Seeding Complete!")
            onComplete()

        } catch (e: Exception) {
            Log.e("FirestoreDB", "GeoJSON Seed Failed", e)
        }
    }

}
