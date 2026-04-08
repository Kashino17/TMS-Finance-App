package com.tms.banking.data.repository

import com.tms.banking.data.local.dao.CategoryDao
import com.tms.banking.data.local.entity.CategoryEntity
import com.tms.banking.data.remote.TmsApi
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val api: TmsApi
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeCategories()

    suspend fun getCategories(): List<CategoryEntity> = categoryDao.getCategories()

    suspend fun getCategoryById(id: Int): CategoryEntity? = categoryDao.getCategoryById(id)

    suspend fun refreshCategories(): Result<Unit> {
        return try {
            val categories = api.getCategories()
            categoryDao.deleteAll()
            categoryDao.insertCategories(categories.map { it.toEntity() })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
