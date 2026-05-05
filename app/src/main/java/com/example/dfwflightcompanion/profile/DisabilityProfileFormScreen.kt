package com.example.dfwflightcompanion.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisabilityProfileFormScreen(
    navController: NavHostController,
    disabilityProfileViewModel: DisabilityProfileViewModel
) {
    val existing by disabilityProfileViewModel.profile.collectAsState()
    var form by remember(existing) { mutableStateOf(existing ?: DisabilityProfile()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Create Profile" else "Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader("Mobility")
            CheckboxField("Uses a wheelchair", form.usesWheelchair) {
                form = form.copy(usesWheelchair = it)
            }
            CheckboxField("Avoid stairs (prefer elevators)", form.avoidStairs) {
                form = form.copy(avoidStairs = it)
            }

            SectionHeader("Sensory")
            CheckboxField("Visual impairment", form.hasVisualImpairment) {
                form = form.copy(hasVisualImpairment = it)
            }
            CheckboxField("Hearing impairment", form.hasHearingImpairment) {
                form = form.copy(hasHearingImpairment = it)
            }

            SectionHeader("Restrooms")
            CheckboxField("Requires mobility-accessible restroom", form.requiresAccessibleRestroom) {
                form = form.copy(requiresAccessibleRestroom = it)
            }
            CheckboxField("Prefers family restroom", form.prefersFamilyRestroom) {
                form = form.copy(prefersFamilyRestroom = it)
            }
            DropdownField(
                label = "Gender preference",
                value = form.restroomGenderPreference,
                options = RestroomPreference.entries,
                onChange = { form = form.copy(restroomGenderPreference = it) }
            )

            SectionHeader("Route priority")
            DropdownField(
                label = "Primary priority",
                value = form.routePriority,
                options = RoutePriority.entries,
                onChange = { form = form.copy(routePriority = it) }
            )

            OutlinedTextField(
                value = form.notes,
                onValueChange = { form = form.copy(notes = it) },
                label = { Text("Additional notes (optional)") },
                supportingText = {
                    Text("Notes are saved with your profile but won't affect navigation or recommendations.")
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    disabilityProfileViewModel.save(form)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (existing == null) "Create Profile" else "Save Changes") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun CheckboxField(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> DropdownField(
    label: String,
    value: T,
    options: List<T>,
    onChange: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value.name.lowercase().replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.name.lowercase().replace('_', ' ')) },
                    onClick = { onChange(opt); expanded = false }
                )
            }
        }
    }
}