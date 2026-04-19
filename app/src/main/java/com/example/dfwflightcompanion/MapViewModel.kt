package com.example.dfwflightcompanion

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MapViewModel : ViewModel() {

    var selectedAmenityId by mutableStateOf<String?>(null)
        private set
    var amenities by mutableStateOf<List<Amenity>>(emptyList())
        private set

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
                isAccessible = detail.isAccessible,
                nodeId = detail.nodeId
            )
        }
    }
}