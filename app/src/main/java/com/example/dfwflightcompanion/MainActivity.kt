package com.example.dfwflightcompanion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.add
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.dfwflightcompanion.ui.theme.DFWFlightCompanionTheme
import androidx.compose.ui.geometry.isEmpty
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DFWFlightCompanionTheme {
                var terminalName by remember { mutableStateOf("Loading...") }
                var terminalDesc by remember { mutableStateOf("Loading...") }

                // SET TO TRUE TO POPULATE DATABASE, THEN SET FALSE TO SAVE COSTS
                val shouldInitializeDb = false

                LaunchedEffect(Unit) {
                    val db = FirebaseFirestore.getInstance()

                    if (shouldInitializeDb) {
                        Log.d("FirestoreTest", "Initializing database...")
                        initializeFirestore()
                    }

                    // Fetch data from Terminal collection to verify
                    db.collection("Terminal")
                        .get()
                        .addOnSuccessListener { result ->
                            if (!result.isEmpty) {
                                // Grab the first one found
                                val document = result.documents[0]
                                terminalName = document.getString("Name") ?: "No Name field"
                                terminalDesc =
                                    document.getString("Description") ?: "No Description field"
                            } else {
                                terminalName = "No documents found"
                                terminalDesc = ""
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("FirestoreTest", "Error getting documents: ", exception)
                            terminalName = "Error: ${exception.message}"
                        }
                }

                BottomNavigationBar()
            }
        }
    }

    /**
     * Seeds the Firestore database with sample data using Auto-Generated IDs.
     */
    private fun initializeFirestore() {
        val db = FirebaseFirestore.getInstance()

        // Terminal
        val terminal = hashMapOf(
            "ID" to "T1",
            "Name" to "Terminal D",
            "Description" to "DFW International Terminal"
        )
        db.collection("Terminal").add(terminal)

        // MapNode
        val mapNode = hashMapOf(
            "NodeID" to "N1",
            "TerminalID" to "T1",
            "XCoordinate" to 0,
            "YCoordinate" to 0,
            "FloorLevel" to 3,
            "NodeType" to "Floor"
        )
        db.collection("MapNode").add(mapNode)

        // PathEdge
        val pathEdge = hashMapOf(
            "EdgeID" to "E1",
            "StartNode" to "N1",
            "EndNode" to "N2",
            "Distance" to 10,
            "IsOpen" to true
        )
        db.collection("PathEdge").add(pathEdge)

        // Amenity
        val amenity = hashMapOf(
            "AmenityID" to "A1",
            "Name" to "Restroom",
            "NodeID" to "N1",
            "AmenityType" to "Restroom",
            "IsAccessible" to true
        )
        db.collection("Amenity").add(amenity)

        // AmenityUnit
        val amenityUnit = hashMapOf(
            "StatusID" to "S1",
            "AmenityID" to "A1",
            "SensorID" to "SN1",
            "Congestion" to "Low",
            "UnitStatus" to "Open",
            "LastUpdated" to System.currentTimeMillis()
        )
        db.collection("AmenityUnit").add(amenityUnit)

        // AmenitySchedule
        val amenitySchedule = hashMapOf(
            "AmenityScheduleID" to "AS1",
            "AmenityID" to "A1",
            "OperatingHours" to "24/7"
        )
        db.collection("AmenitySchedule").add(amenitySchedule)

        // Sensor
        val sensor = hashMapOf(
            "SensorID" to "SN1",
            "SensorType" to "Occupancy",
            "Status" to "Active",
            "LastUpdate" to System.currentTimeMillis()
        )
        db.collection("Sensor").add(sensor)

        // User
        val user = hashMapOf(
            "UserID" to "U1",
            "Email" to "example@email.com",
            "Username" to "testUser",
            "CreatedAt" to System.currentTimeMillis()
        )
        db.collection("User").add(user)

        // UserReports
        val report = hashMapOf(
            "ReportID" to "R1",
            "UserID" to "U1",
            "NodeID" to "N1",
            "Description" to "Broken restroom",
            "ReportType" to "Maintenance"
        )
        db.collection("UserReports").add(report)
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
