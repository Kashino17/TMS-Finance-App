package com.tms.banking.data.repository

import com.tms.banking.data.local.dao.TransactionDao
import com.tms.banking.data.local.entity.TransactionEntity
import com.tms.banking.data.remote.TmsApi
import com.tms.banking.data.remote.dto.UpdateCategoryRequest
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val api: TmsApi
) {
    fun observeTransactions(limit: Int = 5000, offset: Int = 0): Flow<List<TransactionEntity>> =
        transactionDao.observeTransactions(limit, offset)

    fun observeTransactionsByAccount(accountId: Int, limit: Int = 50, offset: Int = 0): Flow<List<TransactionEntity>> =
        transactionDao.observeTransactionsByAccount(accountId, limit, offset)

    fun observeTransactionsByCategory(categoryId: Int): Flow<List<TransactionEntity>> =
        transactionDao.observeTransactionsByCategory(categoryId)

    suspend fun refreshTransactions(accountId: Int? = null, limit: Int = 5000, offset: Int = 0): Result<Unit> {
        return try {
            val transactions = api.getTransactions(accountId, limit, offset)
            // Only replace local data if we got data back from backend
            if (transactions.isNotEmpty()) {
                if (accountId != null) {
                    transactionDao.deleteByAccount(accountId)
                } else {
                    transactionDao.deleteAll()
                }
                transactionDao.insertTransactions(transactions.map { it.toEntity() })
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // Backend unreachable — keep local data, don't delete anything
            Result.failure(e)
        }
    }

    suspend fun updateTransactionCategory(id: Int, categoryId: Int): Result<Unit> {
        return try {
            api.updateTransaction(id, UpdateCategoryRequest(categoryId))
            transactionDao.updateCategory(id, categoryId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
