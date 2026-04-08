package com.tms.banking.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val bank: String,
    val currency: String,
    val type: String,
    val balance: Double,
    val isActive: Boolean,
    val lastSyncAt: String?
)
