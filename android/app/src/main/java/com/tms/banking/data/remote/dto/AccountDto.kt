package com.tms.banking.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.tms.banking.data.local.entity.AccountEntity

data class AccountDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("bank") val bank: String,
    @SerializedName("currency") val currency: String,
    @SerializedName("type") val type: String,
    @SerializedName("balance") val balance: Double,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("last_sync_at") val lastSyncAt: String?
) {
    fun toEntity() = AccountEntity(
        id = id,
        name = name,
        bank = bank,
        currency = currency,
        type = type,
        balance = balance,
        isActive = isActive,
        lastSyncAt = lastSyncAt
    )
}
