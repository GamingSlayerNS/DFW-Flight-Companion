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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.dfwflightcompanion.ui.theme.DFWFlightCompanionTheme
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DFWFlightCompanionTheme {
                var terminalName by remember { mutableStateOf("Loading...") }
                var terminalDesc by remember { mutableStateOf("Loading...") }


                LaunchedEffect(Unit) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("Terminal")
                        .get()
                        .addOnSuccessListener { result ->
                            if (!result.isEmpty) {
                                // Getting the first document for testing
                                val document = result.documents[0]
                                terminalName = document.getString("name") ?: "No name field"
                                terminalDesc = document.getString("desc") ?: "No desc field"
                            } else {
                                terminalName = "No documents found"
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("FirestoreTest", "Error getting documents: ", exception)
                            terminalName = "Error: ${exception.message}"
                        }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = terminalName,
                        desc = terminalDesc,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
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
