package com.tms.banking.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tms.banking.AppContainer
import com.tms.banking.data.local.entity.AccountEntity
import com.tms.banking.data.local.entity.CategoryEntity
import com.tms.banking.data.repository.AccountRepository
import com.tms.banking.data.repository.CategoryRepository
import com.tms.banking.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AddTransactionUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val amount: String = "",
    val currency: String = "AED",
    val selectedAccountId: Int? = null,
    val selectedCategoryId: Int? = null,
    val description: String = "",
    val date: String = todayDateString(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

private fun todayDateString(): String {
    val cal = java.util.Calendar.getInstance()
    return String.format(
        "%04d-%02d-%02d",
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

val supportedCurrencies = listOf("AED", "EUR", "HKD", "USD", "GBP")

class AddTransactionViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    private var accountRepo: AccountRepository? = null
    private var categoryRepo: CategoryRepository? = null
    private var transactionRepo: TransactionRepository? = null

    init {
        viewModelScope.launch {
            val url = container.backendUrlFlow.first()
            if (url.isNotBlank()) {
                initRepositories(url)
            }
        }
    }

    private fun initRepositories(url: String) {
        val api = container.buildApi(url)
        accountRepo = container.accountRepository(api)
        categoryRepo = container.categoryRepository(api)
        transactionRepo = container.transactionRepository(api)
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val accounts = accountRepo?.getAccounts() ?: emptyList()
            val categories = categoryRepo?.getCategories() ?: emptyList()
            _uiState.value = _uiState.value.copy(
                accounts = accounts,
                categories = categories,
                selectedAccountId = accounts.firstOrNull()?.id,
                selectedCategoryId = categories.firstOrNull()?.id
            )
        }
    }

    fun setAmount(value: String) {
        _uiState.value = _uiState.value.copy(amount = value)
    }

    fun setCurrency(value: String) {
        _uiState.value = _uiState.value.copy(currency = value)
    }

    fun setAccount(id: Int) {
        _uiState.value = _uiState.value.copy(selectedAccountId = id)
    }

    fun setCategory(id: Int) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = id)
    }

    fun setDescription(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun setDate(value: String) {
        _uiState.value = _uiState.value.copy(date = value)
    }

    fun save() {
        val state = _uiState.value
        val amount = state.amount.toDoubleOrNull()
        if (amount == null || amount == 0.0) {
            _uiState.value = state.copy(error = "Please enter a valid amount")
            return
        }
        if (state.selectedAccountId == null) {
            _uiState.value = state.copy(error = "Please select an account")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val url = container.backendUrlFlow.first()
                if (url.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Backend URL not configured"
                    )
                    return@launch
                }
                val api = container.buildApi(url)
                // Manual transaction is a negative spend (expense by default)
                // Posted to backend as a notification-style or via a direct endpoint if available
                // For now we create via the notification endpoint with encoded data
                val negAmount = if (amount > 0) -amount else amount
                val notifText = "Manual: ${state.description.ifBlank { "Transaction" }} ${state.currency} $negAmount"
                api.postNotification(
                    com.tms.banking.data.remote.dto.NotificationDto(
                        bankPackage = "com.tms.banking.manual",
                        title = "Manual Transaction",
                        text = notifText,
                        timestamp = System.currentTimeMillis()
                    )
                )
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to save transaction"
                )
            }
        }
    }

    fun resetSaved() {
        _uiState.value = _uiState.value.copy(
            isSaved = false,
            amount = "",
            description = "",
            date = todayDateString()
        )
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddTransactionViewModel(container) as T
    }
}
