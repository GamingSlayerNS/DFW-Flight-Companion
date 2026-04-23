package com.example.dfwflightcompanion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DisabilityProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DisabilityProfileRepository(app.applicationContext)

    val profile: StateFlow<DisabilityProfile?> = repo.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(profile: DisabilityProfile) {
        viewModelScope.launch { repo.save(profile) }
    }

    fun delete() {
        viewModelScope.launch { repo.delete() }
    }
}