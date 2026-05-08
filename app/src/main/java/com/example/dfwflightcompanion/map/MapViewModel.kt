package com.example.dfwflightcompanion.map

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.dfwflightcompanion.helpers.Amenity
import com.example.dfwflightcompanion.helpers.AmenityDetail
import org.maplibre.android.geometry.LatLng
import com.example.dfwflightcompanion.helpers.MapNode

class MapViewModel : ViewModel() {
    var selectedAmenityId by mutableStateOf<String?>(null)
        private set
    var amenities by mutableStateOf<List<Amenity>>(emptyList())
        private set

    var userLocation by mutableStateOf(LatLng(32.897546, -97.044471))
        private set

    var mapNodes by mutableStateOf<List<MapNode>>(emptyList())
        private set

    fun storeMapNodes(nodes: List<MapNode>) {
        mapNodes = nodes
    }

    init {
        // Firestore realtime listener
        FirebaseFirestore.getInstance()
            .collection("Amenity")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val details = snapshot.documents.map { doc ->
                        val map = doc.data ?: emptyMap<String, Any>()
                        AmenityDetail(
                            id = map["AmenityID"] as? String ?: "",
                            name = map["Name"] as? String ?: "Unknown",
                            type = map["AmenityType"] as? String ?: "",
                            subType = map["SubTypeName"] as? String ?: "",
                            congestion = map["Congestion"] as? String ?: "Low",
                            lastUpdated = (map["LastUpdated"] as? Number)?.toLong() ?: 0L,
                            isAccessible = map["IsAccessible"] as? Boolean ?: false,
                            nodeId = map["NodeID"] as? String ?: ""
                        )
                    }
                    storeAmenities(details)
                }
            }
    }

    fun selectAmenity(id: String?) {
        selectedAmenityId = id
    }
    fun storeAmenities(details: List<AmenityDetail>) {
        amenities = details.map { detail ->
            Amenity(
                id = detail.id,
                name = detail.name,
                type = detail.type,
                subType = detail.subType,
                congestion = detail.congestion,
                lastUpdated = detail.lastUpdated,
                isAccessible = detail.isAccessible,
                nodeId = detail.nodeId
            )
        }
    }

    fun updateLocation(newLocation: LatLng) {
        userLocation = newLocation
    }

    var autoStartNavigation by mutableStateOf(false)
        private set

    fun requestAutoStartNavigation(enabled: Boolean) {
        autoStartNavigation = enabled
    }
}