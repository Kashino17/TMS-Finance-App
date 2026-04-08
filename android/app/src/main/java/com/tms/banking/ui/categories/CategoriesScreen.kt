package com.tms.banking.ui.categories

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tms.banking.TmsApp
import com.tms.banking.ui.components.DonutChart
import com.tms.banking.ui.components.DonutSlice
import com.tms.banking.ui.components.TransactionItem
import com.tms.banking.ui.components.formatAmount
import com.tms.banking.ui.theme.Background
import com.tms.banking.ui.theme.CategoryColors
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class DateFilter(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    THREE_MONTHS("3 Months"),
    SIX_MONTHS("6 Months"),
    THIS_YEAR("This Year"),
    ALL("All"),
    CUSTOM("Custom")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(app: TmsApp) {
    val vm: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory(app.container))
    val state by vm.uiState.collectAsStateWithLifecycle()

    var dateFilter by remember { mutableStateOf(DateFilter.THIS_MONTH) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val now = LocalDate.now()
    val dateRange = remember(dateFilter, customFrom, customTo) {
        when (dateFilter) {
            DateFilter.THIS_MONTH -> now.withDayOfMonth(1) to now
            DateFilter.LAST_MONTH -> now.minusMonths(1).withDayOfMonth(1) to now.minusMonths(1).withDayOfMonth(now.minusMonths(1).lengthOfMonth())
            DateFilter.THREE_MONTHS -> now.minusMonths(3) to now
            DateFilter.SIX_MONTHS -> now.minusMonths(6) to now
            DateFilter.THIS_YEAR -> now.withDayOfYear(1) to now
            DateFilter.ALL -> null to null
            DateFilter.CUSTOM -> (customFrom ?: now.minusYears(10)) to (customTo ?: now)
        }
    }

    val filteredTransactions = remember(state.transactions, dateRange) {
        val (from, to) = dateRange
        if (from == null || to == null) {
            state.transactions
        } else {
            state.transactions.filter { tx ->
                try {
                    val txDate = LocalDate.parse(tx.date)
                    !txDate.isBefore(from) && !txDate.isAfter(to)
                } catch (e: Exception) { true }
            }
        }
    }

    val filteredByCategory = remember(filteredTransactions, state.selectedCategoryId) {
        if (state.selectedCategoryId != null) {
            filteredTransactions.filter { it.categoryId == state.selectedCategoryId }
        } else {
            filteredTransactions
        }
    }

    val donutSlices = remember(filteredTransactions, state.categories) {
        state.categories.map { cat ->
            val catTxs = filteredTransactions.filter { it.categoryId == cat.id && it.amount < 0 }
            CategorySpend(
                category = cat,
                totalAed = catTxs.sumOf { kotlin.math.abs(it.amountAed) },
                count = catTxs.size
            )
        }.filter { it.totalAed > 0 }
            .sortedByDescending { it.totalAed }
            .mapIndexed { index, spend ->
                DonutSlice(
                    label = spend.category.name,
                    value = spend.totalAed,
                    color = spend.category.color?.let {
                        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { CategoryColors[index % CategoryColors.size] }
                    } ?: CategoryColors[index % CategoryColors.size]
                )
            }
    }

    val totalSpend = donutSlices.sumOf { it.value }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customFrom = pickerState.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    customTo = pickerState.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    dateFilter = DateFilter.CUSTOM
                    showDatePicker = false
                }) { Text("OK", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = OnSurface) }
            }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(500.dp))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = {
                Text("Categories", color = OnBackground, fontWeight = FontWeight.Bold)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Date filter chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DateFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = dateFilter == filter,
                            onClick = {
                                if (filter == DateFilter.CUSTOM) showDatePicker = true
                                else dateFilter = filter
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
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Donut chart
            item {
                if (donutSlices.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Spending Breakdown",
                                color = OnBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            DonutChart(
                                slices = donutSlices,
                                totalLabel = dateFilter.label,
                                totalValue = formatAmount(totalSpend, "AED"),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Category filter chips
            item {
                Text("Filter by Category", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategoryId == null,
                            onClick = { vm.selectCategory(null) },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.2f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                    items(state.categorySpends) { spend ->
                        FilterChip(
                            selected = state.selectedCategoryId == spend.category.id,
                            onClick = { vm.selectCategory(spend.category.id) },
                            label = { Text("${spend.category.icon ?: ""} ${spend.category.name}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.2f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Transactions
            item {
                Text("Transactions (${filteredByCategory.size})", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (filteredByCategory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No transactions in this period", color = OnSurface)
                    }
                }
            }

            items(filteredByCategory) { tx ->
                val category = state.categories.find { it.id == tx.categoryId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    TransactionItem(transaction = tx, category = category, showInAed = false)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
