package com.tms.banking.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.tms.banking.data.local.entity.CategoryEntity

data class CategoryDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: String?,
    @SerializedName("color") val color: String?,
    @SerializedName("parent_id") val parentId: Int?
) {
    fun toEntity() = CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        parentId = parentId
    )
}
