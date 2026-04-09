package com.tms.banking.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tms.banking.AppContainer
import com.tms.banking.data.local.entity.CategoryEntity
import com.tms.banking.data.local.entity.TransactionEntity
import com.tms.banking.data.remote.TmsApi
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

data class MonthlySummaryEntry(
    val month: String,
    val income: Double,
    val expenses: Double,
    val net: Double
)

data class RecurringEntry(
    val merchant: String,
    val avgAmount: Double,
    val frequency: String,
    val lastDate: String,
    val nextEstimated: String?,
    val category: String?,
    val count: Int
)

data class BudgetEntry(
    val id: Int,
    val categoryId: Int,
    val categoryName: String,
    val amountLimit: Double,
    val spent: Double,
    val percentage: Double,
    val period: String
)

data class CategoriesUiState(
    val categorySpends: List<CategorySpend> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val monthlySummary: List<MonthlySummaryEntry> = emptyList(),
    val recurring: List<RecurringEntry> = emptyList(),
    val budgets: List<BudgetEntry> = emptyList()
)

class CategoriesViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private var categoryRepo: CategoryRepository? = null
    private var transactionRepo: TransactionRepository? = null
    private var api: TmsApi? = null

    init {
        viewModelScope.launch {
            val url = container.backendUrlFlow.first()
            if (url.isNotBlank()) {
                initRepositories(url)
            }
        }
    }

    private fun initRepositories(url: String) {
        val builtApi = container.buildApi(url)
        api = builtApi
        categoryRepo = container.categoryRepository(builtApi)
        transactionRepo = container.transactionRepository(builtApi)
        observeData()
        loadAnalytics()
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

                _uiState.value = _uiState.value.copy(
                    categorySpends = spends,
                    transactions = transactions,  // Always ALL transactions — filtering happens in UI
                    categories = categories
                )
            }
        }
    }

    private fun loadAnalytics() {
        val tmsApi = api ?: return
        viewModelScope.launch {
            // Monthly summary
            try {
                val raw = tmsApi.getMonthlySummary()
                val parsed = raw.map { m ->
                    MonthlySummaryEntry(
                        month = m["month"]?.toString() ?: "",
                        income = (m["income"] as? Number)?.toDouble() ?: 0.0,
                        expenses = (m["expenses"] as? Number)?.toDouble() ?: 0.0,
                        net = (m["net"] as? Number)?.toDouble() ?: 0.0
                    )
                }
                _uiState.value = _uiState.value.copy(monthlySummary = parsed)
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            // Recurring
            try {
                val raw = tmsApi.getRecurring()
                val parsed = raw.map { m ->
                    RecurringEntry(
                        merchant = m["merchant"]?.toString() ?: "",
                        avgAmount = (m["avg_amount"] as? Number)?.toDouble() ?: 0.0,
                        frequency = m["frequency"]?.toString() ?: "",
                        lastDate = m["last_date"]?.toString() ?: "",
                        nextEstimated = m["next_estimated"]?.toString(),
                        category = m["category"]?.toString(),
                        count = (m["count"] as? Number)?.toInt() ?: 0
                    )
                }
                _uiState.value = _uiState.value.copy(recurring = parsed)
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            // Budgets
            try {
                val raw = tmsApi.getBudgets()
                val parsed = raw.map { m ->
                    BudgetEntry(
                        id = (m["id"] as? Number)?.toInt() ?: 0,
                        categoryId = (m["category_id"] as? Number)?.toInt() ?: 0,
                        categoryName = m["category_name"]?.toString() ?: "",
                        amountLimit = (m["amount_limit"] as? Number)?.toDouble() ?: 0.0,
                        spent = (m["spent"] as? Number)?.toDouble() ?: 0.0,
                        percentage = (m["percentage"] as? Number)?.toDouble() ?: 0.0,
                        period = m["period"]?.toString() ?: "monthly"
                    )
                }
                _uiState.value = _uiState.value.copy(budgets = parsed)
            } catch (_: Exception) {}
        }
    }

    fun toggleCategory(categoryId: Int) {
        val current = _uiState.value.selectedCategoryIds
        val updated = if (categoryId in current) current - categoryId else current + categoryId
        _uiState.value = _uiState.value.copy(selectedCategoryIds = updated)
        observeData()
    }

    fun clearCategoryFilter() {
        _uiState.value = _uiState.value.copy(selectedCategoryIds = emptySet())
        observeData()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CategoriesViewModel(container) as T
    }
}
