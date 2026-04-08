package com.tms.banking.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: Int,
    val accountId: Int,
    val amount: Double,
    val currency: String,
    val amountAed: Double,
    val date: String,
    val merchantName: String?,
    val description: String?,
    val categoryId: Int?,
    val source: String?
)
