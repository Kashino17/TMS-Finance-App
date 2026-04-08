package com.tms.banking.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tms.banking.TmsApp
import com.tms.banking.ui.components.AccountCard
import com.tms.banking.ui.components.FoldAwareLayout
import com.tms.banking.ui.components.TransactionItem
import com.tms.banking.ui.components.formatAmount
import com.tms.banking.ui.components.rememberFoldState
import com.tms.banking.ui.theme.Background
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Outline
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface
import com.tms.banking.ui.theme.SurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: TmsApp,
    onAccountClick: (Int) -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(app.container))
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
                    text = "TMS Banking",
                    color = OnBackground,
                    fontWeight = FontWeight.Bold
                )
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

        if (!state.backendConfigured) {
            BackendNotConfiguredBanner()
            return@Column
        }

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            FoldAwareLayout(
                foldState = foldState,
                foldedContent = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        item {
                            WealthHeader(
                                totalAed = state.totalWealthAed,
                                showInAed = state.showInAed
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (state.error != null) {
                            item {
                                ErrorBanner(error = state.error!!)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        item {
                            SectionHeader("Accounts")
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(state.accounts) { account ->
                            AccountCard(
                                account = account,
                                showInAed = state.showInAed,
                                onClick = { onAccountClick(account.id) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            SectionHeader("Recent Transactions")
                            Spacer(modifier = Modifier.height(8.dp))
                        }

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
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Surface)
                            ) {
                                TransactionItem(
                                    transaction = tx,
                                    category = category,
                                    showInAed = state.showInAed
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                },
                unfoldedStart = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            WealthHeader(totalAed = state.totalWealthAed, showInAed = state.showInAed)
                            Spacer(modifier = Modifier.height(8.dp))
                            SectionHeader("Accounts")
                        }
                        items(state.accounts) { account ->
                            AccountCard(
                                account = account,
                                showInAed = state.showInAed,
                                onClick = { onAccountClick(account.id) }
                            )
                        }
                    }
                },
                unfoldedEnd = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            SectionHeader("Recent Transactions")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(state.transactions) { tx ->
                            val category = state.categories.find { it.id == tx.categoryId }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Surface)
                            ) {
                                TransactionItem(
                                    transaction = tx,
                                    category = category,
                                    showInAed = state.showInAed
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun WealthHeader(totalAed: Double, showInAed: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Wealth",
                color = OnSurface,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatAmount(totalAed, "AED"),
                color = OnBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = OnBackground,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    )
}

@Composable
private fun ErrorBanner(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Primary)
            Text(
                text = error,
                color = OnSurface,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun BackendNotConfiguredBanner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Backend not configured", color = OnBackground, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Go to Settings and enter your backend URL",
                color = OnSurface,
                fontSize = 14.sp
            )
        }
    }
}
