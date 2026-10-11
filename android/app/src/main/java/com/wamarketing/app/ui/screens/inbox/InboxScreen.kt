package com.wamarketing.app.ui.screens.inbox

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.wamarketing.app.model.ContactItem
import com.wamarketing.app.model.ConversationItem
import com.wamarketing.app.model.MessageBubbleItem
import com.wamarketing.app.model.TemplateItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.EmptyStateView
import com.wamarketing.app.ui.components.StatusBadge
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    conversations: List<ConversationItem>,
    contacts: List<ContactItem> = emptyList(),
    templates: List<TemplateItem> = emptyList(),
    messagesMap: Map<Int, List<MessageBubbleItem>> = emptyMap(),
    onSendMessage: (conversationId: Int, text: String, templateName: String?, mediaUrl: String?) -> Unit = { _, _, _, _ -> },
    onSendFile: (conversationId: Int, phoneNumber: String, fileUri: Uri, caption: String?) -> Unit = { _, _, _, _ -> },
    onReceiveMessage: (fromPhone: String, text: String, mediaUrl: String?) -> Unit = { _, _, _ -> },
    onSyncBackend: () -> Unit = {},
    onFetchMessages: (convId: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedConversation by remember { mutableStateOf<ConversationItem?>(null) }
    var filterStatus by remember { mutableStateOf("All") } // All, OPEN, RESOLVED
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showSimulateInboundDialog by remember { mutableStateOf(false) }

    // Real-Time WhatsApp Polling: continuously syncs new incoming messages from backend every 3 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            onSyncBackend()
        }
    }

    if (selectedConversation != null) {
        val currentConv = conversations.find { it.id == selectedConversation!!.id } ?: selectedConversation!!
        val activeMessages = messagesMap[currentConv.id] ?: emptyList()

        // Real-Time Active Chat Polling: updates every 2 seconds while inside conversation
        LaunchedEffect(currentConv.id) {
            onFetchMessages(currentConv.id)
            while (true) {
                delay(2000)
                onFetchMessages(currentConv.id)
            }
        }

        ChatDetailView(
            conversation = currentConv,
            messages = activeMessages,
            templates = templates,
            onBack = { selectedConversation = null },
            onSendMessage = { text, templateName, mediaUrl ->
                onSendMessage(currentConv.id, text, templateName, mediaUrl)
            },
            onSendFile = { fileUri, caption ->
                onSendFile(currentConv.id, currentConv.phoneNumber, fileUri, caption)
            },
            onSimulateReply = { replyText ->
                onReceiveMessage(currentConv.phoneNumber, replyText, null)
                onFetchMessages(currentConv.id)
            }
        )
    } else {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    containerColor = WhatsAppGreen,
                    contentColor = Color(0xFF00381B)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Conversation")
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

                // Header with live sync & simulate controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "WhatsApp Inbox",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Real-time two-way messaging & webhooks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSyncBackend) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Replies", tint = WhatsAppGreen)
                        }
                        IconButton(onClick = { showSimulateInboundDialog = true }) {
                            Icon(Icons.Default.Call, contentDescription = "Simulate Inbound Msg", tint = AccentBlue)
                        }
                        StatusBadge("Live 2-Way", "success")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Tabs
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterStatus == "All",
                        onClick = { filterStatus = "All" },
                        label = { Text("All (${conversations.size})") }
                    )
                    FilterChip(
                        selected = filterStatus == "OPEN",
                        onClick = { filterStatus = "OPEN" },
                        label = { Text("Open") }
                    )
                    FilterChip(
                        selected = filterStatus == "RESOLVED",
                        onClick = { filterStatus = "RESOLVED" },
                        label = { Text("Resolved") }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val filtered = remember(filterStatus, conversations) {
                    if (filterStatus == "All") conversations
                    else conversations.filter { it.status.equals(filterStatus, ignoreCase = true) }
                }

                if (filtered.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Email,
                        title = "No conversations found",
                        message = "Incoming WhatsApp customer replies and sent messages will appear here in real-time."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { conv ->
                            ConversationRowItem(
                                conversation = conv,
                                onClick = { selectedConversation = conv }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewConversationDialog(
            contacts = contacts,
            onDismiss = { showNewChatDialog = false },
            onSelectContact = { contact ->
                showNewChatDialog = false
                val existing = conversations.find { it.contactId == contact.id }
                if (existing != null) {
                    selectedConversation = existing
                } else {
                    val newConv = ConversationItem(
                        id = (1000..9999).random(),
                        contactId = contact.id,
                        contactName = contact.fullName,
                        phoneNumber = contact.phoneNumber,
                        status = "OPEN",
                        unreadCount = 0,
                        lastMessage = "Started new conversation",
                        lastMessageAt = "Just now",
                        internalNotes = contact.notes
                    )
                    selectedConversation = newConv
                }
            }
        )
    }

    if (showSimulateInboundDialog) {
        SimulateInboundDialog(
            contacts = contacts,
            onDismiss = { showSimulateInboundDialog = false },
            onSendInbound = { phone, text ->
                onReceiveMessage(phone, text, null)
                showSimulateInboundDialog = false
            }
        )
    }
}

@Composable
fun SimulateInboundDialog(
    contacts: List<ContactItem>,
    onDismiss: () -> Unit,
    onSendInbound: (phone: String, text: String) -> Unit
) {
    var selectedPhone by remember { mutableStateOf(contacts.firstOrNull()?.phoneNumber ?: "+923093697069") }
    var replyText by remember { mutableStateOf("Hi! I received your WhatsApp message, please send me the full brochure.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulate Inbound WhatsApp Reply", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Test receiving incoming customer messages in real-time through the two-way pipeline:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = selectedPhone,
                    onValueChange = { selectedPhone = it },
                    label = { Text("From Customer Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Inbound Message Body") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )

                Text("Quick Inbound Templates:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = { replyText = "YES, interested in the promotion!" },
                        label = { Text("Interested") }
                    )
                    SuggestionChip(
                        onClick = { replyText = "Please send pricing and PDF." },
                        label = { Text("Send PDF") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendInbound(selectedPhone, replyText) },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
            ) {
                Text("Receive Message", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun NewConversationDialog(
    contacts: List<ContactItem>,
    onDismiss: () -> Unit,
    onSelectContact: (ContactItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val eligibleContacts = remember(searchQuery, contacts) {
        contacts.filter {
            (searchQuery.isEmpty() || it.fullName.contains(searchQuery, ignoreCase = true) || it.phoneNumber.contains(searchQuery)) &&
            it.optInStatus != "opted_out"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start New Conversation", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 380.dp)) {
                Text(
                    "Select an opted-in contact to initiate a WhatsApp conversation:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contact name or phone...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (eligibleContacts.isEmpty()) {
                    Text("No eligible contacts found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(eligibleContacts) { contact ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectContact(contact) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(WhatsAppGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(contact.firstName.take(1), fontWeight = FontWeight.Bold, color = WhatsAppGreen)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(contact.fullName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(contact.phoneNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConversationRowItem(
    conversation: ConversationItem,
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
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(WhatsAppDarkGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    conversation.contactName.take(1),
                    fontWeight = FontWeight.Bold,
                    color = WhatsAppGreen,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        conversation.contactName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        conversation.lastMessageAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        conversation.lastMessage,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (conversation.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(WhatsAppGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${conversation.unreadCount}",
                                color = Color(0xFF00381B),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatDetailView(
    conversation: ConversationItem,
    messages: List<MessageBubbleItem>,
    templates: List<TemplateItem> = emptyList(),
    onBack: () -> Unit,
    onSendMessage: (text: String, templateName: String?, mediaUrl: String?) -> Unit,
    onSendFile: (fileUri: Uri, caption: String?) -> Unit,
    onSimulateReply: (replyText: String) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<String?>(null) }
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var showQuickReplySheet by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var templateFilterCategory by remember { mutableStateOf("All") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            var displayName = "file"
            var sizeBytes = 0L
            if (uri.scheme == "content") {
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) displayName = it.getString(nameIndex)
                            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex >= 0) sizeBytes = it.getLong(sizeIndex)
                        }
                    }
                } catch (_: Exception) {}
            } else {
                displayName = uri.lastPathSegment ?: "file"
            }
            selectedFileName = displayName
            selectedFileSize = when {
                sizeBytes > 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
                sizeBytes > 1024 -> String.format(java.util.Locale.US, "%.0f KB", sizeBytes / 1024f)
                sizeBytes > 0 -> "$sizeBytes B"
                else -> null
            }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(WhatsAppDarkGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(conversation.contactName.take(1), fontWeight = FontWeight.Bold, color = WhatsAppGreen)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(conversation.contactName, fontWeight = FontWeight.Bold)
                    Text(conversation.phoneNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Simulate reply button
                IconButton(onClick = { showQuickReplySheet = true }) {
                    Icon(Icons.Default.Call, contentDescription = "Simulate Inbound Reply", tint = WhatsAppGreen)
                }
                StatusBadge(conversation.status, "blue")
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isOutbound = msg.direction == "OUTBOUND"
                val hasFile = msg.mediaUrl != null || msg.messageType != "text" || msg.body.startsWith("[Document:") || msg.body.startsWith("[File:")
                val lower = (msg.body + " " + (msg.mediaUrl ?: "")).lowercase()
                val isApk = lower.contains(".apk")
                val isPdf = lower.contains(".pdf")
                val isImage = msg.messageType == "image" || lower.contains(".png") || lower.contains(".jpg") || lower.contains(".jpeg")
                val isAudio = msg.messageType == "audio" || lower.contains(".mp3") || lower.contains(".wav") || lower.contains(".ogg")
                val isVideo = msg.messageType == "video" || lower.contains(".mp4")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isOutbound) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isOutbound) 14.dp else 2.dp,
                            bottomEnd = if (isOutbound) 2.dp else 14.dp
                        ),
                        color = if (isOutbound) WhatsAppDeepGreen else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (hasFile) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(WhatsAppGreen.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    isApk -> Icons.Default.Phone
                                                    isPdf -> Icons.Default.Share
                                                    isImage -> Icons.Default.Star
                                                    isAudio -> Icons.Default.PlayArrow
                                                    isVideo -> Icons.Default.PlayArrow
                                                    else -> Icons.Default.Share
                                                },
                                                contentDescription = null,
                                                tint = WhatsAppGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            val cleanDocTitle = when {
                                                msg.body.startsWith("[Document: ") -> msg.body.removePrefix("[Document: ").removeSuffix("]")
                                                msg.body.startsWith("[File: ") -> msg.body.removePrefix("[File: ").removeSuffix("]")
                                                isApk -> "Package (.apk)"
                                                isPdf -> "Document (.pdf)"
                                                else -> "WhatsApp Media"
                                            }
                                            Text(
                                                cleanDocTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOutbound) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                when {
                                                    isApk -> "WhatsApp Document (APK)"
                                                    isPdf -> "WhatsApp Document (PDF)"
                                                    isImage -> "WhatsApp Image"
                                                    isAudio -> "WhatsApp Audio"
                                                    isVideo -> "WhatsApp Video"
                                                    else -> "WhatsApp Document"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isOutbound) Color.White.copy(alpha = 0.7f) else WhatsAppGreen
                                            )
                                        }
                                    }
                                }
                            }

                            val isPureAttachmentLabel = msg.body.startsWith("[Document:") || msg.body.startsWith("[File:")
                            if (!isPureAttachmentLabel && msg.body.isNotBlank()) {
                                Text(
                                    text = msg.body,
                                    color = if (isOutbound) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg.timestamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOutbound) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isOutbound) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = null,
                                        tint = if (msg.status == "READ") AccentCyan else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Attached File Preview Banner
        if (selectedFileUri != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        val isApk = selectedFileName?.endsWith(".apk", ignoreCase = true) == true
                        Icon(
                            imageVector = if (isApk) Icons.Default.Phone else Icons.Default.Share,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(selectedFileName ?: "", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${if (isApk) "Android Package (.apk)" else "Direct WhatsApp Document"} • ${selectedFileSize ?: "Ready to send"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WhatsAppGreen
                            )
                        }
                    }
                    IconButton(onClick = {
                        selectedFileUri = null
                        selectedFileName = null
                        selectedFileSize = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Dynamic Templates & Quick Actions Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                SuggestionChip(
                    onClick = { showTemplatesDialog = true },
                    label = { Text("Browse Templates (${templates.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp), tint = WhatsAppGreen) }
                )
            }
            items(templates) { tmpl ->
                val isMarketing = tmpl.category.equals("Marketing", ignoreCase = true)
                val isUtility = tmpl.category.equals("Utility", ignoreCase = true)
                val catTag = if (isMarketing) "[Marketing]" else if (isUtility) "[Utility]" else "[${tmpl.category}]"
                SuggestionChip(
                    onClick = {
                        onSendMessage(tmpl.content, tmpl.name, null)
                    },
                    label = { Text("$catTag ${tmpl.name}", style = MaterialTheme.typography.labelSmall) }
                )
            }
            item {
                SuggestionChip(
                    onClick = {
                        inputText = "Hi, our team is reviewing your inquiry. We will update you shortly!"
                    },
                    label = { Text("Quick Review Reply", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Chat Input Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showAttachmentPicker = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Attach file from device", tint = WhatsAppGreen)
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(if (selectedFileUri != null) "Add caption (optional)..." else "Type official WhatsApp reply...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (selectedFileUri != null) {
                            onSendFile(selectedFileUri!!, inputText.trim().ifEmpty { null })
                            selectedFileUri = null
                            selectedFileName = null
                            selectedFileSize = null
                            inputText = ""
                        } else if (inputText.isNotBlank()) {
                            onSendMessage(inputText.trim(), null, null)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WhatsAppGreen)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF00381B))
                }
            }
        }
    }

    if (showAttachmentPicker) {
        DirectDeviceFilePickerDialog(
            onDismiss = { showAttachmentPicker = false },
            onPickCategory = { mime ->
                filePickerLauncher.launch(mime)
                showAttachmentPicker = false
            }
        )
    }

    if (showQuickReplySheet) {
        AlertDialog(
            onDismissRequest = { showQuickReplySheet = false },
            title = { Text("Simulate Inbound Reply from ${conversation.contactName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Test customer inbound messaging without needing another physical device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        "Yes, please send me the latest brochure!",
                        "What is the starting price for this offer?",
                        "Sounds great! Can we schedule a brief consultation call?",
                        "Received! Thank you for the quick follow up."
                    ).forEach { replySample ->
                        OutlinedButton(
                            onClick = {
                                onSimulateReply(replySample)
                                showQuickReplySheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(replySample, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQuickReplySheet = false }) { Text("Cancel") }
            }
        )
    }

    if (showTemplatesDialog) {
        val filteredTmpls = remember(templateFilterCategory, templates) {
            if (templateFilterCategory == "All") templates
            else templates.filter { it.category.equals(templateFilterCategory, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { showTemplatesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.List, contentDescription = null, tint = WhatsAppGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Template (${templates.size})", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // Category filter row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("All", "Marketing", "Utility", "Authentication").forEach { cat ->
                            FilterChip(
                                selected = templateFilterCategory == cat,
                                onClick = { templateFilterCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (filteredTmpls.isEmpty()) {
                        Text(
                            "No templates under '$templateFilterCategory'. Create one in Templates tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 350.dp)
                        ) {
                            items(filteredTmpls) { tmpl ->
                                val isMarketing = tmpl.category.equals("Marketing", ignoreCase = true)
                                val isUtility = tmpl.category.equals("Utility", ignoreCase = true)
                                val catColor = if (isMarketing) Color(0xFF6750A4) else if (isUtility) Color(0xFF006C50) else Color(0xFFB3261E)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(tmpl.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = catColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        tmpl.category,
                                                        color = catColor,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                StatusBadge(tmpl.status, if (tmpl.status == "APPROVED") "green" else "orange")
                                            }
                                        }
                                        Text(
                                            tmpl.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = {
                                                inputText = tmpl.content
                                                showTemplatesDialog = false
                                            }) {
                                                Text("Insert", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Button(
                                                onClick = {
                                                    onSendMessage(tmpl.content, tmpl.name, null)
                                                    showTemplatesDialog = false
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Send Now", color = Color(0xFF00381B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTemplatesDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun DirectDeviceFilePickerDialog(
    onDismiss: () -> Unit,
    onPickCategory: (mimeType: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = WhatsAppGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send File from Device", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Choose any file directly from your phone storage to send as an official WhatsApp document or media:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AttachmentOptionItem(
                    title = "Android Application (.APK)",
                    subtitle = "Pick APK file directly from device Downloads/Storage",
                    badge = "Direct APK",
                    icon = Icons.Default.Phone,
                    onClick = { onPickCategory("*/*") }
                )

                AttachmentOptionItem(
                    title = "PDF / Document",
                    subtitle = "Pick PDF presentation, catalog, or brochure",
                    badge = "WhatsApp Doc",
                    icon = Icons.Default.Share,
                    onClick = { onPickCategory("*/*") }
                )

                AttachmentOptionItem(
                    title = "Photo / Image",
                    subtitle = "Select JPG, PNG, WEBP from Gallery",
                    badge = "Image",
                    icon = Icons.Default.Star,
                    onClick = { onPickCategory("image/*") }
                )

                AttachmentOptionItem(
                    title = "Audio / Voice Note",
                    subtitle = "Select MP3, AAC, WAV recording",
                    badge = "Audio",
                    icon = Icons.Default.PlayArrow,
                    onClick = { onPickCategory("audio/*") }
                )

                AttachmentOptionItem(
                    title = "Browse Device Files",
                    subtitle = "Open device file explorer for any document",
                    badge = "All Files",
                    icon = Icons.Default.Add,
                    onClick = { onPickCategory("*/*") }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AttachmentOptionItem(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(WhatsAppGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = WhatsAppGreen.copy(alpha = 0.15f)
            ) {
                Text(
                    badge,
                    color = WhatsAppGreen,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
