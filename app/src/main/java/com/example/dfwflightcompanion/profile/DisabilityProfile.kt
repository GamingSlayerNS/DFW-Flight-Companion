package com.example.dfwflightcompanion.profile

data class DisabilityProfile(
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,

    // Mobility
    val usesWheelchair: Boolean = false,
    val avoidStairs: Boolean = false,

    // Sensory
    val hasVisualImpairment: Boolean = false,
    val hasHearingImpairment: Boolean = false,

    // Restroom preferences
    val requiresAccessibleRestroom: Boolean = false,
    val prefersFamilyRestroom: Boolean = false,
    val restroomGenderPreference: RestroomPreference = RestroomPreference.ANY,

    // Routing priority for tie-breaking
    val routePriority: RoutePriority = RoutePriority.BALANCED,

    val notes: String = ""
)

enum class RestroomPreference { ANY, MALE, FEMALE, FAMILY, NON_GENDERED }
enum class RoutePriority { FASTEST, LEAST_WALKING, LEAST_CROWDED, BALANCED }