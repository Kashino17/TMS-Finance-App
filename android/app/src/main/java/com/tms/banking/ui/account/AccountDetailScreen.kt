package com.tms.banking.ui.account

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tms.banking.TmsApp
import com.tms.banking.data.local.entity.TransactionEntity
import com.tms.banking.ui.components.FoldAwareLayout
import com.tms.banking.ui.components.TransactionItem
import com.tms.banking.ui.components.formatAmount
import com.tms.banking.ui.components.rememberFoldState
import com.tms.banking.ui.theme.Background
import com.tms.banking.ui.theme.Negative
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Positive
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface
import com.tms.banking.ui.theme.SurfaceVariant
import com.tms.banking.ui.theme.bankColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    app: TmsApp,
    accountId: Int,
    onNavigateBack: () -> Unit
) {
    val vm: AccountDetailViewModel = viewModel(
        factory = AccountDetailViewModel.Factory(app.container, accountId)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val foldState = rememberFoldState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = state.account?.name ?: "Account",
                    color = OnBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = OnBackground)
                }
            },
            actions = {
                FilterChip(
                    selected = state.showInAed,
                    onClick = { vm.toggleShowInAed() },
                    label = { Text("AED", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.CurrencyExchange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.2f),
                        selectedLabelColor = Primary,
                        selectedLeadingIconColor = Primary
                    )
                )
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = OnSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            val account = state.account
            val summaryContent: @Composable () -> Unit = {
                if (account != null) {
                    AccountSummaryCard(
                        balance = account.balance,
                        currency = account.currency,
                        bank = account.bank,
                        transactions = state.transactions,
                        showInAed = state.showInAed
                    )
                }
            }

            val transactionsContent: @Composable () -> Unit = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.transactions.isEmpty() && !state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No transactions", color = OnSurface)
                            }
                        }
                    }
                    items(state.transactions) { tx ->
                        val category = state.categories.find { it.id == tx.categoryId }
                        var showCategoryMenu by remember { mutableStateOf(false) }
                        Box {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                onClick = { showCategoryMenu = true }
                            ) {
                                TransactionItem(
                                    transaction = tx,
                                    category = category,
                                    showInAed = state.showInAed
                                )
                            }
                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                state.categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = OnBackground) },
                                        onClick = {
                                            vm.updateCategory(tx.id, cat.id)
                                            showCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FoldAwareLayout(
                foldState = foldState,
                foldedContent = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (account != null) {
                            item {
                                AccountSummaryCard(
                                    balance = account.balance,
                                    currency = account.currency,
                                    bank = account.bank,
                                    transactions = state.transactions,
                                    showInAed = state.showInAed
                                )
                            }
                        }
                        item {
                            Text(
                                "Transactions",
                                color = OnBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                        items(state.transactions) { tx ->
                            val category = state.categories.find { it.id == tx.categoryId }
                            var showCategoryMenu by remember { mutableStateOf(false) }
                            Box {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Surface),
                                    onClick = { showCategoryMenu = true }
                                ) {
                                    TransactionItem(
                                        transaction = tx,
                                        category = category,
                                        showInAed = state.showInAed
                                    )
                                }
                                DropdownMenu(
                                    expanded = showCategoryMenu,
                                    onDismissRequest = { showCategoryMenu = false }
                                ) {
                                    state.categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name, color = OnBackground) },
                                            onClick = {
                                                vm.updateCategory(tx.id, cat.id)
                                                showCategoryMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                unfoldedStart = {
                    Column(modifier = Modifier.padding(16.dp)) {
                        summaryContent()
                    }
                },
                unfoldedEnd = {
                    transactionsContent()
                }
            )
        }
    }
}

@Composable
private fun AccountSummaryCard(
    balance: Double,
    currency: String,
    bank: String,
    transactions: List<TransactionEntity>,
    showInAed: Boolean
) {
    val color = bankColor(bank)
    val totalSpent = transactions.filter { it.amount < 0 }.sumOf { if (showInAed) it.amountAed else it.amount }
    val totalIncome = transactions.filter { it.amount > 0 }.sumOf { if (showInAed) it.amountAed else it.amount }
    val displayCurrency = if (showInAed) "AED" else currency

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Current Balance", color = OnSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatAmount(balance, displayCurrency),
                color = OnBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Income", color = OnSurface, fontSize = 12.sp)
                    Text(
                        text = formatAmount(totalIncome, displayCurrency),
                        color = Positive,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Spent", color = OnSurface, fontSize = 12.sp)
                    Text(
                        text = formatAmount(kotlin.math.abs(totalSpent), displayCurrency),
                        color = Negative,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
