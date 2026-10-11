package com.wamarketing.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Home),
    CONTACTS("Contacts", Icons.Default.Person),
    CAMPAIGNS("Campaigns", Icons.Default.Send),
    TEMPLATES("Templates", Icons.Default.Email),
    INBOX("Inbox", Icons.Default.Phone),
    CRM("CRM Pipeline", Icons.Default.List),
    AUTOMATION("Automation", Icons.Default.Share),
    AI_ASSISTANT("AI Assistant", Icons.Default.Star),
    ANALYTICS("Analytics", Icons.Default.Info),
    MEDIA_LIBRARY("Media Library", Icons.Default.AddCircle),
    SETTINGS("Settings", Icons.Default.Settings)
}
