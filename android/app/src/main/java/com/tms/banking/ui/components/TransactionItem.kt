package com.tms.banking.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tms.banking.data.local.entity.CategoryEntity
import com.tms.banking.data.local.entity.TransactionEntity
import com.tms.banking.ui.theme.CategoryColors
import com.tms.banking.ui.theme.Negative
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Positive
import com.tms.banking.ui.theme.SurfaceVariant

@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    showInAed: Boolean,
    modifier: Modifier = Modifier
) {
    val isNegative = transaction.amount < 0
    val amountColor = if (isNegative) Negative else Positive
    val displayAmount = if (showInAed) transaction.amountAed else transaction.amount
    val displayCurrency = if (showInAed) "AED" else transaction.currency

    val categoryColor = category?.color?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { CategoryColors.first() }
    } ?: CategoryColors.first()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(categoryColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category?.icon ?: getCategoryEmoji(category?.name),
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchantName ?: transaction.description ?: "Transaction",
                color = OnBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (category != null) {
                    Box(
                        modifier = Modifier
                            .background(categoryColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = category.name,
                            color = categoryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = formatDate(transaction.date),
                    color = OnSurface,
                    fontSize = 11.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (isNegative) "-" else "+"}${formatAmount(kotlin.math.abs(displayAmount), displayCurrency)}",
                color = amountColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            if (showInAed && transaction.currency != "AED") {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatAmount(kotlin.math.abs(transaction.amount), transaction.currency),
                    color = OnSurface,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun getCategoryEmoji(name: String?): String {
    return when {
        name == null -> "💳"
        name.contains("Food", ignoreCase = true) || name.contains("Restaurant", ignoreCase = true) -> "🍽️"
        name.contains("Transport", ignoreCase = true) || name.contains("Travel", ignoreCase = true) -> "🚗"
        name.contains("Shopping", ignoreCase = true) -> "🛍️"
        name.contains("Health", ignoreCase = true) -> "🏥"
        name.contains("Entertainment", ignoreCase = true) -> "🎬"
        name.contains("Utilities", ignoreCase = true) -> "💡"
        name.contains("Salary", ignoreCase = true) || name.contains("Income", ignoreCase = true) -> "💰"
        name.contains("Coffee", ignoreCase = true) -> "☕"
        name.contains("Grocery", ignoreCase = true) -> "🛒"
        else -> "💳"
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("T")[0].split("-")
        if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else dateStr
    } catch (_: Exception) {
        dateStr
    }
}
