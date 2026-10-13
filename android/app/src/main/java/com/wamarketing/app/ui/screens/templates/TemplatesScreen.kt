package com.wamarketing.app.ui.screens.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wamarketing.app.model.TemplateItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.EmptyStateView
import com.wamarketing.app.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    templates: List<TemplateItem>,
    onCreateTemplate: (TemplateItem) -> Unit,
    onSyncMeta: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedTypeFilter by remember { mutableStateOf("All") } // All, Approved, Local
    var showCreateDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Marketing", "Utility", "Authentication", "Follow-up", "Welcome", "Appointment")

    val filteredTemplates = remember(selectedCategory, selectedTypeFilter, templates) {
        templates.filter { tmpl ->
            val matchesCategory = selectedCategory == "All" || tmpl.category.equals(selectedCategory, ignoreCase = true)
            val matchesType = when (selectedTypeFilter) {
                "Pending" -> tmpl.status == "PENDING"
                "Approved" -> tmpl.status == "APPROVED"
                "Local" -> tmpl.templateType == "Local App Template"
                else -> true
            }
            matchesCategory && matchesType
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = WhatsAppGreen,
                contentColor = Color(0xFF00381B)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Template")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Template Library", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Compliant WhatsApp & local draft templates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSyncMeta,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync Meta", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Meta", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", color = Color(0xFF00381B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Type filter tabs (All, WhatsApp Approved, Local App)
            TabRow(
                selectedTabIndex = when (selectedTypeFilter) {
                    "Pending" -> 1
                    "Approved" -> 2
                    "Local" -> 3
                    else -> 0
                },
                containerColor = Color.Transparent
            ) {
                Tab(selected = selectedTypeFilter == "All", onClick = { selectedTypeFilter = "All" }, text = { Text("All (${templates.size})") })
                val pendingCount = templates.count { it.status == "PENDING" }
                Tab(selected = selectedTypeFilter == "Pending", onClick = { selectedTypeFilter = "Pending" }, text = { Text("Pending${if (pendingCount > 0) " ($pendingCount)" else ""}") })
                Tab(selected = selectedTypeFilter == "Approved", onClick = { selectedTypeFilter = "Approved" }, text = { Text("Meta Approved") })
                Tab(selected = selectedTypeFilter == "Local", onClick = { selectedTypeFilter = "Local" }, text = { Text("Local Drafts") })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Categories horizontal bar
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredTemplates.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Email,
                    title = "No templates found",
                    message = "Create approved business message templates to begin outreach.",
                    actionButtonText = "Create Template",
                    onActionClick = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTemplates, key = { it.id }) { template ->
                        TemplateCardItem(template = template)
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTemplateDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { tmpl ->
                onCreateTemplate(tmpl)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun TemplateCardItem(template: TemplateItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
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
                    Text(template.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge(
                            text = template.category,
                            statusType = if (template.category.equals("Marketing", ignoreCase = true)) "purple" else "blue"
                        )
                        Text(template.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StatusBadge(
                    text = if (template.status == "APPROVED") "Meta Approved" else template.status,
                    statusType = if (template.status == "APPROVED") "success" else "amber"
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = template.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (template.variables.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    template.variables.forEach { v ->
                        StatusBadge(v, "blue")
                    }
                }
            }
            if (!template.buttonText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { /* preview button */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = WhatsAppGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${template.buttonText} (${template.buttonUrl ?: "Link"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = WhatsAppGreen
                    )
                }
            }
        }
    }
}

@Composable
fun CreateTemplateDialog(
    onDismiss: () -> Unit,
    onConfirm: (TemplateItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Marketing") } // "Marketing" or "Utility" or "Authentication"
    var content by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en_US") }
    var isOfficial by remember { mutableStateOf(true) }
    var addWebsiteButton by remember { mutableStateOf(false) }
    var buttonText by remember { mutableStateOf("Visit Website") }
    var buttonUrl by remember { mutableStateOf("https://") }

    val officialCategories = listOf("Marketing", "Utility", "Authentication")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Create Message Template", fontWeight = FontWeight.Bold)
                Text(
                    "Syncs with Meta WhatsApp Business Manager",
                    style = MaterialTheme.typography.bodySmall,
                    color = WhatsAppGreen
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.lowercase().replace(" ", "_").filter { ch -> ch.isLetterOrDigit() || ch == '_' }
                    },
                    label = { Text("Template Name (lowercase & _ only)") },
                    placeholder = { Text("e.g. seasonal_promo_v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    officialCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    if (category == "Marketing") "Marketing: Promotions, product announcements, discount offers."
                    else if (category == "Utility") "Utility: Order alerts, account updates, receipts, notifications."
                    else "Authentication: One-time passwords and verification codes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Template Body Text") },
                    placeholder = { Text("Hello {{first_name}}, check out our latest update for {{company}}!") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                // Variable insertion chips
                Text("Insert Variable:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val quickVars = listOf("{{first_name}}", "{{company}}", "{{city}}")
                    quickVars.forEach { v ->
                        SuggestionChip(
                            onClick = { content += " $v" },
                            label = { Text(v, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Website Button Option
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = addWebsiteButton, onCheckedChange = { addWebsiteButton = it })
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Website Button (CTA URL)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }

                        if (addWebsiteButton) {
                            OutlinedTextField(
                                value = buttonText,
                                onValueChange = { buttonText = it },
                                label = { Text("Button Text (max 25 chars)") },
                                placeholder = { Text("e.g. Visit Website / Download Now") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = buttonUrl,
                                onValueChange = { buttonUrl = it },
                                label = { Text("Website Link URL") },
                                placeholder = { Text("https://yourwebsite.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isOfficial, onCheckedChange = { isOfficial = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Submit to Meta WhatsApp Business Management", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && content.isNotBlank()) {
                        val cleanName = name.trim().lowercase().replace(" ", "_").filter { ch -> ch.isLetterOrDigit() || ch == '_' }
                        val newTmpl = TemplateItem(
                            id = (1000..9999).random(),
                            name = cleanName,
                            category = category,
                            language = language,
                            content = content,
                            templateType = if (isOfficial) "WhatsApp Approved Template" else "Local App Template",
                            status = if (isOfficial) "PENDING" else "DRAFT",
                            variables = listOf("first_name", "company"),
                            buttonText = if (addWebsiteButton && buttonText.isNotBlank()) buttonText.trim() else null,
                            buttonUrl = if (addWebsiteButton && buttonUrl.isNotBlank()) buttonUrl.trim() else null
                        )
                        onConfirm(newTmpl)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                enabled = name.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save Template", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
