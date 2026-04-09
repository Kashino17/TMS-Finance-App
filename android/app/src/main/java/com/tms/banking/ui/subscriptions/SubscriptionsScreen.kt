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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tms.banking.TmsApp
import com.tms.banking.ui.components.formatAmount
import com.tms.banking.ui.theme.Background
import com.tms.banking.ui.theme.Negative
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Positive
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface
import com.tms.banking.ui.theme.SurfaceVariant

data class SubscriptionData(
    val merchant: String,
    val avgAmount: Double,
    val frequency: String,
    val lastDate: String,
    val nextEstimated: String,
    val category: String?,
    val categoryType: String,
    val count: Int,
    val status: String,
    val cancelUrl: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    app: TmsApp,
    onNavigateBack: () -> Unit
) {
    val vm: SubscriptionsViewModel = viewModel(factory = SubscriptionsViewModel.Factory(app.container))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showBusiness by remember { mutableStateOf(false) }

    val filtered = if (showBusiness) state.business else state.personal
    val totalMonthly = filtered.sumOf { it.avgAmount }

    Column(
        modifier = Modifier.fillMaxSize().background(Background)
    ) {
        TopAppBar(
            title = { Text("Subscriptions & Recurring", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
            // Toggle chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showBusiness,
                        onClick = { showBusiness = false },
                        label = { Text("Personal Abos") },
                        leadingIcon = { Icon(Icons.Filled.Subscriptions, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary.copy(alpha = 0.2f),
                            selectedLabelColor = Primary,
                        )
                    )
                    FilterChip(
                        selected = showBusiness,
                        onClick = { showBusiness = true },
                        label = { Text("Business") },
                        leadingIcon = { Icon(Icons.Filled.Business, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary.copy(alpha = 0.2f),
                            selectedLabelColor = Primary,
                        )
                    )
                }
            }

            // Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            if (showBusiness) "Monthly Business Expenses" else "Monthly Subscriptions",
                            color = OnSurface, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            formatAmount(totalMonthly, "AED"),
                            color = Negative,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${filtered.size} active recurring payments", color = OnSurface, fontSize = 12.sp)
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (showBusiness) "No business recurring expenses detected" else "No personal subscriptions detected",
                            color = OnSurface
                        )
                    }
                }
            }

            items(filtered) { sub ->
                SubscriptionCard(sub)
            }
        }
    }
}

@Composable
private fun SubscriptionCard(sub: SubscriptionData) {
    val context = LocalContext.current
    val statusColor = when (sub.status) {
        "active" -> Positive
        "overdue" -> Negative
        "pending" -> Color(0xFFFF9800)
        else -> OnSurface
    }
    val statusLabel = when (sub.status) {
        "active" -> "Active this month"
        "overdue" -> "Overdue — possibly cancelled?"
        "pending" -> "Pending this month"
        else -> sub.status
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(sub.merchant, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "${sub.frequency} · ${sub.category ?: "Uncategorized"}",
                        color = OnSurface, fontSize = 12.sp
                    )
                }
                Text(
                    formatAmount(sub.avgAmount, "AED"),
                    color = Negative,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("Last: ${sub.lastDate} · Next: ${sub.nextEstimated}", color = OnSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                if (sub.cancelUrl != null) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sub.cancelUrl)))
                    }) {
                        Icon(Icons.Filled.OpenInNew, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage", color = Primary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
