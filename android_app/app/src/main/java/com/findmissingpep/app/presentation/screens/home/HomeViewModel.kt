package com.findmissingpep.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.domain.repository.AuthRepository
import com.findmissingpep.app.domain.repository.CaseRepository
import com.findmissingpep.app.domain.repository.NotificationRepository
import com.findmissingpep.app.domain.repository.SightingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val activeCasesCount: Int = 0,
    val verifiedMatchesToday: Int = 0,
    val recentSightings: List<Sighting> = emptyList(),
    val myCases: List<MissingCase> = emptyList(),
    val unreadNotifications: Int = 0,
    val isOfficer: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val caseRepository: CaseRepository,
    private val sightingRepository: SightingRepository,
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
        observeUnreadCount()
    }

    private fun observeUnreadCount() {
        viewModelScope.launch {
            notificationRepository.unreadCount.collect { count ->
                _uiState.update { it.copy(unreadNotifications = count) }
            }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Fetch cases
            val casesResult = caseRepository.fetchMyCases(limit = 10)
            casesResult.onSuccess { paginated ->
                _uiState.update { state ->
                    state.copy(
                        myCases = paginated.items,
                        activeCasesCount = paginated.items.count {
                            it.status.value == "ACTIVE" || it.status.value == "PROCESSING" || it.status.value == "PENDING_FIR_REVIEW"
                        }
                    )
                }
            }

            // Fetch recent sightings
            val sightingsResult = sightingRepository.fetchSightings(limit = 5)
            sightingsResult.onSuccess { sightings ->
                _uiState.update { state ->
                    state.copy(
                        recentSightings = sightings,
                        verifiedMatchesToday = sightings.count { it.status.value == "CONFIRMED" }
                    )
                }
            }

            // Fetch unread count
            notificationRepository.fetchUnreadCount()

            // Check user role
            authRepository.currentUser.firstOrNull()?.let { user ->
                _uiState.update { it.copy(isOfficer = user.role.value != "citizen") }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
