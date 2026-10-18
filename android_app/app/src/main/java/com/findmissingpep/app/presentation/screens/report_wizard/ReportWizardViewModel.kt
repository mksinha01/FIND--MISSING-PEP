package com.findmissingpep.app.presentation.screens.report_wizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.FirValidation
import com.findmissingpep.app.domain.repository.CaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReportWizardState(
    val currentStep: Int = 0, // 0, 1, 2
    // Step 1: Demographics
    val fullName: String = "",
    val age: Int = 25,
    val gender: String = "",
    val heightCm: Int = 170,
    val description: String = "",
    val lastSeenLocation: String = "",
    val lastSeenTime: String = "",
    val contactInfo: String = "",
    // Step 2: FIR
    val firNumber: String = "",
    val firPoliceStation: String = "",
    val firDate: String = "",
    val firDocument: File? = null,
    val firDocumentUri: Uri? = null,
    val firValidation: FirValidation? = null,
    val isValidatingFir: Boolean = false,
    // Step 3: Photos
    val photoFiles: List<File> = emptyList(),
    val photoUris: List<Uri> = emptyList(),
    // Submission
    val isSubmitting: Boolean = false,
    val submittedCaseId: String? = null,
    val error: String? = null
)

@HiltViewModel
class ReportWizardViewModel @Inject constructor(
    private val caseRepository: CaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportWizardState())
    val uiState: StateFlow<ReportWizardState> = _uiState.asStateFlow()

    private var firValidationJob: Job? = null

    // ── Step navigation ──
    fun nextStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(2)) }
    }

    fun previousStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(0)) }
    }

    // ── Step 1 updates ──
    fun updateFullName(value: String) { _uiState.update { it.copy(fullName = value) } }
    fun updateAge(value: Int) { _uiState.update { it.copy(age = value) } }
    fun updateGender(value: String) { _uiState.update { it.copy(gender = value) } }
    fun updateHeightCm(value: Int) { _uiState.update { it.copy(heightCm = value) } }
    fun updateDescription(value: String) { _uiState.update { it.copy(description = value) } }
    fun updateLastSeenLocation(value: String) { _uiState.update { it.copy(lastSeenLocation = value) } }
    fun updateLastSeenTime(value: String) { _uiState.update { it.copy(lastSeenTime = value) } }
    fun updateContactInfo(value: String) { _uiState.update { it.copy(contactInfo = value) } }

    // ── Step 2 updates ──
    fun updateFirNumber(value: String) {
        _uiState.update { it.copy(firNumber = value, firValidation = null) }
        // Debounced FIR validation
        firValidationJob?.cancel()
        if (value.length >= 5) {
            firValidationJob = viewModelScope.launch {
                delay(500) // debounce
                _uiState.update { it.copy(isValidatingFir = true) }
                caseRepository.validateFir(value).fold(
                    onSuccess = { validation ->
                        _uiState.update { it.copy(firValidation = validation, isValidatingFir = false) }
                    },
                    onFailure = {
                        _uiState.update { it.copy(isValidatingFir = false) }
                    }
                )
            }
        }
    }

    fun updateFirPoliceStation(value: String) { _uiState.update { it.copy(firPoliceStation = value) } }
    fun updateFirDate(value: String) { _uiState.update { it.copy(firDate = value) } }
    fun setFirDocument(file: File?, uri: Uri?) {
        _uiState.update { it.copy(firDocument = file, firDocumentUri = uri) }
    }

    // ── Step 3 updates ──
    fun addPhotos(files: List<File>, uris: List<Uri>) {
        _uiState.update { state ->
            val currentPhotos = state.photoFiles.toMutableList()
            val currentUris = state.photoUris.toMutableList()
            currentPhotos.addAll(files.take(5 - currentPhotos.size))
            currentUris.addAll(uris.take(5 - currentUris.size))
            state.copy(photoFiles = currentPhotos, photoUris = currentUris)
        }
    }

    fun removePhoto(index: Int) {
        _uiState.update { state ->
            state.copy(
                photoFiles = state.photoFiles.toMutableList().apply { removeAt(index) },
                photoUris = state.photoUris.toMutableList().apply { removeAt(index) }
            )
        }
    }

    // ── Submit ──
    fun submitReport() {
        val state = _uiState.value
        if (state.photoFiles.isEmpty() || state.fullName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            caseRepository.submitReport(
                fullName = state.fullName,
                age = state.age,
                gender = state.gender.ifBlank { null },
                heightCm = state.heightCm,
                description = state.description.ifBlank { null },
                lastSeenLocation = state.lastSeenLocation.ifBlank { null },
                lastSeenTime = state.lastSeenTime.ifBlank { null },
                contactInfo = state.contactInfo.ifBlank { null },
                firNumber = state.firNumber.ifBlank { null },
                firPoliceStation = state.firPoliceStation.ifBlank { null },
                firDate = state.firDate.ifBlank { null },
                photos = state.photoFiles,
                firDocument = state.firDocument
            ).fold(
                onSuccess = { case ->
                    _uiState.update { it.copy(isSubmitting = false, submittedCaseId = case.id) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = e.localizedMessage ?: "Submission failed")
                    }
                }
            )
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun resetForm() {
        _uiState.value = ReportWizardState()
    }
}
