package com.example.dfwflightcompanion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.example.dfwflightcompanion.ui.theme.DFWFlightCompanionTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("FirestoreTest", "MainActivity onCreate started")
        
        enableEdgeToEdge()
        setContent {
            DFWFlightCompanionTheme {
                var statusMessage by remember { mutableStateOf("Ready") }

                // SET TO TRUE TO WIPE AND RE-POPULATE FROM GEOJSON, THEN SET FALSE
                val shouldInitializeDb = true

                LaunchedEffect(Unit) {
                    val db = FirebaseFirestore.getInstance()

                    if (shouldInitializeDb) {
                        statusMessage = "Wiping and Initializing from GeoJSON..."
                        wipeAndSeedFirestore(db) {
                            statusMessage = "Database Population Complete!"
                        }
                    }
                }

                BottomNavigationBar()
            }
        }
    }

    private fun wipeAndSeedFirestore(db: FirebaseFirestore, onComplete: () -> Unit) {
        val collections = listOf(
            "Terminal", "MapNode", "PathEdge", "Amenity", 
            "AmenityUnit", "AmenitySchedule", "Sensor", "User", "UserReports", "MapFeature"
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

    private fun seedFromGeoJson(db: FirebaseFirestore, onComplete: () -> Unit) {
        try {
            Log.d("FirestoreTest", "Starting GeoJSON Seed...")
            
            // 1. Seed Terminal Root
            db.collection("Terminal").add(hashMapOf(
                "Name" to "Terminal D",
                "Description" to "DFW International Terminal",
                "Center" to GeoPoint(32.8974, -97.0446)
            ))

            // 2. Parse routing.geojson for Nodes and Edges
            val routingJson = assets.open("routing.geojson").bufferedReader().use { it.readText() }
            val routingObj = JSONObject(routingJson)
            val routingFeatures = routingObj.getJSONArray("features")

            for (i in 0 until routingFeatures.length()) {
                val feature = routingFeatures.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val geom = feature.getJSONObject("geometry")
                val type = props.optString("type")

                if (type == "poi") {
                    val coords = geom.getJSONArray("coordinates")
                    db.collection("MapNode").add(hashMapOf(
                        "id" to props.optString("id"),
                        "type" to "poi",
                        "name" to props.optString("name"),
                        "level" to props.optInt("level"),
                        "weight" to props.optDouble("weight"),
                        "coordinates" to GeoPoint(coords.getDouble(1), coords.getDouble(0))
                    ))
                } else if (type == "path") {
                    val coordsArray = geom.getJSONArray("coordinates")
                    val pathPoints = mutableListOf<GeoPoint>()
                    for (j in 0 until coordsArray.length()) {
                        val pt = coordsArray.getJSONArray(j)
                        pathPoints.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                    }
                    db.collection("PathEdge").add(hashMapOf(
                        "id" to props.optString("id"),
                        "type" to "path",
                        "name" to props.optString("name"),
                        "level" to props.optInt("level"),
                        "weight" to props.optDouble("weight"),
                        "coordinates" to pathPoints
                    ))
                }
            }

            // 3. Parse floorplan.geojson for MapFeatures (Polygons)
            val floorplanJson = assets.open("floorplan.geojson").bufferedReader().use { it.readText() }
            val floorplanObj = JSONObject(floorplanJson)
            val floorplanFeatures = floorplanObj.getJSONArray("features")

            for (i in 0 until floorplanFeatures.length()) {
                val feature = floorplanFeatures.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val geom = feature.getJSONObject("geometry")
                
                if (geom.getString("type") == "Polygon") {
                    val rings = geom.getJSONArray("coordinates")
                    val exteriorRing = rings.getJSONArray(0)
                    val points = mutableListOf<GeoPoint>()
                    for (j in 0 until exteriorRing.length()) {
                        val pt = exteriorRing.getJSONArray(j)
                        points.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                    }
                    
                    db.collection("MapFeature").add(hashMapOf(
                        "id" to props.optString("id"),
                        "type" to props.optString("type"),
                        "name" to props.optString("name"),
                        "level" to props.optInt("level"),
                        "coordinates" to points

                    ))
                }
            }

            Log.d("FirestoreTest", "GeoJSON Seeding Complete!")
            onComplete()

        } catch (e: Exception) {
            Log.e("FirestoreTest", "GeoJSON Seed Failed", e)
        }
    }
}
