package com.tms.banking.ui.loans

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.tms.banking.ui.theme.Outline
import com.tms.banking.ui.theme.Positive
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface
import com.tms.banking.ui.theme.SurfaceVariant

data class LoanData(
    val id: Int = 0,
    val name: String = "",
    val type: String = "credit_card_loan",
    val totalAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val monthlyPayment: Double = 0.0,
    val interestRate: Double? = null,
    val currency: String = "AED",
    val startDate: String = "",
    val endDate: String? = null,
    val dueDay: Int? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(app: TmsApp) {
    val vm: LoansViewModel = viewModel(factory = LoansViewModel.Factory(app.container))
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    val totalDebt = state.loans.filter { it.isActive }.sumOf { it.remainingAmount }
    val totalMonthly = state.loans.filter { it.isActive }.sumOf { it.monthlyPayment }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Loans & Installments", color = OnBackground, fontWeight = FontWeight.Bold) },
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
                            Text("Total Outstanding", color = OnSurface, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formatAmount(totalDebt, "AED"),
                                color = Negative,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Monthly Payment", color = OnSurface, fontSize = 11.sp)
                                    Text(formatAmount(totalMonthly, "AED"), color = OnBackground, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Active Loans", color = OnSurface, fontSize = 11.sp)
                                    Text("${state.loans.count { it.isActive }}", color = OnBackground, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                if (state.loans.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.CreditCard, null, tint = OnSurface, modifier = Modifier.height(48.dp).width(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No loans yet", color = OnSurface, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tap + to add a loan or installment", color = OnSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        }
                    }
                }

                items(state.loans) { loan ->
                    LoanCard(loan = loan, onDelete = { vm.deleteLoan(loan.id) })
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = Primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, "Add loan", tint = OnBackground)
        }
    }

    if (showAddDialog) {
        AddLoanDialog(
            onDismiss = { showAddDialog = false },
            onSave = { loan ->
                vm.addLoan(loan)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun LoanCard(loan: LoanData, onDelete: () -> Unit) {
    val progress = if (loan.totalAmount > 0) 1.0 - (loan.remainingAmount / loan.totalAmount) else 0.0
    val typeLabel = when (loan.type) {
        "personal_loan" -> "Personal Loan"
        "credit_card_loan" -> "Credit Card Loan"
        "installment" -> "Installment"
        else -> loan.type
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(loan.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(typeLabel, color = Primary, fontSize = 12.sp)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Delete", tint = OnSurface.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Positive,
                trackColor = SurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Remaining", color = OnSurface, fontSize = 11.sp)
                    Text(formatAmount(loan.remainingAmount, loan.currency), color = Negative, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monthly", color = OnSurface, fontSize = 11.sp)
                    Text(formatAmount(loan.monthlyPayment, loan.currency), color = OnBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Paid", color = OnSurface, fontSize = 11.sp)
                    Text("${(progress * 100).toInt()}%", color = Positive, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }

            if (loan.interestRate != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Interest: ${loan.interestRate}% | Due day: ${loan.dueDay ?: "-"}", color = OnSurface.copy(alpha = 0.6f), fontSize = 11.sp)
            }

            if (loan.notes != null && loan.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(loan.notes, color = OnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLoanDialog(onDismiss: () -> Unit, onSave: (LoanData) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("credit_card_loan") }
    var totalAmount by remember { mutableStateOf("") }
    var remainingAmount by remember { mutableStateOf("") }
    var monthlyPayment by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }

    val types = listOf("credit_card_loan" to "Credit Card Loan", "personal_loan" to "Personal Loan", "installment" to "Installment")
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
        focusedBorderColor = Primary, unfocusedBorderColor = Outline,
        cursorColor = Primary, focusedContainerColor = SurfaceVariant, unfocusedContainerColor = SurfaceVariant
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Loan / Installment", color = OnBackground) },
        containerColor = Surface,
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = textFieldColors)
                }
                item {
                    ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                        OutlinedTextField(
                            value = types.find { it.first == type }?.second ?: type,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = textFieldColors
                        )
                        ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            types.forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { type = value; typeExpanded = false })
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(value = totalAmount, onValueChange = { totalAmount = it }, label = { Text("Total Amount (AED)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = textFieldColors)
                }
                item {
                    OutlinedTextField(value = remainingAmount, onValueChange = { remainingAmount = it }, label = { Text("Remaining Amount (AED)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = textFieldColors)
                }
                item {
                    OutlinedTextField(value = monthlyPayment, onValueChange = { monthlyPayment = it }, label = { Text("Monthly Payment (AED)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = textFieldColors)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = interestRate, onValueChange = { interestRate = it }, label = { Text("Interest %") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = textFieldColors)
                        OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Due Day") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = textFieldColors)
                    }
                }
                item {
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(LoanData(
                        name = name,
                        type = type,
                        totalAmount = totalAmount.toDoubleOrNull() ?: 0.0,
                        remainingAmount = remainingAmount.toDoubleOrNull() ?: 0.0,
                        monthlyPayment = monthlyPayment.toDoubleOrNull() ?: 0.0,
                        interestRate = interestRate.toDoubleOrNull(),
                        startDate = java.time.LocalDate.now().toString(),
                        dueDay = dueDay.toIntOrNull(),
                        notes = notes.ifBlank { null },
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = name.isNotBlank() && totalAmount.isNotBlank()
            ) {
                Text("Save", color = OnBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurface) }
        }
    )
}
