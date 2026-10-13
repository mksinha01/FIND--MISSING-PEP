package com.findmissingpep.app.presentation.screens.cases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.CaseStatus
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.domain.model.Photo
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.domain.repository.CaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CasesUiState(
    val cases: List<MissingCase> = emptyList(),
    val isLoading: Boolean = false,
    val selectedStatusFilter: CaseStatus? = null,
    val error: String? = null
)

data class CaseDetailUiState(
    val case: MissingCase? = null,
    val sightings: List<Sighting> = emptyList(),
    val isLoading: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val isClosingCase: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class CasesViewModel @Inject constructor(
    private val caseRepository: CaseRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(CasesUiState())
    val listState: StateFlow<CasesUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(CaseDetailUiState())
    val detailState: StateFlow<CaseDetailUiState> = _detailState.asStateFlow()

    init {
        // Observe Room cached cases
        viewModelScope.launch {
            caseRepository.observeCases().collect { cached ->
                _listState.update { current ->
                    current.copy(
                        cases = if (current.selectedStatusFilter != null) {
                            cached.filter { it.status == current.selectedStatusFilter }
                        } else {
                            cached
                        }
                    )
                }
            }
        }
        refreshCases()
    }

    fun refreshCases() {
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true, error = null) }
            caseRepository.fetchMyCases(
                status = _listState.value.selectedStatusFilter?.value
            ).fold(
                onSuccess = { paginated ->
                    _listState.update { it.copy(isLoading = false) }
                },
                onFailure = { err ->
                    _listState.update { it.copy(isLoading = false, error = err.localizedMessage) }
                }
            )
        }
    }

    fun setStatusFilter(status: CaseStatus?) {
        _listState.update { it.copy(selectedStatusFilter = status) }
        refreshCases()
    }

    fun loadCaseDetail(caseId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, error = null) }
            val caseResult = caseRepository.getCaseById(caseId)
            val sightingsResult = caseRepository.getCaseSightings(caseId)

            caseResult.fold(
                onSuccess = { case ->
                    _detailState.update {
                        it.copy(
                            case = case,
                            sightings = sightingsResult.getOrDefault(emptyList()),
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _detailState.update {
                        it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to load case details")
                    }
                }
            )
        }
    }

    fun uploadAdditionalPhoto(caseId: String, photoFile: File) {
        viewModelScope.launch {
            _detailState.update { it.copy(isUploadingPhoto = true, error = null) }
            caseRepository.uploadPhoto(caseId, photoFile).fold(
                onSuccess = { newPhoto ->
                    _detailState.update { current ->
                        val updatedCase = current.case?.let { c ->
                            c.copy(photos = c.photos + newPhoto)
                        }
                        current.copy(
                            case = updatedCase,
                            isUploadingPhoto = false,
                            successMessage = "Photo uploaded and queued for visual verification"
                        )
                    }
                },
                onFailure = { err ->
                    _detailState.update {
                        it.copy(isUploadingPhoto = false, error = err.localizedMessage)
                    }
                }
            )
        }
    }

    fun closeCase(caseId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isClosingCase = true, error = null) }
            caseRepository.closeCase(caseId).fold(
                onSuccess = {
                    _detailState.update {
                        it.copy(
                            isClosingCase = false,
                            successMessage = "Case closed successfully"
                        )
                    }
                    loadCaseDetail(caseId)
                },
                onFailure = { err ->
                    _detailState.update {
                        it.copy(isClosingCase = false, error = err.localizedMessage)
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _detailState.update { it.copy(error = null, successMessage = null) }
        _listState.update { it.copy(error = null) }
    }
}
