package com.tms.banking.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NotificationDto(
    @SerializedName("bank_package") val bankPackage: String,
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String,
    @SerializedName("timestamp") val timestamp: Long
)
