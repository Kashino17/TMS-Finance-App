package com.tms.banking.ui.categories

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.tms.banking.ui.theme.Negative
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Positive
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface
import com.tms.banking.ui.theme.SurfaceVariant
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

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
fun CategoriesScreen(app: TmsApp, onNavigateToSubscriptions: () -> Unit = {}) {
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
            actions = {
                TextButton(onClick = onNavigateToSubscriptions) {
                    Text("Abos", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )

        // Find selected donut index for highlighting
        val selectedDonutIndex = remember(state.selectedCategoryId, donutSlices, state.categories) {
            if (state.selectedCategoryId == null) null
            else {
                val catName = state.categories.find { it.id == state.selectedCategoryId }?.name
                donutSlices.indexOfFirst { it.label == catName }.takeIf { it >= 0 }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Spending Summary + Date Filter (together)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Total Spent", color = OnSurface, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            formatAmount(totalSpend, "AED"),
                            color = Negative,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                                        selectedContainerColor = Primary.copy(alpha = 0.3f),
                                        selectedLabelColor = Primary,
                                        containerColor = Surface,
                                        labelColor = OnSurface
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Donut Chart with interactive legend (legend = category filter)
            item {
                if (donutSlices.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Spending Breakdown", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Tap a category to filter", color = OnSurface.copy(alpha = 0.6f), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            DonutChart(
                                slices = donutSlices,
                                totalLabel = dateFilter.label,
                                totalValue = formatAmount(totalSpend, "AED"),
                                modifier = Modifier.fillMaxWidth(),
                                onSliceClick = { index ->
                                    val catName = donutSlices.getOrNull(index)?.label
                                    val catId = state.categories.find { it.name == catName }?.id
                                    if (catId == state.selectedCategoryId) {
                                        vm.selectCategory(null) // Deselect
                                    } else {
                                        vm.selectCategory(catId)
                                    }
                                },
                                selectedIndex = selectedDonutIndex
                            )
                        }
                    }
                }
            }

            // 3. Monthly bar chart
            item {
                if (state.monthlySummary.isNotEmpty()) {
                    MonthlyBarChart(summary = state.monthlySummary)
                }
            }

            // 4. Budget progress
            item {
                if (state.budgets.isNotEmpty()) {
                    BudgetSection(budgets = state.budgets)
                }
            }

            // 5. Recurring section
            item {
                if (state.recurring.isNotEmpty()) {
                    RecurringSection(recurring = state.recurring)
                }
            }

            // 6. Transactions
            item {
                Text(
                    "Transactions (${filteredByCategory.size})",
                    color = OnBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            if (filteredByCategory.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
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
            }
        }
    }
}

@Composable
private fun MonthlyBarChart(summary: List<MonthlySummaryEntry>) {
    // Show last 6 months
    val entries = summary.takeLast(6)
    val maxValue = entries.maxOfOrNull { maxOf(it.income, it.expenses) }.takeIf { it != null && it > 0 } ?: 1.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Monthly Overview",
                color = OnBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(4.dp)
                        .background(Positive, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Income", color = OnSurface, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(4.dp)
                        .background(Negative, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Expenses", color = OnSurface, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                val barGroupWidth = chartWidth / entries.size
                val barWidth = barGroupWidth * 0.3f
                val gap = barGroupWidth * 0.05f

                entries.forEachIndexed { i, entry ->
                    val groupLeft = i * barGroupWidth
                    val centerX = groupLeft + barGroupWidth / 2f

                    // Income bar (green, left of center)
                    val incomeHeight = ((entry.income / maxValue) * chartHeight * 0.85f).toFloat()
                    val incomeLeft = centerX - barWidth - gap / 2f
                    drawRect(
                        color = Positive,
                        topLeft = Offset(incomeLeft, chartHeight - incomeHeight),
                        size = Size(barWidth, incomeHeight)
                    )

                    // Expense bar (red, right of center)
                    val expenseHeight = ((entry.expenses / maxValue) * chartHeight * 0.85f).toFloat()
                    val expenseLeft = centerX + gap / 2f
                    drawRect(
                        color = Negative,
                        topLeft = Offset(expenseLeft, chartHeight - expenseHeight),
                        size = Size(barWidth, expenseHeight)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Month labels
            Row(modifier = Modifier.fillMaxWidth()) {
                entries.forEach { entry ->
                    Text(
                        text = formatMonthLabel(entry.month),
                        color = OnSurface,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun formatMonthLabel(month: String): String {
    // month is expected as "2024-01" or "2024-01-01"
    return try {
        val parts = month.split("-")
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        months.getOrElse(m - 1) { month }
    } catch (_: Exception) { month }
}

@Composable
private fun BudgetSection(budgets: List<BudgetEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Budgets",
                color = OnBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            budgets.forEach { budget ->
                BudgetProgressRow(budget = budget)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BudgetProgressRow(budget: BudgetEntry) {
    val pct = budget.percentage.coerceAtLeast(0.0)
    val progressColor = when {
        pct > 100.0 -> Negative
        pct >= 80.0 -> Color(0xFFFFB74D)
        else -> Positive
    }
    val progressFraction = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()

    val fmt = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = budget.categoryName,
                color = OnBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AED ${fmt.format(budget.spent)} / ${fmt.format(budget.amountLimit)}",
                color = OnSurface,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = progressColor,
            trackColor = SurfaceVariant,
        )
        if (pct > 100.0) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Over budget by ${fmt.format(budget.spent - budget.amountLimit)} AED",
                color = Negative,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun RecurringSection(recurring: List<RecurringEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Recurring Payments",
                color = OnBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Detected subscriptions & regular payments",
                color = OnSurface,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            recurring.forEach { entry ->
                RecurringRow(entry = entry)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun RecurringRow(entry: RecurringEntry) {
    val fmt = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .background(Primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = recurringEmoji(entry.merchant), fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.merchant,
                color = OnBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val nextText = if (!entry.nextEstimated.isNullOrBlank()) {
                "Next: ${formatShortDate(entry.nextEstimated)}"
            } else {
                entry.frequency.replaceFirstChar { it.uppercase() }
            }
            Text(
                text = nextText,
                color = OnSurface,
                fontSize = 11.sp
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "AED ${fmt.format(entry.avgAmount)}",
                color = Negative,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            if (!entry.category.isNullOrBlank()) {
                Text(
                    text = entry.category,
                    color = OnSurface,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun recurringEmoji(merchant: String): String {
    val m = merchant.lowercase()
    return when {
        m.contains("netflix") -> "🎬"
        m.contains("spotify") || m.contains("music") -> "🎵"
        m.contains("amazon") -> "📦"
        m.contains("apple") || m.contains("itunes") -> "🍎"
        m.contains("google") -> "G"
        m.contains("gym") || m.contains("fitness") -> "💪"
        m.contains("phone") || m.contains("telecom") || m.contains("etisalat") || m.contains("du ") -> "📱"
        m.contains("water") || m.contains("elec") || m.contains("dewa") -> "💡"
        m.contains("insurance") -> "🛡"
        else -> "🔄"
    }
}

private fun formatShortDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("T")[0].split("-")
        if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0].takeLast(2)}" else dateStr
    } catch (_: Exception) { dateStr }
}
