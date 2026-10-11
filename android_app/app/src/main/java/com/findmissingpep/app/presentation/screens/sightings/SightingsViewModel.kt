package com.findmissingpep.app.presentation.screens.sightings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.domain.model.SightingStatus
import com.findmissingpep.app.domain.repository.SightingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SightingsFeedState(
    val sightings: List<Sighting> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: SightingStatus? = null,
    val error: String? = null
)

@HiltViewModel
class SightingsViewModel @Inject constructor(
    private val sightingRepository: SightingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SightingsFeedState())
    val uiState: StateFlow<SightingsFeedState> = _uiState.asStateFlow()

    init {
        // Observe Room cached sightings
        viewModelScope.launch {
            sightingRepository.observeSightings().collect { cached ->
                _uiState.update { current ->
                    current.copy(
                        sightings = if (current.selectedFilter != null) {
                            cached.filter { it.status == current.selectedFilter }
                        } else {
                            cached
                        }
                    )
                }
            }
        }
        refreshSightings()
    }

    fun refreshSightings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            sightingRepository.fetchSightings(
                statusFilter = _uiState.value.selectedFilter?.name
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.localizedMessage) }
                }
            )
        }
    }

    fun setFilter(status: SightingStatus?) {
        _uiState.update { it.copy(selectedFilter = status) }
        refreshSightings()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
