package com.tms.banking.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(app: TmsApp) {
    val vm: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory(app.container))
    val state by vm.uiState.collectAsStateWithLifecycle()

    val donutSlices = state.categorySpends.mapIndexed { index, spend ->
        DonutSlice(
            label = spend.category.name,
            value = spend.totalAed,
            color = spend.category.color?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { CategoryColors[index % CategoryColors.size] }
            } ?: CategoryColors[index % CategoryColors.size]
        )
    }

    val totalSpend = state.categorySpends.sumOf { it.totalAed }

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
                                totalLabel = "Total Spent",
                                totalValue = formatAmount(totalSpend, "AED"),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

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

            item {
                Text("Transactions", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.transactions.isEmpty()) {
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
                    TransactionItem(transaction = tx, category = category, showInAed = false)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
