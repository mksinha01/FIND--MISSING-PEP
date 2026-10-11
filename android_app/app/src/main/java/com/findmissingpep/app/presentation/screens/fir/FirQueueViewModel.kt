package com.findmissingpep.app.presentation.screens.fir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.domain.repository.CaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FirQueueState(
    val pendingCases: List<MissingCase> = emptyList(),
    val isLoading: Boolean = false,
    val isActing: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class FirQueueViewModel @Inject constructor(
    private val caseRepository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirQueueState())
    val uiState: StateFlow<FirQueueState> = _uiState.asStateFlow()

    init {
        refreshQueue()
    }

    fun refreshQueue() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            caseRepository.getPendingFirQueue().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(pendingCases = list, isLoading = false) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.localizedMessage) }
                }
            )
        }
    }

    fun verifyFir(caseId: String, notes: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, error = null) }
            caseRepository.verifyFir(caseId, notes).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActing = false,
                            successMessage = "FIR verified! ArcFace embeddings activated for live CCTV scanning."
                        )
                    }
                    refreshQueue()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isActing = false, error = err.localizedMessage) }
                }
            )
        }
    }

    fun rejectFir(caseId: String, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActing = true, error = null) }
            caseRepository.rejectFir(caseId, reason).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActing = false,
                            successMessage = "FIR rejected with feedback sent to citizen reporter."
                        )
                    }
                    refreshQueue()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isActing = false, error = err.localizedMessage) }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
