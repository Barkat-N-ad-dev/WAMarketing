package com.wamarketing.app.ui.screens.campaigns

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.wamarketing.app.model.CampaignItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.EmptyStateView
import com.wamarketing.app.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignsScreen(
    campaigns: List<CampaignItem>,
    onCreateNewCampaign: () -> Unit,
    onLaunchCampaign: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCampaign by remember { mutableStateOf<CampaignItem?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNewCampaign,
                containerColor = WhatsAppGreen,
                contentColor = Color(0xFF00381B)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Campaign")
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
                    Text(
                        text = "WhatsApp Campaigns",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${campaigns.size} total campaigns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onCreateNewCampaign,
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Campaign", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (campaigns.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Send,
                    title = "No campaigns yet",
                    message = "Launch your first permission-based WhatsApp campaign to engage customers.",
                    actionButtonText = "Create Campaign",
                    onActionClick = onCreateNewCampaign
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(campaigns, key = { it.id }) { campaign ->
                        CampaignCardItem(
                            campaign = campaign,
                            onClick = { selectedCampaign = campaign },
                            onLaunch = { onLaunchCampaign(campaign.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    selectedCampaign?.let { camp ->
        CampaignDetailModal(
            campaign = camp,
            onDismiss = { selectedCampaign = null },
            onLaunch = {
                onLaunchCampaign(camp.id)
                selectedCampaign = null
            }
        )
    }
}

@Composable
fun CampaignCardItem(
    campaign: CampaignItem,
    onClick: () -> Unit,
    onLaunch: () -> Unit
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
                Text(
                    text = campaign.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusBadge(
                    text = campaign.status,
                    statusType = when (campaign.status) {
                        "COMPLETED" -> "success"
                        "RUNNING", "SCHEDULED" -> "warning"
                        "DRAFT" -> "neutral"
                        else -> "danger"
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = campaign.messageContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sent: ${campaign.sentCount}", style = MaterialTheme.typography.labelMedium)
                    Text("Delivered: ${campaign.deliveredCount}", color = AccentGreen, style = MaterialTheme.typography.labelMedium)
                    Text("Replies: ${campaign.replyCount}", color = AccentBlue, style = MaterialTheme.typography.labelMedium)
                    if (campaign.failedCount > 0) {
                        Text("Failed: ${campaign.failedCount}", color = AccentRed, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (campaign.status == "DRAFT" || campaign.status == "SCHEDULED") {
                    FilledTonalButton(
                        onClick = onLaunch,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Launch Now", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun CampaignDetailModal(
    campaign: CampaignItem,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(campaign.name, fontWeight = FontWeight.Bold)
                StatusBadge(campaign.status, "info")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Message Content:", fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(campaign.messageContent, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
                Divider()
                Text("Performance Breakdown:", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Total Audience:")
                    Text("${campaign.totalRecipients}", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Delivered:")
                    Text("${campaign.deliveredCount}", fontWeight = FontWeight.Bold, color = AccentGreen)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Replies Received:")
                    Text("${campaign.replyCount}", fontWeight = FontWeight.Bold, color = AccentBlue)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Opt-Outs:")
                    Text("${campaign.optOutCount}", fontWeight = FontWeight.Bold, color = AccentRed)
                }
            }
        },
        confirmButton = {
            if (campaign.status == "DRAFT") {
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                ) {
                    Text("Launch Campaign", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
