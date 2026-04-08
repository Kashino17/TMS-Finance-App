package com.tms.banking.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tms.banking.AppContainer
import com.tms.banking.data.remote.dto.SyncStatusDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SettingsUiState(
    val backendUrl: String = "",
    val urlInput: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val syncStatus: List<SyncStatusDto> = emptyList(),
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val notificationListenerEnabled: Boolean = false
)

enum class ConnectionStatus {
    Idle, Testing, Success, Failure
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.backendUrlFlow.collectLatest { url ->
                _uiState.value = _uiState.value.copy(
                    backendUrl = url,
                    urlInput = url
                )
            }
        }
    }

    fun setUrlInput(value: String) {
        _uiState.value = _uiState.value.copy(urlInput = value, connectionStatus = ConnectionStatus.Idle)
    }

    fun saveUrl() {
        viewModelScope.launch {
            container.saveBackendUrl(_uiState.value.urlInput)
        }
    }

    fun testConnection() {
        val url = _uiState.value.urlInput.ifBlank { _uiState.value.backendUrl }
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Failure)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Testing)
            try {
                val api = container.buildApi(url)
                val response = api.health()
                if (response.isSuccessful) {
                    container.saveBackendUrl(url)
                    _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Success)
                } else {
                    _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Failure)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Failure)
            }
        }
    }

    fun triggerSync() {
        val url = _uiState.value.backendUrl
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)
            try {
                val api = container.buildApi(url)
                val response = api.triggerSync()
                if (response.isSuccessful) {
                    val statuses = api.getSyncStatus()
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncStatus = statuses,
                        syncMessage = "Sync triggered successfully"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncMessage = "Sync trigger failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun loadSyncStatus() {
        val url = _uiState.value.backendUrl
        if (url.isBlank()) return
        viewModelScope.launch {
            try {
                val api = container.buildApi(url)
                val statuses = api.getSyncStatus()
                _uiState.value = _uiState.value.copy(syncStatus = statuses)
            } catch (_: Exception) {}
        }
    }

    fun setNotificationListenerEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationListenerEnabled = enabled)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container) as T
    }
}
