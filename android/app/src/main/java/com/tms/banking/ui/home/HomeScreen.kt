package com.tms.banking.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.tms.banking.TmsApp
import com.tms.banking.ui.components.AccountCard
import com.tms.banking.ui.components.FoldAwareLayout
import com.tms.banking.ui.components.TransactionItem
import com.tms.banking.ui.components.formatAmount
import com.tms.banking.ui.components.rememberFoldState
import com.tms.banking.ui.theme.Background
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
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

    var dateFilter by remember { mutableStateOf(DateFilter.ALL) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredTransactions = remember(state.transactions, dateFilter, customFrom, customTo, state.searchQuery) {
        val now = LocalDate.now()
        val (from, to) = when (dateFilter) {
            DateFilter.THIS_MONTH -> now.withDayOfMonth(1) to now
            DateFilter.LAST_MONTH -> now.minusMonths(1).withDayOfMonth(1) to now.minusMonths(1).withDayOfMonth(now.minusMonths(1).lengthOfMonth())
            DateFilter.THREE_MONTHS -> now.minusMonths(3) to now
            DateFilter.SIX_MONTHS -> now.minusMonths(6) to now
            DateFilter.THIS_YEAR -> now.withDayOfYear(1) to now
            DateFilter.CUSTOM -> (customFrom ?: now.minusYears(10)) to (customTo ?: now)
            DateFilter.ALL -> null to null
        }
        val dateFiltered = if (from == null || to == null) {
            state.transactions
        } else {
            state.transactions.filter { tx ->
                try {
                    val txDate = LocalDate.parse(tx.date)
                    !txDate.isBefore(from) && !txDate.isAfter(to)
                } catch (e: Exception) { true }
            }
        }
        val q = state.searchQuery.trim()
        if (q.isEmpty()) dateFiltered
        else dateFiltered.filter { tx ->
            (tx.merchantName?.contains(q, ignoreCase = true) == true) ||
            (tx.description?.contains(q, ignoreCase = true) == true)
        }
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { fromMillis, toMillis ->
                customFrom = fromMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                customTo = toMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                dateFilter = DateFilter.CUSTOM
                showDatePicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        if (searchActive) {
            TopAppBar(
                title = {
                    TextField(
                        value = state.searchQuery,
                        onValueChange = { vm.setSearchQuery(it) },
                        placeholder = { Text("Search transactions...", color = OnSurface, fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = OnBackground,
                            unfocusedTextColor = OnBackground,
                            cursorColor = Primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        searchActive = false
                        vm.setSearchQuery("")
                        keyboardController?.hide()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
            androidx.compose.runtime.LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        } else {
            TopAppBar(
                title = {
                    Text(
                        text = "TMS Banking",
                        color = OnBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { searchActive = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = OnSurface)
                    }
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
        }

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
                            SectionHeader("Transactions (${filteredTransactions.size})")
                            Spacer(modifier = Modifier.height(8.dp))
                            DateFilterRow(
                                selectedFilter = dateFilter,
                                onFilterChange = { dateFilter = it },
                                onCustomRange = { showDatePicker = true }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (filteredTransactions.isEmpty() && !state.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No transactions in this period", color = OnSurface)
                                }
                            }
                        }

                        items(filteredTransactions) { tx ->
                            val category = state.categories.find { it.id == tx.categoryId }
                            var showCategoryMenu by remember { mutableStateOf(false) }
                            Box {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Surface)
                                ) {
                                    TransactionItem(
                                        transaction = tx,
                                        category = category,
                                        showInAed = state.showInAed,
                                        onLongPress = { showCategoryMenu = true }
                                    )
                                }
                                DropdownMenu(
                                    expanded = showCategoryMenu,
                                    onDismissRequest = { showCategoryMenu = false }
                                ) {
                                    Text(
                                        text = "Change Category",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnBackground,
                                        fontSize = 13.sp
                                    )
                                    state.categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${cat.icon ?: ""} ${cat.name}",
                                                    color = if (cat.id == tx.categoryId) Primary else OnBackground,
                                                    fontSize = 14.sp
                                                )
                                            },
                                            onClick = {
                                                vm.updateCategory(tx.id, cat.id)
                                                showCategoryMenu = false
                                            }
                                        )
                                    }
                                }
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
                            var showCategoryMenu by remember { mutableStateOf(false) }
                            Box {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Surface)
                                ) {
                                    TransactionItem(
                                        transaction = tx,
                                        category = category,
                                        showInAed = state.showInAed,
                                        onLongPress = { showCategoryMenu = true }
                                    )
                                }
                                DropdownMenu(
                                    expanded = showCategoryMenu,
                                    onDismissRequest = { showCategoryMenu = false }
                                ) {
                                    Text(
                                        text = "Change Category",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnBackground,
                                        fontSize = 13.sp
                                    )
                                    state.categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${cat.icon ?: ""} ${cat.name}",
                                                    color = if (cat.id == tx.categoryId) Primary else OnBackground,
                                                    fontSize = 14.sp
                                                )
                                            },
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

enum class DateFilter(val label: String) {
    ALL("All"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    THREE_MONTHS("3 Months"),
    SIX_MONTHS("6 Months"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}

@Composable
private fun DateFilterRow(
    selectedFilter: DateFilter,
    onFilterChange: (DateFilter) -> Unit,
    onCustomRange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DateFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = {
                    if (filter == DateFilter.CUSTOM) onCustomRange()
                    else onFilterChange(filter)
                },
                label = { Text(filter.label, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary.copy(alpha = 0.2f),
                    selectedLabelColor = Primary,
                    containerColor = Surface,
                    labelColor = OnSurface
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedStartDateMillis, state.selectedEndDateMillis) }) {
                Text("OK", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurface)
            }
        }
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(500.dp))
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
