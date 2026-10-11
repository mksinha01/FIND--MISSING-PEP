package com.findmissingpep.app.presentation.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.PersonTimeline
import com.findmissingpep.app.domain.model.TimelineEntry
import com.findmissingpep.app.domain.repository.CaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val timeline: PersonTimeline? = null,
    val selectedEntry: TimelineEntry? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BreadcrumbMapViewModel @Inject constructor(
    private val caseRepository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun loadTimeline(caseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            caseRepository.getCaseTimeline(caseId).fold(
                onSuccess = { timeline ->
                    _uiState.update {
                        it.copy(
                            timeline = timeline,
                            selectedEntry = timeline.entries.lastOrNull(),
                            isLoading = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, error = err.localizedMessage ?: "Failed to load movement trajectory")
                    }
                }
            )
        }
    }

    fun selectEntry(entry: TimelineEntry) {
        _uiState.update { it.copy(selectedEntry = entry) }
    }
}
