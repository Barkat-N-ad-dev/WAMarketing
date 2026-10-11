package com.wamarketing.app.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.wamarketing.app.model.ContactItem
import com.wamarketing.app.model.TagItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.EmptyStateView
import com.wamarketing.app.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contacts: List<ContactItem>,
    onAddContact: (ContactItem) -> Unit,
    onDeleteContact: (Int) -> Unit,
    onOpenImport: () -> Unit,
    onSendMessageToContact: (ContactItem, String, String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedContactForDetail by remember { mutableStateOf<ContactItem?>(null) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var isBulkMode by remember { mutableStateOf(false) }

    val tagsList = listOf("All", "New Lead", "Hot Lead", "Customer", "VIP", "Dubai", "Pakistan", "Saudi Arabia")

    val filteredContacts = remember(searchQuery, selectedTag, contacts) {
        contacts.filter { contact ->
            val matchesSearch = searchQuery.isEmpty() ||
                contact.fullName.contains(searchQuery, ignoreCase = true) ||
                contact.phoneNumber.contains(searchQuery) ||
                (contact.company?.contains(searchQuery, ignoreCase = true) == true) ||
                (contact.city?.contains(searchQuery, ignoreCase = true) == true)

            val matchesTag = selectedTag == null || selectedTag == "All" ||
                contact.tags.any { it.name.equals(selectedTag, ignoreCase = true) } ||
                contact.leadStatus.equals(selectedTag, ignoreCase = true) ||
                contact.country.equals(selectedTag, ignoreCase = true)

            matchesSearch && matchesTag
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = WhatsAppGreen,
                contentColor = Color(0xFF00381B)
            ) {
                Icon(Icons.Default.Person, contentDescription = "Add Contact")
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

            // Search Bar & Import Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, phone, city...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onOpenImport,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import CSV", tint = WhatsAppGreen)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tag Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tagsList) { tag ->
                    val isSelected = (selectedTag == null && tag == "All") || (selectedTag == tag)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTag = if (tag == "All") null else tag },
                        label = { Text(tag) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WhatsAppGreen.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Bulk actions banner
            if (isBulkMode && selectedIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${selectedIds.size} selected", fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(onClick = {
                                selectedIds.forEach { onDeleteContact(it) }
                                selectedIds.clear()
                                isBulkMode = false
                            }) {
                                Text("Delete", color = AccentRed)
                            }
                            TextButton(onClick = {
                                selectedIds.clear()
                                isBulkMode = false
                            }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Contact List
            if (filteredContacts.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Person,
                    title = "No contacts found",
                    message = if (searchQuery.isNotEmpty()) "Try adjusting your search criteria." else "Start adding contacts or import via CSV.",
                    actionButtonText = "Add Contact",
                    onActionClick = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredContacts, key = { it.id }) { contact ->
                        ContactCardItem(
                            contact = contact,
                            isSelected = selectedIds.contains(contact.id),
                            onSelectToggle = {
                                isBulkMode = true
                                if (selectedIds.contains(contact.id)) selectedIds.remove(contact.id)
                                else selectedIds.add(contact.id)
                            },
                            onClick = { selectedContactForDetail = contact },
                            onDelete = { onDeleteContact(contact.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    // Add Contact Dialog
    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newContact ->
                onAddContact(newContact)
                showAddDialog = false
            }
        )
    }

    // Contact Detail Bottom Sheet
    selectedContactForDetail?.let { contact ->
        ContactDetailDialog(
            contact = contact,
            onDismiss = { selectedContactForDetail = null },
            onDelete = {
                onDeleteContact(contact.id)
                selectedContactForDetail = null
            },
            onSendMessage = { c, text, tmpl ->
                onSendMessageToContact(c, text, tmpl)
                selectedContactForDetail = null
            }
        )
    }
}

@Composable
fun ContactCardItem(
    contact: ContactItem,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(WhatsAppDarkGreen.copy(alpha = 0.15f))
                    .clickable(onClick = onSelectToggle),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = WhatsAppGreen)
                } else {
                    Text(
                        text = contact.firstName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = WhatsAppGreen,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.fullName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (contact.optInStatus == "opted_out") {
                        StatusBadge("OPT-OUT", "danger")
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!contact.company.isNullOrEmpty() || !contact.city.isNullOrEmpty()) {
                    Text(
                        text = listOfNotNull(contact.company, contact.city).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (contact.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        contact.tags.take(3).forEach { t ->
                            StatusBadge(t.name, "neutral")
                        }
                    }
                }
            }
            StatusBadge(contact.leadStatus, "warning")
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (ContactItem) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var tagText by remember { mutableStateOf("New Lead") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Contact", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (with +country code) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (firstName.isNotBlank() && phone.isNotBlank()) {
                        val newContact = ContactItem(
                            id = (1000..9999).random(),
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            phoneNumber = phone.trim(),
                            whatsappNumber = phone.trim(),
                            email = email.trim().ifEmpty { null },
                            company = company.trim().ifEmpty { null },
                            city = city.trim().ifEmpty { null },
                            country = country.trim().ifEmpty { null },
                            leadStatus = "New Lead",
                            tags = listOf(TagItem(1, tagText, "#25D366"))
                        )
                        onConfirm(newContact)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                enabled = firstName.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Save Contact", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ContactDetailDialog(
    contact: ContactItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSendMessage: (ContactItem, String, String?) -> Unit = { _, _, _ -> }
) {
    var showSendModal by remember { mutableStateOf(false) }

    if (showSendModal) {
        SendContactMessageModal(
            contact = contact,
            onDismiss = { showSendModal = false },
            onSend = { text, templateName ->
                onSendMessage(contact, text, templateName)
                showSendModal = false
                onDismiss()
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(WhatsAppDarkGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(contact.firstName.take(1), fontWeight = FontWeight.Bold, color = WhatsAppGreen)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(contact.fullName, fontWeight = FontWeight.Bold)
                        Text(contact.leadStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phone: ${contact.phoneNumber}", fontWeight = FontWeight.Medium)
                    contact.email?.let { Text("Email: $it") }
                    contact.company?.let { Text("Company: $it") }
                    contact.city?.let { Text("Location: $it, ${contact.country ?: ""}") }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Consent: ")
                        StatusBadge(
                            text = if (contact.optInStatus == "opted_in") "Opted-In" else "Opted-Out",
                            statusType = if (contact.optInStatus == "opted_in") "success" else "danger"
                        )
                    }
                    if (contact.tags.isNotEmpty()) {
                        Text("Tags: ${contact.tags.joinToString { it.name }}")
                    }

                    if (contact.optInStatus == "opted_out") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentRed.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                "Contact has opted out. Messaging is prohibited under WhatsApp anti-spam policies.",
                                color = AccentRed,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (contact.optInStatus == "opted_in") {
                        Button(
                            onClick = { showSendModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00381B))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send Message", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
            },
            dismissButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = AccentRed)
                }
            }
        )
    }
}

@Composable
fun SendContactMessageModal(
    contact: ContactItem,
    onDismiss: () -> Unit,
    onSend: (text: String, templateName: String?) -> Unit
) {
    var isTemplateMode by remember { mutableStateOf(true) }
    val templates = listOf(
        "hello_world" to "Hello World! Welcome and thank you for choosing our business. (Meta Cloud API Pre-approved)",
        "welcome_intro_offer" to "Hello ${contact.firstName}, welcome! Reply OFFER to see exclusive discounts.",
        "consultation_follow_up" to "Hi ${contact.firstName}, following up on our recent conversation. Would tomorrow work for a quick demo?",
        "appointment_reminder_v1" to "Dear ${contact.firstName}, reminder for your upcoming briefing. Reply CONFIRM to verify."
    )
    var selectedTemplateIndex by remember { mutableStateOf(0) }
    var customMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Message ${contact.firstName}", fontWeight = FontWeight.Bold)
                Text(contact.phoneNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Notice banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "WhatsApp Cloud API requires an approved template when starting a conversation with new contacts or after 24 hours of inactivity.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isTemplateMode,
                        onClick = { isTemplateMode = true },
                        label = { Text("Template (Recommended)") }
                    )
                    FilterChip(
                        selected = !isTemplateMode,
                        onClick = { isTemplateMode = false },
                        label = { Text("Freeform Text") }
                    )
                }

                if (isTemplateMode) {
                    Text("Select Approved Template:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    templates.forEachIndexed { index, (name, preview) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTemplateIndex = index },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedTemplateIndex == index) WhatsAppGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (selectedTemplateIndex == index) CardDefaults.outlinedCardBorder() else null
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedTemplateIndex == index,
                                        onClick = { selectedTemplateIndex = index }
                                    )
                                    Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        label = { Text("Custom WhatsApp Message") },
                        placeholder = { Text("Type outbound message...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isTemplateMode) {
                        val (tmplName, tmplText) = templates[selectedTemplateIndex]
                        onSend(tmplText, tmplName)
                    } else {
                        if (customMessage.isNotBlank()) {
                            onSend(customMessage.trim(), null)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                enabled = isTemplateMode || customMessage.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00381B))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
