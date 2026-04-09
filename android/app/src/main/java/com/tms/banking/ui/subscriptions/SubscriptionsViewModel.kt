package com.tms.banking.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tms.banking.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SubscriptionsUiState(
    val personal: List<SubscriptionData> = emptyList(),
    val business: List<SubscriptionData> = emptyList(),
    val isLoading: Boolean = false,
)

class SubscriptionsViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val url = container.backendUrlFlow.first()
            if (url.isBlank()) return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val api = container.buildApi(url)
                val all = api.getRecurring()
                val subs = all.map { r ->
                    SubscriptionData(
                        merchant = r["merchant"] as? String ?: "",
                        avgAmount = r["avg_amount"] as? Double ?: 0.0,
                        frequency = r["frequency"] as? String ?: "monthly",
                        lastDate = r["last_date"] as? String ?: "",
                        nextEstimated = r["next_estimated"] as? String ?: "",
                        category = r["category"] as? String,
                        categoryType = r["category_type"] as? String ?: "personal",
                        count = (r["count"] as? Double)?.toInt() ?: 0,
                        status = r["status"] as? String ?: "unknown",
                        cancelUrl = r["cancel_url"] as? String,
                    )
                }
                _uiState.value = SubscriptionsUiState(
                    personal = subs.filter { it.categoryType == "personal" },
                    business = subs.filter { it.categoryType == "business" },
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SubscriptionsViewModel(container) as T
    }
}
