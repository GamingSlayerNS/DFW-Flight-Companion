package com.example.dfwflightcompanion

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
class MapViewModel : ViewModel() {

    var selectedAmenityId by mutableStateOf<String?>(null)
        private set

    fun selectAmenity(id: String?) {
        selectedAmenityId = id
    }
}