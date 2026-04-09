package com.tms.banking.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tms.banking.AppContainer
import com.tms.banking.data.local.entity.AccountEntity
import com.tms.banking.data.local.entity.CategoryEntity
import com.tms.banking.data.local.entity.TransactionEntity
import com.tms.banking.data.remote.TmsApi
import com.tms.banking.data.repository.AccountRepository
import com.tms.banking.data.repository.CategoryRepository
import com.tms.banking.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val totalWealthAed: Double = 0.0,
    val showInAed: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val backendConfigured: Boolean = false,
    val searchQuery: String = ""
)

class HomeViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var accountRepo: AccountRepository? = null
    private var transactionRepo: TransactionRepository? = null
    private var categoryRepo: CategoryRepository? = null

    init {
        viewModelScope.launch {
            val url = container.backendUrlFlow.first()
            if (url.isNotBlank()) {
                initRepositories(url)
            }
            container.backendUrlFlow.collectLatest { newUrl ->
                if (newUrl.isNotBlank()) {
                    initRepositories(newUrl)
                } else {
                    _uiState.value = _uiState.value.copy(backendConfigured = false)
                }
            }
        }
    }

    private fun initRepositories(url: String) {
        try {
            val api = container.buildApi(url)
            accountRepo = container.accountRepository(api)
            transactionRepo = container.transactionRepository(api)
            categoryRepo = container.categoryRepository(api)
            _uiState.value = _uiState.value.copy(backendConfigured = true)
            observeData()
            refresh()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(backendConfigured = false, error = "Invalid backend URL")
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                accountRepo!!.observeAccounts(),
                transactionRepo!!.observeTransactions(limit = 5000),
                categoryRepo!!.observeCategories()
            ) { accounts, transactions, categories ->
                Triple(accounts, transactions, categories)
            }.collectLatest { (accounts, transactions, categories) ->
                val total = accounts.filter { it.currency == "AED" }.sumOf { it.balance }
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    transactions = transactions,
                    categories = categories,
                    totalWealthAed = total
                )
            }
        }
    }

    fun refresh() {
        val accRepo = accountRepo ?: return
        val txRepo = transactionRepo ?: return
        val catRepo = categoryRepo ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val r1 = accRepo.refreshAccounts()
            val r2 = txRepo.refreshTransactions(limit = 5000)
            val r3 = catRepo.refreshCategories()
            val err = listOf(r1, r2, r3).firstOrNull { it.isFailure }?.exceptionOrNull()?.message
            _uiState.value = _uiState.value.copy(isLoading = false, error = err)
        }
    }

    fun toggleShowInAed() {
        _uiState.value = _uiState.value.copy(showInAed = !_uiState.value.showInAed)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun updateCategory(txnId: Int, categoryId: Int) {
        val txRepo = transactionRepo ?: return
        viewModelScope.launch {
            txRepo.updateTransactionCategory(txnId, categoryId)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(container) as T
    }
}
