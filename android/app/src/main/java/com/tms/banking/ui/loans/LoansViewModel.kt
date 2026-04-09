package com.tms.banking.ui.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tms.banking.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LoansUiState(
    val loans: List<LoanData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class LoansViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(LoansUiState())
    val uiState: StateFlow<LoansUiState> = _uiState.asStateFlow()

    init {
        loadLoans()
    }

    private fun loadLoans() {
        viewModelScope.launch {
            val url = container.backendUrlFlow.first()
            if (url.isBlank()) return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val api = container.buildApi(url)
                val resp = api.getLoans()
                val loans = resp.map { loan ->
                    LoanData(
                        id = (loan["id"] as? Double)?.toInt() ?: 0,
                        name = loan["name"] as? String ?: "",
                        type = loan["type"] as? String ?: "",
                        totalAmount = loan["total_amount"] as? Double ?: 0.0,
                        remainingAmount = loan["remaining_amount"] as? Double ?: 0.0,
                        monthlyPayment = loan["monthly_payment"] as? Double ?: 0.0,
                        interestRate = loan["interest_rate"] as? Double,
                        currency = loan["currency"] as? String ?: "AED",
                        startDate = loan["start_date"] as? String ?: "",
                        endDate = loan["end_date"] as? String,
                        dueDay = (loan["due_day"] as? Double)?.toInt(),
                        notes = loan["notes"] as? String,
                        isActive = loan["is_active"] as? Boolean ?: true,
                    )
                }
                _uiState.value = _uiState.value.copy(loans = loans, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun addLoan(loan: LoanData) {
        viewModelScope.launch {
            val url = container.backendUrlFlow.first()
            if (url.isBlank()) return@launch
            try {
                val api = container.buildApi(url)
                api.createLoan(mapOf(
                    "name" to loan.name,
                    "type" to loan.type,
                    "total_amount" to loan.totalAmount,
                    "remaining_amount" to loan.remainingAmount,
                    "monthly_payment" to loan.monthlyPayment,
                    "interest_rate" to (loan.interestRate ?: 0.0),
                    "currency" to loan.currency,
                    "start_date" to loan.startDate,
                    "due_day" to (loan.dueDay ?: 0),
                    "notes" to (loan.notes ?: ""),
                ))
                loadLoans()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteLoan(id: Int) {
        viewModelScope.launch {
            val url = container.backendUrlFlow.first()
            if (url.isBlank()) return@launch
            try {
                val api = container.buildApi(url)
                api.deleteLoan(id)
                loadLoans()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoansViewModel(container) as T
    }
}
