package com.example.dfwflightcompanion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.dfwflightcompanion.ui.theme.DFWFlightCompanionTheme
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("FirestoreTest", "MainActivity onCreate started")
        
        enableEdgeToEdge()
        setContent {
            DFWFlightCompanionTheme {
                var terminalName by remember { mutableStateOf("Loading...") }
                var terminalDesc by remember { mutableStateOf("Loading...") }

                // SET TO TRUE TO WIPE AND RE-POPULATE, THEN SET FALSE TO SAVE COSTS
                val shouldInitializeDb = true 

                LaunchedEffect(Unit) {
                    Log.d("FirestoreTest", "LaunchedEffect triggered")
                    val db = FirebaseFirestore.getInstance()

                    if (shouldInitializeDb) {
                        Log.d("FirestoreTest", "Wiping and Initializing database...")
                        wipeAndSeedFirestore(db) {
                            fetchTerminalData(db) { name, desc ->
                                terminalName = name
                                terminalDesc = desc
                            }
                        }
                    } else {
                        fetchTerminalData(db) { name, desc ->
                            terminalName = name
                            terminalDesc = desc
                        }
                    }
                }

                BottomNavigationBar()
            }
        }
    }

    private fun fetchTerminalData(db: FirebaseFirestore, onResult: (String, String) -> Unit) {
        db.collection("Terminal")
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val document = result.documents[0]
                    val name = document.getString("Name") ?: "No Name field"
                    val desc = document.getString("Description") ?: "No Description field"
                    onResult(name, desc)
                    Log.d("FirestoreTest", "Data fetched: $name")
                } else {
                    onResult("No documents found", "")
                    Log.d("FirestoreTest", "No documents found in Terminal")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreTest", "Error getting documents: ", exception)
                onResult("Error", exception.message ?: "Unknown error")
            }
    }

    /**
     * Wipes specific collections and then seeds them with fresh data.
     */
    private fun wipeAndSeedFirestore(db: FirebaseFirestore, onComplete: () -> Unit) {
        val collections = listOf(
            "Terminal", "MapNode", "PathEdge", "Amenity", 
            "AmenityUnit", "AmenitySchedule", "Sensor", "User", "UserReports"
        )

        var collectionsProcessed = 0

        collections.forEach { collectionName ->
            db.collection(collectionName).get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val batch = db.batch()
                    task.result?.forEach { document ->
                        batch.delete(document.reference)
                    }
                    batch.commit().addOnCompleteListener {
                        collectionsProcessed++
                        if (collectionsProcessed == collections.size) {
                            seedData(db, onComplete)
                        }
                    }
                } else {
                    collectionsProcessed++
                    if (collectionsProcessed == collections.size) {
                        seedData(db, onComplete)
                    }
                }
            }
        }
    }

    private fun seedData(db: FirebaseFirestore, onComplete: () -> Unit) {
        Log.d("FirestoreTest", "Writing fresh documents with auto-generated IDs...")
        
        var addsDone = 0
        val totalToSeed = 9
        
        fun checkDone() {
            addsDone++
            if (addsDone == totalToSeed) {
                Log.d("FirestoreTest", "Seeding complete!")
                onComplete()
            }
        }

        // Terminal
        db.collection("Terminal").add(hashMapOf(
            "ID" to "T1",
            "Name" to "Terminal D",
            "Description" to "DFW International Terminal"
        )).addOnCompleteListener { checkDone() }

        // MapNode
        db.collection("MapNode").add(hashMapOf(
            "NodeID" to "N1",
            "TerminalID" to "T1",
            "XCoordinate" to 0,
            "YCoordinate" to 0,
            "FloorLevel" to 3,
            "NodeType" to "Floor"
        )).addOnCompleteListener { checkDone() }
        
        // PathEdge
        db.collection("PathEdge").add(hashMapOf(
            "EdgeID" to "E1",
            "StartNode" to "N1",
            "EndNode" to "N2",
            "Distance" to 10,
            "IsOpen" to true
        )).addOnCompleteListener { checkDone() }

        // Amenity
        db.collection("Amenity").add(hashMapOf(
            "AmenityID" to "A1",
            "Name" to "Restroom",
            "NodeID" to "N1",
            "AmenityType" to "Restroom",
            "IsAccessible" to true
        )).addOnCompleteListener { checkDone() }

        // AmenityUnit
        db.collection("AmenityUnit").add(hashMapOf(
            "StatusID" to "S1",
            "AmenityID" to "A1",
            "SensorID" to "SN1",
            "Congestion" to "Low",
            "UnitStatus" to "Open",
            "LastUpdated" to System.currentTimeMillis()
        )).addOnCompleteListener { checkDone() }

        // AmenitySchedule
        db.collection("AmenitySchedule").add(hashMapOf(
            "AmenityScheduleID" to "AS1",
            "AmenityID" to "A1",
            "OperatingHours" to "24/7"
        )).addOnCompleteListener { checkDone() }

        // Sensor
        db.collection("Sensor").add(hashMapOf(
            "SensorID" to "SN1",
            "SensorType" to "Occupancy",
            "Status" to "Active",
            "LastUpdate" to System.currentTimeMillis()
        )).addOnCompleteListener { checkDone() }

        // User
        db.collection("User").add(hashMapOf(
            "UserID" to "U1",
            "Email" to "example@email.com",
            "Username" to "testUser",
            "CreatedAt" to System.currentTimeMillis()
        )).addOnCompleteListener { checkDone() }

        // UserReports
        db.collection("UserReports").add(hashMapOf(
            "ReportID" to "R1",
            "UserID" to "U1",
            "NodeID" to "N1",
            "Description" to "Broken restroom",
            "ReportType" to "Maintenance"
        )).addOnCompleteListener { checkDone() }
    }
}

@Composable
fun Greeting(name: String, desc: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "Firestore Test:")
        Text(text = "Terminal Name: $name")
        Text(text = "Description: $desc")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DFWFlightCompanionTheme {
        Greeting("Sample Terminal", "Sample Description")
    }
}

@Preview(showBackground = true)
@Composable
fun NavigationBarPreview(){
    DFWFlightCompanionTheme {
        BottomNavigationBar()
    }
}
