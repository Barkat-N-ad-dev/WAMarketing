package com.wamarketing.app.ui.screens.crm

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
import com.wamarketing.app.model.LeadItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmPipelineScreen(
    leads: List<LeadItem>,
    modifier: Modifier = Modifier
) {
    val stages = listOf("New Lead", "Contacted", "Interested", "Follow-up", "Negotiation", "Customer", "Closed")
    var selectedStageFilter by remember { mutableStateOf("All") }
    var selectedLeadToMove by remember { mutableStateOf<LeadItem?>(null) }

    val leadsList = remember { mutableStateListOf(*leads.toTypedArray()) }

    val totalPipelineValue = remember(leadsList) {
        leadsList.sumOf { it.dealValue }
    }

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
                Text("CRM Pipeline", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("Total Pipeline Value: $${"%,.0f".format(totalPipelineValue)}", style = MaterialTheme.typography.bodyMedium, color = WhatsAppGreen, fontWeight = FontWeight.SemiBold)
            }
            StatusBadge("${leadsList.size} Leads", "blue")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Stage Selector Tabs
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedStageFilter == "All",
                    onClick = { selectedStageFilter = "All" },
                    label = { Text("All Stages") }
                )
            }
            items(stages) { stage ->
                val countInStage = leadsList.count { it.stage.equals(stage, ignoreCase = true) }
                FilterChip(
                    selected = selectedStageFilter == stage,
                    onClick = { selectedStageFilter = stage },
                    label = { Text("$stage ($countInStage)") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val displayedLeads = remember(selectedStageFilter, leadsList) {
            if (selectedStageFilter == "All") leadsList
            else leadsList.filter { it.stage.equals(selectedStageFilter, ignoreCase = true) }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(displayedLeads, key = { it.id }) { lead ->
                LeadCardItem(
                    lead = lead,
                    onMoveStage = { selectedLeadToMove = lead }
                )
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    selectedLeadToMove?.let { lead ->
        MoveLeadStageDialog(
            lead = lead,
            stages = stages,
            onDismiss = { selectedLeadToMove = null },
            onSelectStage = { newStage ->
                val idx = leadsList.indexOfFirst { it.id == lead.id }
                if (idx != -1) {
                    leadsList[idx] = lead.copy(stage = newStage)
                }
                selectedLeadToMove = null
            }
        )
    }
}

@Composable
fun LeadCardItem(
    lead: LeadItem,
    onMoveStage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lead.contactName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$${"%,.0f".format(lead.dealValue)}",
                    fontWeight = FontWeight.Bold,
                    color = WhatsAppGreen,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${lead.phoneNumber} ${lead.company?.let { "• $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (lead.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(lead.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = lead.stage,
                    statusType = when (lead.stage) {
                        "Customer" -> "success"
                        "Negotiation" -> "purple"
                        "Interested" -> "blue"
                        else -> "warning"
                    }
                )
                OutlinedButton(
                    onClick = onMoveStage,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Change Stage", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun MoveLeadStageDialog(
    lead: LeadItem,
    stages: List<String>,
    onDismiss: () -> Unit,
    onSelectStage: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move '${lead.contactName}' Stage", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stages.forEach { stage ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStage(stage) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (lead.stage == stage) WhatsAppGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stage, fontWeight = if (lead.stage == stage) FontWeight.Bold else FontWeight.Normal)
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
