package com.wamarketing.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wamarketing.app.model.DashboardStats
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.StatCard
import com.wamarketing.app.ui.components.StatusBadge
import com.wamarketing.app.ui.navigation.AppDestination

@Composable
fun DashboardScreen(
    stats: DashboardStats,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Quick Actions Bar
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Button(
                        onClick = { onNavigate(AppDestination.CAMPAIGNS) },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Campaign", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onNavigate(AppDestination.CONTACTS) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Contact")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onNavigate(AppDestination.CONTACTS) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import CSV")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onNavigate(AppDestination.TEMPLATES) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Template")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onNavigate(AppDestination.INBOX) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Inbox")
                    }
                }
            }
        }

        item {
            // Delivery & Response Rate Gauges
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Delivery Rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${stats.deliveryRatePercent}%", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = AccentGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusBadge("Compliant Tier", "success")
                    }
                    Divider(modifier = Modifier.height(60.dp).width(1.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Response Rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${stats.responseRatePercent}%", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = AccentBlue)
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusBadge("High Engagement", "blue")
                    }
                }
            }
        }

        item {
            Text(
                text = "Key Performance Indicators",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Total Contacts", stats.totalContacts.toString(), Icons.Default.Person, WhatsAppGreen, "Audience base", Modifier.weight(1f))
                    StatCard("Active Campaigns", stats.activeCampaigns.toString(), Icons.Default.Send, AccentBlue, "Running now", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Messages Sent", stats.messagesSent.toString(), Icons.Default.Send, AccentPurple, "Outbound messages", Modifier.weight(1f))
                    StatCard("Delivered", stats.messagesDelivered.toString(), Icons.Default.Check, AccentGreen, "Confirmed delivery", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Failed", stats.messagesFailed.toString(), Icons.Default.Warning, AccentRed, "API/Carrier errors", Modifier.weight(1f))
                    StatCard("Replies", stats.replies.toString(), Icons.Default.Email, AccentAmber, "Inbound inquiries", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Leads", stats.leads.toString(), Icons.Default.List, AccentCyan, "In CRM pipeline", Modifier.weight(1f))
                    StatCard("Opt-Outs", stats.optOuts.toString(), Icons.Default.Clear, AccentRed, "Global suppression", Modifier.weight(1f))
                }
            }
        }

        item {
            // Visual chart: Messages Over Time
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
                        Text("Campaign Activity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Last 7 Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val maxVal = stats.messagesOverTime.maxOfOrNull { it.value } ?: 100f
                        stats.messagesOverTime.forEach { point ->
                            val barHeight = ((point.value / maxVal) * 90).coerceAtLeast(10f)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height(barHeight.dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(WhatsAppGreen)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(point.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
