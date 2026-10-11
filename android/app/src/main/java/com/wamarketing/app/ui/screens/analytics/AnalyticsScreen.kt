package com.wamarketing.app.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun AnalyticsScreen(
    stats: DashboardStats,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Campaign Analytics", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Delivery benchmarks & conversion insights", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge("98.4% Compliant", "success")
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Delivery Rate", "${stats.deliveryRatePercent}%", Icons.Default.CheckCircle, WhatsAppGreen, "WhatsApp tier score", Modifier.weight(1f))
                StatCard("Response Rate", "${stats.responseRatePercent}%", Icons.Default.Phone, AccentBlue, "Customer inbound replies", Modifier.weight(1f))
            }
        }

        item {
            // Visual Campaign Funnel
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Messaging Funnel Progression", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    FunnelBar("1. Sent to Queue", 100, AccentPurple, "${stats.messagesSent} messages")
                    FunnelBar("2. Delivered to Handset", (stats.deliveryRatePercent).toInt(), AccentGreen, "${stats.messagesDelivered} delivered")
                    FunnelBar("3. Read by Recipient", 72, AccentBlue, "${(stats.messagesDelivered * 0.72).toInt()} read")
                    FunnelBar("4. Customer Replied", (stats.responseRatePercent).toInt(), AccentAmber, "${stats.replies} replies")
                }
            }
        }

        item {
            // Contact Growth Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Audience Growth (Opted-in)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(14.dp))
                    stats.contactGrowth.forEach { pt ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(pt.label, style = MaterialTheme.typography.bodyMedium)
                            Text("+${pt.value.toInt()} contacts", fontWeight = FontWeight.Bold, color = WhatsAppGreen)
                        }
                    }
                }
            }
        }

        item {
            // Opt-Out & Compliance Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.1f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Suppression & Quality Rating", fontWeight = FontWeight.Bold, color = AccentRed)
                        Text(
                            "Total opt-outs: ${stats.optOuts}. Unsubscribe keywords are immediately honored to preserve Tier messaging limits.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
fun FunnelBar(label: String, percent: Int, color: Color, countText: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(countText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = percent / 100f,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
