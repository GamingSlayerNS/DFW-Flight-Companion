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
                BottomNavigationBar()
            }
        }
    }
}
