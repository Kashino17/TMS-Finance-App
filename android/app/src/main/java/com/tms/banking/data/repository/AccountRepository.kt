package com.tms.banking.data.repository

import com.tms.banking.data.local.dao.AccountDao
import com.tms.banking.data.local.entity.AccountEntity
import com.tms.banking.data.remote.TmsApi
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val accountDao: AccountDao,
    private val api: TmsApi
) {
    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAccounts()

    suspend fun getAccounts(): List<AccountEntity> = accountDao.getAccounts()

    suspend fun getAccountById(id: Int): AccountEntity? = accountDao.getAccountById(id)

    suspend fun refreshAccounts(): Result<Unit> {
        return try {
            val accounts = api.getAccounts()
            accountDao.deleteAll()
            accountDao.insertAccounts(accounts.map { it.toEntity() })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTotalWealthAed(accounts: List<AccountEntity>): Double {
        // For accounts not in AED, would need exchange rates from backend
        // For now sum all balances (most accounts should be AED or the backend converts)
        return accounts.sumOf { it.balance }
    }
}
