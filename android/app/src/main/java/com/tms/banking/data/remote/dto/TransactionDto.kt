package com.tms.banking.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.tms.banking.data.local.entity.TransactionEntity

data class TransactionDto(
    @SerializedName("id") val id: Int,
    @SerializedName("account_id") val accountId: Int,
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: String,
    @SerializedName("amount_aed") val amountAed: Double,
    @SerializedName("date") val date: String,
    @SerializedName("merchant_name") val merchantName: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("category_id") val categoryId: Int?,
    @SerializedName("source") val source: String?
) {
    fun toEntity() = TransactionEntity(
        id = id,
        accountId = accountId,
        amount = amount,
        currency = currency,
        amountAed = amountAed,
        date = date,
        merchantName = merchantName,
        description = description,
        categoryId = categoryId,
        source = source
    )
}

data class UpdateCategoryRequest(
    @SerializedName("category_id") val categoryId: Int
)
