package com.tms.banking.ui.subscriptions

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tms.banking.AppContainer
import com.tms.banking.ui.components.formatAmount
import com.tms.banking.ui.theme.Background
import com.tms.banking.ui.theme.Negative
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Positive
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface
import com.tms.banking.ui.theme.SurfaceVariant
import kotlinx.coroutines.flow.first

data class SubTransaction(
    val id: Int,
    val date: String,
    val amount: Double,
    val amountAed: Double,
    val currency: String,
    val merchantName: String,
    val description: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionDetailScreen(
    container: AppContainer,
    sub: SubscriptionData,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var transactions by remember { mutableStateOf<List<SubTransaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val statusColor = when (sub.status) {
        "active" -> Positive
        "pending" -> Primary
        else -> OnSurface.copy(alpha = 0.5f)
    }
    val statusLabel = when (sub.status) {
        "active" -> "Active"
        "pending" -> "Expected soon"
        "inactive" -> "Inactive"
        else -> sub.status
    }

    LaunchedEffect(sub.merchant) {
        try {
            val url = container.backendUrlFlow.first()
            if (url.isNotBlank()) {
                val api = container.buildApi(url)
                val result = api.getRecurringTransactions(sub.merchant.lowercase())
                transactions = result.map { r ->
                    SubTransaction(
                        id = (r["id"] as? Double)?.toInt() ?: 0,
                        date = r["date"] as? String ?: "",
                        amount = r["amount"] as? Double ?: 0.0,
                        amountAed = r["amount_aed"] as? Double ?: 0.0,
                        currency = r["currency"] as? String ?: "AED",
                        merchantName = r["merchant_name"] as? String ?: "",
                        description = r["description"] as? String,
                    )
                }
            }
        } catch (_: Exception) {}
        isLoading = false
    }

    val totalSpent = transactions.sumOf { it.amountAed }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text(sub.merchant, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = OnSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(statusColor, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(statusLabel, color = statusColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(sub.frequency, color = OnSurface, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(formatAmount(sub.avgAmount, "AED"), color = Negative, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("per month", color = OnSurface, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total spent", color = OnSurface, fontSize = 11.sp)
                                Text(formatAmount(totalSpent, "AED"), color = OnBackground, fontWeight = FontWeight.SemiBold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Payments", color = OnSurface, fontSize = 11.sp)
                                Text("${transactions.size}", color = OnBackground, fontWeight = FontWeight.SemiBold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Category", color = OnSurface, fontSize = 11.sp)
                                Text(sub.category ?: "—", color = OnBackground, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (sub.cancelUrl != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sub.cancelUrl))) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.OpenInNew, null, tint = OnBackground, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Manage Subscription", color = OnBackground)
                            }
                        }
                    }
                }
            }

            // Transaction history header
            item {
                Text("Payment History (${transactions.size})", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            }

            items(transactions) { txn ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(txn.date, color = OnBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            if (txn.description != null) {
                                Text(txn.description, color = OnSurface, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        Text(
                            formatAmount(txn.amountAed, "AED"),
                            color = if (txn.amountAed < 0) Negative else Positive,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
