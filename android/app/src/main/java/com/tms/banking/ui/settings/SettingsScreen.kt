package com.tms.banking.ui.settings

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tms.banking.TmsApp
import com.tms.banking.service.BankingNotificationListenerService
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
fun SettingsScreen(app: TmsApp) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app.container))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val isEnabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )?.contains(context.packageName) == true
        vm.setNotificationListenerEnabled(isEnabled)
        vm.loadSyncStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = { Text("Settings", color = OnBackground, fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { vm.loadSyncStatus() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = OnSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = "Backend Connection") {
                    OutlinedTextField(
                        value = state.urlInput,
                        onValueChange = { vm.setUrlInput(it) },
                        label = { Text("Backend URL", color = OnSurface) },
                        placeholder = { Text("http://100.x.x.x:8000", color = OnSurface.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = OutlinedTextFieldDefaults.colors(
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
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { vm.saveUrl() },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Outline)
                        ) {
                            Text("Save", color = OnBackground)
                        }
                        Button(
                            onClick = { vm.testConnection() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            enabled = state.connectionStatus != ConnectionStatus.Testing
                        ) {
                            if (state.connectionStatus == ConnectionStatus.Testing) {
                                CircularProgressIndicator(
                                    color = OnBackground,
                                    modifier = Modifier.height(18.dp).width(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Test Connection", color = OnBackground)
                            }
                        }
                    }

                    when (state.connectionStatus) {
                        ConnectionStatus.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Positive)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connection successful", color = Positive, fontSize = 13.sp)
                            }
                        }
                        ConnectionStatus.Failure -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = Negative)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connection failed", color = Negative, fontSize = 13.sp)
                            }
                        }
                        else -> {}
                    }
                }
            }

            item {
                SectionCard(title = "🏦 Emirates NBD") {
                    var showPassword by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = state.enbdUsername,
                        onValueChange = { vm.setEnbdUsername(it) },
                        label = { Text("Username / Email", color = OnSurface) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.AccountBalance, null, tint = OnSurface) },
                        colors = OutlinedTextFieldDefaults.colors(
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
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.enbdPassword,
                        onValueChange = { vm.setEnbdPassword(it) },
                        label = { Text("Password", color = OnSurface) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = OnSurface
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
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
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { vm.saveEnbdCredentials() },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Outline)
                        ) {
                            Text("Save", color = OnBackground)
                        }
                        Button(
                            onClick = { vm.syncEnbd() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            enabled = state.enbdSyncStatus != "waiting_smartpass" && state.enbdSyncStatus != "syncing"
                        ) {
                            if (state.enbdSyncStatus == "waiting_smartpass" || state.enbdSyncStatus == "syncing") {
                                CircularProgressIndicator(
                                    color = OnBackground,
                                    modifier = Modifier.height(18.dp).width(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Sync ENBD", color = OnBackground)
                            }
                        }
                    }
                    if (state.enbdSyncMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val msgColor = when (state.enbdSyncStatus) {
                            "done" -> Positive
                            "error" -> Negative
                            "waiting_smartpass" -> Color(0xFFFF9800)
                            else -> OnSurface
                        }
                        Text(state.enbdSyncMessage, color = msgColor, fontSize = 13.sp)
                    }
                    if (state.enbdHasCredentials) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🔒 Credentials encrypted & stored locally", color = OnSurface.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            }

            item {
                SectionCard(title = "🤖 AI Categorization (Kimi K 2.5)") {
                    var showKey by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = state.kimiApiKey,
                        onValueChange = { vm.setKimiApiKey(it) },
                        label = { Text("Kimi API Key", color = OnSurface) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle",
                                    tint = OnSurface
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnBackground,
                            unfocusedTextColor = OnBackground,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Outline,
                            cursorColor = Primary,
                            focusedContainerColor = SurfaceVariant,
                            unfocusedContainerColor = SurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.saveKimiApiKey() },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Outline)
                        ) {
                            Text("Save", color = OnBackground)
                        }
                        Button(
                            onClick = { vm.testKimiKey() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            enabled = state.kimiKeyStatus != "testing"
                        ) {
                            if (state.kimiKeyStatus == "testing") {
                                CircularProgressIndicator(color = OnBackground, modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Test Key", color = OnBackground)
                            }
                        }
                    }
                    if (state.kimiKeyMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val color = when (state.kimiKeyStatus) {
                            "success" -> Positive
                            "error" -> Negative
                            else -> OnSurface
                        }
                        Text(state.kimiKeyMessage, color = color, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { vm.aiCategorize() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled = !state.kimiCategorizing && state.kimiApiKey.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (state.kimiCategorizing) {
                            CircularProgressIndicator(color = OnBackground, modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Categorizing...", color = OnBackground)
                        } else {
                            Text("Categorize All Transactions", color = OnBackground, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (state.kimiCategorizeMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.kimiCategorizeMessage, color = OnSurface, fontSize = 12.sp)
                    }
                    Text("🔒 API key encrypted & stored locally", color = OnSurface.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }

            item {
                SectionCard(title = "Sync") {
                    Button(
                        onClick = { vm.triggerSync() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled = !state.isSyncing && state.backendUrl.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                color = OnBackground,
                                modifier = Modifier.height(20.dp).width(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = OnBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now", color = OnBackground, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (state.syncMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.syncMessage!!, color = OnSurface, fontSize = 13.sp)
                    }
                }
            }

            if (state.syncStatus.isNotEmpty()) {
                item {
                    SectionCard(title = "Sync Status") {
                        state.syncStatus.forEach { status ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(status.accountName, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (status.lastSyncAt != null) {
                                        Text(status.lastSyncAt, color = OnSurface, fontSize = 11.sp)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val statusColor = when (status.status.lowercase()) {
                                        "ok", "success" -> Positive
                                        "error", "failed" -> Negative
                                        else -> OnSurface
                                    }
                                    Text(status.status, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("${status.transactionsFetched} tx", color = OnSurface, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Notification Listener") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = if (state.notificationListenerEnabled) Positive else OnSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.notificationListenerEnabled) "Enabled" else "Disabled",
                                color = if (state.notificationListenerEnabled) Positive else Negative,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Forwards banking notifications to backend",
                                color = OnSurface,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (!state.notificationListenerEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Enable in System Settings", color = Primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            item {
                SectionCard(title = "App Info") {
                    InfoRow("Package", "com.tms.banking")
                    InfoRow("Min SDK", "28 (Android 9)")
                    InfoRow("Backend", state.backendUrl.ifBlank { "Not configured" })
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = OnSurface, fontSize = 13.sp)
        Text(value, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
