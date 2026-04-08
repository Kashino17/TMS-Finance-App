package com.tms.banking.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tms.banking.TmsApp
import com.tms.banking.ui.theme.Background
import com.tms.banking.ui.theme.Negative
import com.tms.banking.ui.theme.OnBackground
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Outline
import com.tms.banking.ui.theme.Positive
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface
import com.tms.banking.ui.theme.SurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    app: TmsApp,
    onNavigateBack: () -> Unit
) {
    val vm: AddTransactionViewModel = viewModel(factory = AddTransactionViewModel.Factory(app.container))
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            vm.resetSaved()
            onNavigateBack()
        }
    }

    var currencyExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = { Text("Add Transaction", color = OnBackground, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = OnBackground)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Amount + Currency row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = { vm.setAmount(it) },
                    label = { Text("Amount", color = OnSurface) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = tmsTextFieldColors()
                )

                Box {
                    Card(
                        modifier = Modifier.width(100.dp).height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        onClick = { currencyExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(state.currency, color = OnBackground, fontWeight = FontWeight.Medium)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = OnSurface)
                        }
                    }
                    DropdownMenu(
                        expanded = currencyExpanded,
                        onDismissRequest = { currencyExpanded = false }
                    ) {
                        supportedCurrencies.forEach { cur ->
                            DropdownMenuItem(
                                text = { Text(cur, color = OnBackground) },
                                onClick = { vm.setCurrency(cur); currencyExpanded = false }
                            )
                        }
                    }
                }
            }

            // Account selector
            LabeledDropdown(
                label = "Account",
                value = state.accounts.find { it.id == state.selectedAccountId }?.name ?: "Select account",
                expanded = accountExpanded,
                onExpandChange = { accountExpanded = it }
            ) {
                state.accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text("${account.name} (${account.bank})", color = OnBackground) },
                        onClick = { vm.setAccount(account.id); accountExpanded = false }
                    )
                }
            }

            // Category selector
            LabeledDropdown(
                label = "Category",
                value = state.categories.find { it.id == state.selectedCategoryId }?.name ?: "Select category",
                expanded = categoryExpanded,
                onExpandChange = { categoryExpanded = it }
            ) {
                state.categories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text("${cat.icon ?: ""} ${cat.name}", color = OnBackground) },
                        onClick = { vm.setCategory(cat.id); categoryExpanded = false }
                    )
                }
            }

            // Description
            OutlinedTextField(
                value = state.description,
                onValueChange = { vm.setDescription(it) },
                label = { Text("Description", color = OnSurface) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                maxLines = 3,
                colors = tmsTextFieldColors()
            )

            // Date
            OutlinedTextField(
                value = state.date,
                onValueChange = { vm.setDate(it) },
                label = { Text("Date (YYYY-MM-DD)", color = OnSurface) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = tmsTextFieldColors()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Negative.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = state.error!!,
                        color = Negative,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            Button(
                onClick = { vm.save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = OnBackground,
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Transaction", color = OnBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LabeledDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Text(label, color = OnSurface, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                onClick = { onExpandChange(true) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(value, color = OnBackground)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = OnSurface)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandChange(false) }
            ) {
                content()
            }
        }
    }
}

@Composable
private fun tmsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnBackground,
    unfocusedTextColor = OnBackground,
    focusedBorderColor = Primary,
    unfocusedBorderColor = Outline,
    focusedLabelColor = Primary,
    unfocusedLabelColor = OnSurface,
    cursorColor = Primary,
    focusedContainerColor = SurfaceVariant,
    unfocusedContainerColor = SurfaceVariant
)
