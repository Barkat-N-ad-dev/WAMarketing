package com.wamarketing.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import com.wamarketing.app.model.WhatsAppAccountItem
import com.wamarketing.app.model.WalletTransactionItem
import com.wamarketing.app.data.AppConfig
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.StatusBadge
import com.wamarketing.app.ui.screens.wallet.WalletRechargeDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    accounts: List<WhatsAppAccountItem>,
    walletBalance: Double = 50.0,
    walletTransactions: List<WalletTransactionItem> = emptyList(),
    onRechargeWallet: (Double, String) -> Unit = { _, _ -> },
    onConnectAccount: (WhatsAppAccountItem) -> Unit,
    onSetDefaultAccount: (Int) -> Unit = {},
    onDisconnectAccount: (Int) -> Unit = {},
    onRestoreAccount: (Int) -> Unit = {},
    onSyncAccount: (Int) -> Unit = {},
    onResetClientState: () -> Unit = {},
    onPurgeDemoData: () -> Unit = {},
    onUpdateBackendUrl: (String) -> Unit = {},
    onUpdateGeminiKey: (String) -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("wamarketing_persistent_store", android.content.Context.MODE_PRIVATE) }
    var backendUrlInput by remember { mutableStateOf(AppConfig.getBackendUrl(prefs)) }
    var geminiKeyInput by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var connectionTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    var showConnectDialog by remember { mutableStateOf(false) }
    var showWalletDialog by remember { mutableStateOf(false) }
    var stopKeywordText by remember { mutableStateOf("STOP, UNSUBSCRIBE, REMOVE, QUIT, CANCEL") }
    var notificationEnabled by remember { mutableStateOf(true) }
    var testWebhookStatus by remember { mutableStateOf<String?>(null) }
    var isTestingWebhook by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val activeAccounts = remember(accounts) { accounts.filter { it.status != "ARCHIVED" } }
    val archivedAccounts = remember(accounts) { accounts.filter { it.status == "ARCHIVED" } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Settings & Configuration",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                "Manage official WhatsApp Business numbers, profiles, and compliance rules",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            // WhatsApp Accounts Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Official WhatsApp Numbers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Meta Cloud API v19.0 Integration", style = MaterialTheme.typography.labelSmall, color = WhatsAppGreen)
                        }
                        Button(
                            onClick = { showConnectDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00381B))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Connect Number", color = Color(0xFF00381B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (activeAccounts.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("No active WhatsApp number connected", fontWeight = FontWeight.SemiBold)
                                Text("Connect an official Phone Number ID to start messaging customers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        activeAccounts.forEach { acc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Profile Avatar
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(WhatsAppGreen.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = WhatsAppGreen,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    acc.profileName ?: acc.businessName,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                if (acc.isDefault) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    StatusBadge("DEFAULT", "success")
                                                }
                                            }
                                            Text(
                                                acc.phoneNumber,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        StatusBadge(acc.verificationStatus, "success")
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Metadata Row: Quality Rating, Phone ID, WABA ID
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Phone ID: ${acc.phoneNumberId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "Quality: ${acc.qualityRating}",
                                                color = Color(0xFF10B981),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "WABA ID: ${acc.wabaId} • Connected: ${acc.connectedAt}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Action Buttons
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = { onSyncAccount(acc.id) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sync", style = MaterialTheme.typography.labelSmall)
                                        }

                                        if (!acc.isDefault) {
                                            OutlinedButton(
                                                onClick = { onSetDefaultAccount(acc.id) },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                Text("Set Default", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { onDisconnectAccount(acc.id) },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1.4f)
                                        ) {
                                            Text("Archive / Hide", style = MaterialTheme.typography.labelSmall, color = AccentAmber)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ARCHIVED / HIDDEN NUMBERS (Preserved Analytics & Data)
        if (archivedAccounts.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Archived Numbers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("Historical Analytics & Logs Preserved", style = MaterialTheme.typography.labelSmall, color = AccentAmber)
                            }
                            StatusBadge("${archivedAccounts.size} Archived", "amber")
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "These numbers are disconnected from active campaigns to avoid clutter, but their message history, recipient analytics, and CRM activities remain completely intact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        archivedAccounts.forEach { acc ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(acc.profileName ?: acc.businessName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(acc.phoneNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Phone ID: ${acc.phoneNumberId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }

                                    Button(
                                        onClick = { onRestoreAccount(acc.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restore", color = Color(0xFF00381B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            // Meta Messaging Wallet Balance & Recharge Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = Color(0xFF00381B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Meta Messaging Wallet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Official Meta Cloud API Usage Balance", style = MaterialTheme.typography.labelSmall, color = WhatsAppGreen)
                            }
                        }
                        StatusBadge(if (walletBalance > 5.0) "Active" else "Low Balance", if (walletBalance > 5.0) "success" else "warning")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("CURRENT BALANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "$%.2f USD".format(Locale.US, walletBalance),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = WhatsAppDarkGreen
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Capacity: ~%d Marketing / ~%d Utility msg".format((walletBalance / 0.05).toInt(), (walletBalance / 0.02).toInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { showWalletDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Recharge", color = Color(0xFF00381B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Supported: UPI • Card • Stripe • PayPal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(
                            onClick = { showWalletDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Rates & History →", style = MaterialTheme.typography.labelSmall, color = WhatsAppDarkGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Official Meta Cloud API Webhook Setup (Zero PC Required - Cloud Hosted)
            val currentBackend = backendUrlInput.trim().removeSuffix("/")
            val webhookUrl = "$currentBackend/api/v1/webhooks/whatsapp"
            val verifyToken = "wamarketing_secure_verify_token"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("2-Way Messaging Webhook", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Cloud Hosted • No PC Required", style = MaterialTheme.typography.labelSmall, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
                        }
                        StatusBadge("2-Way Live", "success")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Your server is hosted in the cloud 24/7. To receive customer replies and status updates, copy and paste these into your Meta WhatsApp App:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Webhook URL Copy Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("CALLBACK URL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                webhookUrl,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = WhatsAppDarkGreen
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("WhatsApp Webhook Callback URL", webhookUrl)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Webhook URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Callback URL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Verify Token Copy Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("VERIFY TOKEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                verifyToken,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = WhatsAppDarkGreen
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("WhatsApp Webhook Verify Token", verifyToken)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Verify Token copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Verify Token", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Quick Meta Setup (Zero PC Needed):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Text("1. Open Meta Developer Portal -> WhatsApp -> Configuration -> Webhook.", style = MaterialTheme.typography.labelSmall)
                            Text("2. Click 'Edit', paste Callback URL & Verify Token from above, then 'Verify and save'.", style = MaterialTheme.typography.labelSmall)
                            Text("3. Under Webhook fields, click 'Manage' and subscribe to 'messages'.", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                isTestingWebhook = true
                                testWebhookStatus = null
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val targetUrl = backendUrlInput.trim().removeSuffix("/")
                                        val handshakeUrl = URL("$targetUrl/api/v1/webhooks/whatsapp?hub.mode=subscribe&hub.challenge=test_meta_challenge_12345&hub.verify_token=wamarketing_secure_verify_token")
                                        val conn = handshakeUrl.openConnection() as HttpURLConnection
                                        AppConfig.applyStandardHeaders(conn)
                                        conn.requestMethod = "GET"
                                        conn.connectTimeout = 6000
                                        conn.readTimeout = 6000
                                        val code = conn.responseCode
                                        val resp = if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else ""
                                        withContext(Dispatchers.Main) {
                                            isTestingWebhook = false
                                            if (code in 200..299 && resp.trim() == "test_meta_challenge_12345") {
                                                testWebhookStatus = "Success! Meta Webhook handshake verified (HTTP 200)."
                                            } else {
                                                testWebhookStatus = "Handshake HTTP $code: $resp"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isTestingWebhook = false
                                            testWebhookStatus = "Handshake error: ${e.message}"
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            enabled = !isTestingWebhook,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isTestingWebhook) "Verifying..." else "Verify Handshake",
                                color = Color(0xFF00381B),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingWebhook = true
                                testWebhookStatus = null
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val targetUrl = backendUrlInput.trim().removeSuffix("/")
                                        val url = URL("$targetUrl/api/v1/inbox/simulate-inbound")
                                        val conn = url.openConnection() as HttpURLConnection
                                        AppConfig.applyStandardHeaders(conn)
                                        conn.requestMethod = "POST"
                                        conn.setRequestProperty("Content-Type", "application/json")
                                        conn.doOutput = true
                                        conn.connectTimeout = 6000
                                        conn.readTimeout = 6000

                                        val json = JSONObject().apply {
                                            put("from_phone", "+923093697069")
                                            put("body", "Test verification: Inbound customer reply pipeline is working perfectly!")
                                            put("sender_name", "Live Test Customer")
                                        }
                                        conn.outputStream.use { it.write(json.toString().toByteArray()) }
                                        val code = conn.responseCode
                                        withContext(Dispatchers.Main) {
                                            isTestingWebhook = false
                                            if (code in 200..299) {
                                                testWebhookStatus = "Success! Simulated customer reply delivered to Inbox."
                                            } else {
                                                testWebhookStatus = "Server responded with HTTP $code"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isTestingWebhook = false
                                            testWebhookStatus = "Error: ${e.message}"
                                        }
                                    }
                                }
                            },
                            enabled = !isTestingWebhook,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simulate Message", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    testWebhookStatus?.let { statusMsg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            statusMsg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (statusMsg.startsWith("Success")) WhatsAppGreen else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            // Opt-Out & Compliance Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Opt-Out & Unsubscribe Policy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "WhatsApp policies strictly require automated opt-out handling. Inbound messages with these keywords automatically update contact status to 'opted_out' and add to the suppression list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = stopKeywordText,
                        onValueChange = { stopKeywordText = it },
                        label = { Text("Unsubscribe Keywords (Comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            // Server API Endpoint & Network Isolation Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Server & Network Configuration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Configure custom Server IP or Cloud Host", style = MaterialTheme.typography.labelSmall, color = WhatsAppGreen)
                        }
                        StatusBadge("Multi-Tenant", "success")
                    }

                    Text(
                        "Clients can specify their own backend IP or cloud domain. Leaving empty or standalone will run directly on device without connecting to private local networks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = backendUrlInput,
                        onValueChange = { backendUrlInput = it },
                        label = { Text("Backend Server URL / IP") },
                        placeholder = { Text("https://des-frontpage-finally-congressional.trycloudflare.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val clean = backendUrlInput.trim().removeSuffix("/")
                                onUpdateBackendUrl(clean)
                                isTestingConnection = true
                                connectionTestStatus = null
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val url = URL("$clean/api/v1/health")
                                        val conn = url.openConnection() as HttpURLConnection
                                        AppConfig.applyStandardHeaders(conn)
                                        conn.requestMethod = "GET"
                                        conn.connectTimeout = 6000
                                        conn.readTimeout = 6000
                                        val code = conn.responseCode
                                        withContext(Dispatchers.Main) {
                                            isTestingConnection = false
                                            connectionTestStatus = if (code in 200..299) "Connected successfully! (HTTP $code)" else "Server returned HTTP $code"
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isTestingConnection = false
                                            connectionTestStatus = "Connection error: ${e.message}"
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isTestingConnection) "Testing..." else "Save & Test URL", color = Color(0xFF00381B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    connectionTestStatus?.let { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (status.startsWith("Connected")) WhatsAppGreen else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            // Google Gemini AI Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Google Gemini AI (BS Bot)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Powers real-time campaign writer & copywriting", style = MaterialTheme.typography.labelSmall, color = WhatsAppGreen)
                        }
                        StatusBadge(if (geminiKeyInput.isNotBlank()) "Gemini Active" else "Offline Mode", if (geminiKeyInput.isNotBlank()) "success" else "amber")
                    }

                    Text(
                        "Enter your Gemini API key to unlock full conversational intelligence for BS Bot. Keys are stored locally on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = geminiKeyInput,
                        onValueChange = { geminiKeyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            onUpdateGeminiKey(geminiKeyInput.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Gemini API Key", color = Color(0xFF00381B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            // Client Isolation & Account Reset Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Client Privacy & Account Isolation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Each device maintains its own isolated WhatsApp credentials and campaign data. When distributing the APK to clients, connected accounts are never shared.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            onResetClientState()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentAmber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Local Accounts (Reset for Client Distribution)", color = AccentAmber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = {
                            onPurgeDemoData()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Purge Demo Contacts & Clear Seed Data", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            // System Notifications Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Campaign & Message Alerts", fontWeight = FontWeight.Bold)
                        Text("Notify when campaign finishes or reply arrives", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = notificationEnabled, onCheckedChange = { notificationEnabled = it })
                }
            }
        }

        // About Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("About WAMarketing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("WA Marketing — WhatsApp Marketing SaaS", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                    Text("Developed by Barkat Ali", style = MaterialTheme.typography.bodyMedium, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
                    Text("Official Portfolio: https://barkatai.ihsanraza.com/#about", style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen)
                    Text("Version: v1.0.0 (Production Release)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Architecture: Jetpack Compose • FastAPI • PostgreSQL • Meta Cloud API v19.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("© 2026 Barkat Ali. All rights reserved.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
            }
        }

        item {
            // Logout
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = AccentRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout from WAMarketing")
            }
            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    if (showConnectDialog) {
        ConnectWhatsAppModal(
            onDismiss = { showConnectDialog = false },
            onConnect = { acc ->
                onConnectAccount(acc)
                showConnectDialog = false
            }
        )
    }

    if (showWalletDialog) {
        WalletRechargeDialog(
            currentBalance = walletBalance,
            transactions = walletTransactions,
            onDismiss = { showWalletDialog = false },
            onRecharge = { amount, method ->
                onRechargeWallet(amount, method)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectWhatsAppModal(
    onDismiss: () -> Unit,
    onConnect: (WhatsAppAccountItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var businessName by remember { mutableStateOf("Test number") }
    var phoneNumber by remember { mutableStateOf("+1 555 197 7671") }
    var phoneNumberId by remember { mutableStateOf("1181137018426157") }
    var wabaId by remember { mutableStateOf("2080401999220434") }
    var accessToken by remember { mutableStateOf("") }

    var isVerifying by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<String?>(null) }
    var isVerifiedSuccess by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect WhatsApp Business Number", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter your Meta Developer credentials or verify your Cloud API setup:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business / Account Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("WhatsApp Phone Number (E.164)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phoneNumberId,
                    onValueChange = { phoneNumberId = it },
                    label = { Text("Phone Number ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = wabaId,
                    onValueChange = { wabaId = it },
                    label = { Text("WhatsApp Business Account (WABA) ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it },
                    label = { Text("Permanent Access Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Verification Button
                OutlinedButton(
                    onClick = {
                        isVerifying = true
                        verificationResult = null
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val url = URL("https://graph.facebook.com/v19.0/$phoneNumberId?fields=verified_name,display_phone_number,quality_rating")
                                    val conn = url.openConnection() as HttpURLConnection
                                    conn.requestMethod = "GET"
                                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                                    conn.connectTimeout = 10000
                                    conn.readTimeout = 10000
                                    val code = conn.responseCode
                                    if (code in 200..299) {
                                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                                        val json = JSONObject(text)
                                        val displayPhone = json.optString("display_phone_number", "")
                                        val vName = json.optString("verified_name", "")
                                        val rating = json.optString("quality_rating", "GREEN")
                                        Triple(true, "Meta API Verified: $displayPhone (Quality: $rating)", displayPhone to vName)
                                    } else {
                                        val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                                        Triple(false, "Authentication Error ($code): Invalid token or ID", "" to "")
                                    }
                                } catch (e: Exception) {
                                    Triple(false, "Network error: ${e.message}", "" to "")
                                }
                            }
                            isVerifying = false
                            isVerifiedSuccess = result.first
                            verificationResult = result.second
                            if (result.first) {
                                if (result.third.first.isNotBlank()) phoneNumber = result.third.first
                                if (result.third.second.isNotBlank()) businessName = result.third.second
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Meta Credentials...")
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = WhatsAppGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test & Verify with Meta API")
                    }
                }

                if (verificationResult != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isVerifiedSuccess == true) WhatsAppDarkGreen.copy(alpha = 0.2f) else AccentRed.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = verificationResult!!,
                            color = if (isVerifiedSuccess == true) WhatsAppGreen else AccentRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newAcc = WhatsAppAccountItem(
                        id = (100..999).random(),
                        businessName = businessName,
                        phoneNumber = phoneNumber,
                        wabaId = wabaId,
                        phoneNumberId = phoneNumberId,
                        status = "CONNECTED",
                        isDefault = true,
                        connectedAt = "Just now",
                        profileName = businessName,
                        qualityRating = "GREEN",
                        verificationStatus = "VERIFIED",
                        accessToken = accessToken
                    )
                    onConnect(newAcc)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
            ) {
                Text("Connect", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
