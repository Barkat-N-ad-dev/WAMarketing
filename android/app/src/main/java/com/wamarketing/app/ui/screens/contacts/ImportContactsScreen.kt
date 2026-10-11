package com.wamarketing.app.ui.screens.contacts

import android.content.Context
import android.net.Uri
import com.wamarketing.app.data.AppConfig
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.wamarketing.app.data.CsvLeadParser
import com.wamarketing.app.model.ContactItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.StatusBadge

@Composable
fun ImportContactsScreen(
    onBack: () -> Unit,
    onImportContacts: (List<ContactItem>) -> Unit = {},
    onImportComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) } // 1: Pick / Paste, 2: Preview & Import, 3: Success Result
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var parsedContacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var rawText by remember { mutableStateOf("") }
    var isPastingMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Read configured server URL from preferences
    val prefs = remember { context.getSharedPreferences("wamarketing_persistent_store", Context.MODE_PRIVATE) }
    val backendUrl = remember { AppConfig.getBackendUrl(prefs) }

    // Native Android device file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            var displayName = "contacts.csv"
            try {
                val cursor = context.contentResolver.query(fileUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) displayName = it.getString(nameIdx)
                    }
                }
            } catch (_: Exception) {}

            selectedFileName = displayName
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                val contacts = CsvLeadParser.parseFromUriWithBackend(context, fileUri, displayName, backendUrl)
                isLoading = false
                parsedContacts = contacts
                if (contacts.isNotEmpty()) {
                    step = 2
                    errorMessage = null
                } else {
                    errorMessage = "No valid phone numbers found in '$displayName'. Please check that the file has at least 7-digit phone numbers."
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Import Leads from Device",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Extract names & phone numbers directly from phone CSV/Excel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (step) {
            1 -> {
                // Step 1: Pick from phone or paste
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = WhatsAppGreen)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Upload CSV File from Phone", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Select any .csv or .txt contact spreadsheet on your device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isLoading) "Reading File..." else "Select CSV / Excel / PDF File", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                        }

                        if (isLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = WhatsAppGreen, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Analyzing file & extracting contact records...", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        errorMessage?.let { err ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }

                        Divider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Or paste spreadsheet rows manually:", style = MaterialTheme.typography.bodySmall)
                            TextButton(
                                onClick = {
                                    rawText = "Hamza Khan, +971501234567, Apex Dynamics, Dubai\n" +
                                            "Fatima Zahra, +971559876543, Emirates Trading, Abu Dhabi\n" +
                                            "Tariq Mahmood, +923001234567, Pak Trade Co, Lahore\n" +
                                            "Zainab Ali, +966501234567, Saudi Ventures, Riyadh"
                                    parsedContacts = CsvLeadParser.parseFromText(rawText)
                                    selectedFileName = "Sample_Leads_Template.csv"
                                    step = 2
                                }
                            ) {
                                Text("Load Sample Leads", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        OutlinedTextField(
                            value = rawText,
                            onValueChange = {
                                rawText = it
                                parsedContacts = CsvLeadParser.parseFromText(it)
                            },
                            placeholder = { Text("Name, Phone Number, Company, City\nJohn Doe, +923091234567, Acme Co, Lahore") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        if (rawText.isNotBlank()) {
                            Button(
                                onClick = {
                                    if (parsedContacts.isNotEmpty()) {
                                        selectedFileName = "Pasted_Leads.csv"
                                        step = 2
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                            ) {
                                Text("Parse & Preview (${parsedContacts.size} Leads)", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            2 -> {
                // Step 2: Preview & Validation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pre-Import Lead Verification", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(selectedFileName ?: "Device File", style = MaterialTheme.typography.bodySmall, color = WhatsAppGreen)
                    }
                    StatusBadge("${parsedContacts.size} Valid Leads", "success")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(parsedContacts) { c ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${c.phoneNumber} • ${c.company} • ${c.city}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { step = 1 },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text("Back")
                    }

                    Button(
                        onClick = {
                            onImportContacts(parsedContacts)
                            step = 3
                        },
                        modifier = Modifier.weight(2f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                    ) {
                        Text("Import & Save ${parsedContacts.size} Leads", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                    }
                }
            }

            3 -> {
                // Step 3: Complete
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Import Complete!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "All ${parsedContacts.size} leads from '$selectedFileName' were imported into your CRM and Contacts with E.164 WhatsApp numbers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onImportComplete,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                ) {
                    Text("Done (View Contacts)", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
