package com.tms.banking.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tms.banking.AppContainer
import com.tms.banking.data.local.entity.CategoryEntity
import com.tms.banking.data.local.entity.TransactionEntity
import com.tms.banking.data.repository.CategoryRepository
import com.tms.banking.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CategorySpend(
    val category: CategoryEntity,
    val totalAed: Double,
    val count: Int
)

data class CategoriesUiState(
    val categorySpends: List<CategorySpend> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class CategoriesViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

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
        categoryRepo = container.categoryRepository(api)
        transactionRepo = container.transactionRepository(api)
        observeData()
    }

    private fun observeData() {
        val catRepo = categoryRepo ?: return
        val txRepo = transactionRepo ?: return

        viewModelScope.launch {
            combine(
                catRepo.observeCategories(),
                txRepo.observeTransactions(limit = 500)
            ) { categories, transactions ->
                Pair(categories, transactions)
            }.collectLatest { (categories, transactions) ->
                val spends = categories.map { cat ->
                    val catTxs = transactions.filter { it.categoryId == cat.id && it.amount < 0 }
                    CategorySpend(
                        category = cat,
                        totalAed = catTxs.sumOf { kotlin.math.abs(it.amountAed) },
                        count = catTxs.size
                    )
                }.filter { it.totalAed > 0 }
                    .sortedByDescending { it.totalAed }

                val selectedTxs = if (_uiState.value.selectedCategoryId != null) {
                    transactions.filter { it.categoryId == _uiState.value.selectedCategoryId }
                } else {
                    transactions
                }

                _uiState.value = _uiState.value.copy(
                    categorySpends = spends,
                    transactions = selectedTxs,
                    categories = categories
                )
            }
        }
    }

    fun selectCategory(categoryId: Int?) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
        observeData()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CategoriesViewModel(container) as T
    }
}
