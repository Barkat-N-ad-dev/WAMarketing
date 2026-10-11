package com.wamarketing.app.ui.screens.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wamarketing.app.model.AutomationItem
import com.wamarketing.app.model.AutomationNodeItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.StatusBadge

@Composable
fun AutomationScreen(
    automations: List<AutomationItem>,
    modifier: Modifier = Modifier
) {
    val autoList = remember { mutableStateListOf(*automations.toTypedArray()) }
    var selectedAutoForGraph by remember { mutableStateOf<AutomationItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Marketing Automation", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("Server-side event workflows & auto-responders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge("Backend Queue", "success")
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(autoList, key = { it.id }) { auto ->
                AutomationCardItem(
                    automation = auto,
                    onToggle = {
                        val idx = autoList.indexOfFirst { it.id == auto.id }
                        if (idx != -1) {
                            autoList[idx] = auto.copy(isActive = !auto.isActive)
                        }
                    },
                    onClick = { selectedAutoForGraph = auto }
                )
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    selectedAutoForGraph?.let { auto ->
        VisualWorkflowModal(
            automation = auto,
            onDismiss = { selectedAutoForGraph = null }
        )
    }
}

@Composable
fun AutomationCardItem(
    automation: AutomationItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    Text(automation.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Trigger: ${automation.triggerType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = automation.isActive, onCheckedChange = { onToggle() })
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${automation.totalRuns} total runs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onClick) {
                    Text("View Workflow Graph →", style = MaterialTheme.typography.labelMedium, color = WhatsAppGreen)
                }
            }
        }
    }
}

@Composable
fun VisualWorkflowModal(
    automation: AutomationItem,
    onDismiss: () -> Unit
) {
    val sampleNodes = remember {
        listOf(
            AutomationNodeItem("1", "trigger", "Trigger: New Lead Created", "Fires when contact added via API/CSV"),
            AutomationNodeItem("2", "add_tag", "Action: Add Tag", "Apply tag 'Hot Lead'"),
            AutomationNodeItem("3", "send_whatsapp", "WhatsApp Action", "Send approved Welcome Template v1"),
            AutomationNodeItem("4", "wait", "Delay: Wait 2 Days", "Pause execution queue for 48 hours"),
            AutomationNodeItem("5", "condition", "Condition: If Customer Replied", "Check conversation inbound count > 0"),
            AutomationNodeItem("6", "update_crm", "CRM Action: Move Stage", "Progress lead to 'Interested'"),
            AutomationNodeItem("7", "end", "End Workflow", "Logs stored in PostgreSQL audit log")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = WhatsAppGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(automation.name, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Visual Execution Pipeline (Runs on Backend):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(sampleNodes) { node ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(WhatsAppDarkGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(node.id, fontWeight = FontWeight.Bold, color = WhatsAppGreen)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(node.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(node.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}
