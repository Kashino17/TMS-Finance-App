package com.tms.banking.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tms.banking.AppContainer
import com.tms.banking.data.local.entity.AccountEntity
import com.tms.banking.data.local.entity.CategoryEntity
import com.tms.banking.data.local.entity.TransactionEntity
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

data class AccountDetailUiState(
    val account: AccountEntity? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val showInAed: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AccountDetailViewModel(
    private val container: AppContainer,
    private val accountId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()

    private var accountRepo: AccountRepository? = null
    private var transactionRepo: TransactionRepository? = null
    private var categoryRepo: CategoryRepository? = null

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
        transactionRepo = container.transactionRepository(api)
        categoryRepo = container.categoryRepository(api)
        observeData()
        refresh()
    }

    private fun observeData() {
        val txRepo = transactionRepo ?: return
        val catRepo = categoryRepo ?: return

        viewModelScope.launch {
            combine(
                txRepo.observeTransactionsByAccount(accountId, limit = 100),
                catRepo.observeCategories()
            ) { transactions, categories ->
                Pair(transactions, categories)
            }.collectLatest { (transactions, categories) ->
                _uiState.value = _uiState.value.copy(
                    transactions = transactions,
                    categories = categories
                )
            }
        }

        viewModelScope.launch {
            val acc = accountRepo?.getAccountById(accountId)
            _uiState.value = _uiState.value.copy(account = acc)
        }
    }

    fun refresh() {
        val txRepo = transactionRepo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = txRepo.refreshTransactions(accountId, limit = 100)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = result.exceptionOrNull()?.message
            )
            val acc = accountRepo?.getAccountById(accountId)
            _uiState.value = _uiState.value.copy(account = acc)
        }
    }

    fun toggleShowInAed() {
        _uiState.value = _uiState.value.copy(showInAed = !_uiState.value.showInAed)
    }

    fun updateCategory(transactionId: Int, categoryId: Int) {
        val txRepo = transactionRepo ?: return
        viewModelScope.launch {
            txRepo.updateTransactionCategory(transactionId, categoryId)
        }
    }

    class Factory(private val container: AppContainer, private val accountId: Int) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountDetailViewModel(container, accountId) as T
    }
}
