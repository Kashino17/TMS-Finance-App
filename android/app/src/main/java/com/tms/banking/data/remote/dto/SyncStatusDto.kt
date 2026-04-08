package com.tms.banking.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SyncStatusDto(
    @SerializedName("account_id") val accountId: Int,
    @SerializedName("account_name") val accountName: String,
    @SerializedName("last_sync_at") val lastSyncAt: String?,
    @SerializedName("status") val status: String,
    @SerializedName("transactions_fetched") val transactionsFetched: Int
)
