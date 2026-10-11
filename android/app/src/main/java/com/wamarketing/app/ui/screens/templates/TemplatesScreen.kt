package com.wamarketing.app.ui.screens.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
                "Approved" -> tmpl.templateType == "WhatsApp Approved Template"
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
                    "Approved" -> 1
                    "Local" -> 2
                    else -> 0
                },
                containerColor = Color.Transparent
            ) {
                Tab(selected = selectedTypeFilter == "All", onClick = { selectedTypeFilter = "All" }, text = { Text("All (${templates.size})") })
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isOfficial, onCheckedChange = { isOfficial = it })
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
                            status = if (isOfficial) "APPROVED" else "DRAFT",
                            variables = listOf("first_name", "company")
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
