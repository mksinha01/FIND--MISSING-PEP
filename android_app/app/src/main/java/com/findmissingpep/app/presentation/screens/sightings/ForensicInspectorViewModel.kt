package com.findmissingpep.app.presentation.screens.sightings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.domain.repository.CaseRepository
import com.findmissingpep.app.domain.repository.SightingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForensicInspectorState(
    val sighting: Sighting? = null,
    val associatedCase: MissingCase? = null,
    val isLoading: Boolean = false,
    val isActing: Boolean = false,
    val actionSuccessMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class ForensicInspectorViewModel @Inject constructor(
    private val sightingRepository: SightingRepository,
    private val caseRepository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForensicInspectorState())
    val uiState: StateFlow<ForensicInspectorState> = _uiState.asStateFlow()

    fun loadSighting(sightingId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            sightingRepository.getSightingById(sightingId).fold(
                onSuccess = { sighting ->
                    _uiState.update { it.copy(sighting = sighting, isLoading = false) }
                    // Also load associated case details for the reference photo
                    caseRepository.getCaseById(sighting.personId).onSuccess { c ->
                        _uiState.update { it.copy(associatedCase = c) }
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, error = err.localizedMessage ?: "Failed to load sighting")
                    }
                }
            )
        }
    }

    fun confirmSighting(reviewNotes: String) {
        val currentSighting = _uiState.value.sighting ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, error = null) }
            sightingRepository.confirmSighting(currentSighting.id, reviewNotes).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            sighting = updated,
                            isActing = false,
                            actionSuccessMessage = "Match confirmed! Case status marked as FOUND across city nodes."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isActing = false, error = err.localizedMessage)
                    }
                }
            )
        }
    }

    fun rejectSighting(reviewNotes: String) {
        val currentSighting = _uiState.value.sighting ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, error = null) }
            sightingRepository.rejectSighting(currentSighting.id, reviewNotes).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            sighting = updated,
                            isActing = false,
                            actionSuccessMessage = "False alarm rejected and logged."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isActing = false, error = err.localizedMessage)
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, actionSuccessMessage = null) }
    }
}
