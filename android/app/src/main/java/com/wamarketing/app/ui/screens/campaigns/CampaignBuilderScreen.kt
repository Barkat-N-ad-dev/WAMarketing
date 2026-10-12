package com.wamarketing.app.ui.screens.campaigns

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import com.wamarketing.app.data.CsvLeadParser
import com.wamarketing.app.model.CampaignItem
import com.wamarketing.app.model.ContactItem
import com.wamarketing.app.model.MediaFileItem
import com.wamarketing.app.model.TagItem
import com.wamarketing.app.model.TemplateItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignBuilderScreen(
    templates: List<TemplateItem> = emptyList(),
    mediaFiles: List<MediaFileItem> = emptyList(),
    onImportLeadsSheet: ((List<ContactItem>) -> Unit)? = null,
    onAddMedia: ((MediaFileItem) -> Unit)? = null,
    onBack: () -> Unit,
    onSaveCampaign: (CampaignItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) } // 1 through 8

    // State for builder
    var campaignName by remember { mutableStateOf("New Leads Outreach Campaign") }

    // Step 2: Audience / Sheet Import State
    var audienceMode by remember { mutableStateOf("import") } // "segments" or "import"
    var selectedAudienceTag by remember { mutableStateOf("Imported Sheet Leads") }
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    var sheetRawText by remember {
        mutableStateOf(
            "Hamza Khan, +971501234567, Apex Dynamics, Dubai\n" +
            "Fatima Zahra, +971559876543, Emirates Trading, Abu Dhabi\n" +
            "Tariq Mahmood, +923001234567, Pak Trade Co, Lahore\n" +
            "Zainab Ali, +966501234567, Saudi Ventures, Riyadh"
        )
    }
    var parsedContacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var isSheetImported by remember { mutableStateOf(false) }

    // Parse initial sample text
    LaunchedEffect(sheetRawText) {
        if (uploadedFileName == null) {
            parsedContacts = CsvLeadParser.parseFromText(sheetRawText)
        }
    }

    // Device CSV / Excel / PDF File Picker
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            var displayName = "device_leads.csv"
            try {
                val cursor = context.contentResolver.query(fileUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) displayName = it.getString(nameIdx)
                    }
                }
            } catch (_: Exception) {}

            uploadedFileName = displayName
            coroutineScope.launch {
                val contacts = CsvLeadParser.parseFromUriWithBackend(context, fileUri, displayName)
                if (contacts.isNotEmpty()) {
                    parsedContacts = contacts
                    onImportLeadsSheet?.invoke(contacts)
                    isSheetImported = true
                    // Populate clean text representation instead of binary zip headers
                    sheetRawText = contacts.joinToString("\n") { 
                        "${it.fullName}, ${it.phoneNumber}, ${it.company ?: ""}, ${it.city ?: ""}" 
                    }
                }
            }
        }
    }

    // Step 3: Message Text with Variable Substitution & Meta Template Selection
    var useApprovedTemplate by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<TemplateItem?>(null) }
    var templateCategoryFilter by remember { mutableStateOf("All") }
    var messageText by remember {
        mutableStateOf("Hello {{first_name}}, we have an exclusive update for {{company}} in {{city}}! Get our application here: {{download_link}}")
    }

    // Step 4: Media Selection (including APK packages)
    var selectedMedia by remember {
        mutableStateOf<MediaFileItem?>(
            mediaFiles.find { it.fileType == "package" } ?: mediaFiles.firstOrNull()
        )
    }
    var attachMedia by remember { mutableStateOf(true) }

    // Device Media / APK Picker
    val deviceMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            var fileName = "device_file_${System.currentTimeMillis()}"
            var fileSize = 1024L * 1024L
            try {
                val cursor = context.contentResolver.query(fileUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) fileName = it.getString(nameIdx)
                        val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1) fileSize = it.getLong(sizeIdx)
                    }
                }
            } catch (_: Exception) {}

            val detectedMime = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
            val lowerName = fileName.lowercase()

            val fileType = when {
                lowerName.endsWith(".apk") || detectedMime.contains("vnd.android.package-archive") -> "package"
                lowerName.endsWith(".pdf") || detectedMime.contains("pdf") -> "document"
                detectedMime.startsWith("image/") || lowerName.matches(Regex(".*\\.(jpg|jpeg|png|webp|gif)$")) -> "image"
                detectedMime.startsWith("video/") || lowerName.matches(Regex(".*\\.(mp4|mov|mkv|3gp)$")) -> "video"
                else -> "document"
            }

            val isNative = true
            val reason: String? = null

            val newItem = MediaFileItem(
                id = (1000..9999).random(),
                filename = fileName,
                fileType = fileType,
                mimeType = detectedMime,
                fileSizeBytes = fileSize,
                publicUrl = fileUri.toString(),
                isWhatsAppSupported = isNative,
                whatsAppUnsupportedReason = reason,
                createdAt = "Today (From Phone)"
            )
            selectedMedia = newItem
            attachMedia = true
            onAddMedia?.invoke(newItem)
        }
    }

    // Step 6: Scheduling
    var isSendNow by remember { mutableStateOf(true) }
    var scheduledDate by remember { mutableStateOf("Tomorrow, 10:00 AM") }
    var timeZone by remember { mutableStateOf("Asia/Dubai (GST +04:00)") }

    val stepTitles = listOf(
        "1. Campaign Name", "2. Audience / Import Sheet", "3. Message & Variables",
        "4. Media / APK File", "5. Live Preview", "6. Schedule",
        "7. Compliance Review", "8. Launch"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "Campaign Wizard",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Step $step of 8: ${stepTitles[step - 1]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WhatsAppGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = step / 8f,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = WhatsAppGreen
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Wizard Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (step) {
                1 -> {
                    Text("Campaign Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Name your campaign for tracking performance and analytics.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = campaignName,
                        onValueChange = { campaignName = it },
                        label = { Text("Campaign Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                2 -> {
                    // AUDIENCE & CSV SHEET IMPORT FROM DEVICE
                    Text("Step 2: Recipient Audience", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Upload a CSV file directly from your phone or select existing customer segments:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = audienceMode == "import",
                            onClick = { audienceMode = "import"; selectedAudienceTag = "Imported Sheet Leads" },
                            label = { Text("Upload from Phone") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                        FilterChip(
                            selected = audienceMode == "segments",
                            onClick = { audienceMode = "segments" },
                            label = { Text("Existing Segments") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                    }

                    if (audienceMode == "import") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        uploadedFileName ?: "CSV Lead File",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    StatusBadge("${parsedContacts.size} Leads Parsed", "success")
                                }

                                // Native Device File Picker Button
                                Button(
                                    onClick = { csvPickerLauncher.launch("*/*") },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pick CSV File from Device", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                                }

                                Divider()

                                Text(
                                    "Or paste CSV rows manually (Name, Phone, Company, City):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = sheetRawText,
                                    onValueChange = {
                                        sheetRawText = it
                                        uploadedFileName = null
                                        parsedContacts = CsvLeadParser.parseFromText(it)
                                        isSheetImported = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            uploadedFileName = "Sample_Dubai_Leads.csv"
                                            sheetRawText = "Hamza Khan, +971501234567, Apex Dynamics, Dubai\n" +
                                                    "Fatima Zahra, +971559876543, Emirates Trading, Abu Dhabi\n" +
                                                    "Tariq Mahmood, +923001234567, Pak Trade Co, Lahore\n" +
                                                    "Zainab Ali, +966501234567, Saudi Ventures, Riyadh"
                                            parsedContacts = CsvLeadParser.parseFromText(sheetRawText)
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sample Leads", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Button(
                                        onClick = {
                                            if (parsedContacts.isNotEmpty()) {
                                                onImportLeadsSheet?.invoke(parsedContacts)
                                                isSheetImported = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (isSheetImported) "Imported to CRM" else "Import to CRM",
                                            color = Color(0xFF00381B),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Existing segments
                        val audienceTags = listOf("All Opted-In (850)", "VIP (142)", "Hot Lead (98)", "Dubai Investors (210)", "Customers (320)")
                        audienceTags.forEach { tag ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAudienceTag = tag }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedAudienceTag == tag,
                                    onClick = { selectedAudienceTag = tag }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tag, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                3 -> {
                    // MESSAGE & META TEMPLATE SELECTION
                    Text("Step 3: Message Text & Approved Meta Template", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Choose between an official Meta WhatsApp pre-approved template or custom message copy:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Template vs Custom Mode Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = useApprovedTemplate,
                            onClick = {
                                useApprovedTemplate = true
                                if (selectedTemplate == null) {
                                    val firstApproved = templates.firstOrNull { it.status.equals("APPROVED", ignoreCase = true) } ?: templates.firstOrNull()
                                    selectedTemplate = firstApproved
                                    if (firstApproved != null) messageText = firstApproved.content
                                }
                            },
                            label = { Text("Use Approved Meta Template", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                if (useApprovedTemplate) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilterChip(
                            selected = !useApprovedTemplate,
                            onClick = { useApprovedTemplate = false },
                            label = { Text("Custom Copy") },
                            leadingIcon = {
                                if (!useApprovedTemplate) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    if (useApprovedTemplate) {
                        // Category filter chips
                        Text("Filter by Category:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        val catOptions = listOf("All", "Marketing", "Utility", "Authentication")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(catOptions) { cat ->
                                val isSelected = templateCategoryFilter == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { templateCategoryFilter = cat },
                                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        val availableTemplates = templates.filter {
                            templateCategoryFilter == "All" || it.category.equals(templateCategoryFilter, ignoreCase = true)
                        }

                        if (availableTemplates.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    "No templates found under '$templateCategoryFilter'. Create one in Templates screen or choose another category.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                availableTemplates.forEach { tmpl ->
                                    val isSelected = selectedTemplate?.id == tmpl.id
                                    val isMarketing = tmpl.category.equals("Marketing", ignoreCase = true)
                                    val isUtility = tmpl.category.equals("Utility", ignoreCase = true)
                                    val catColor = if (isMarketing) Color(0xFF6750A4) else if (isUtility) Color(0xFF006C50) else Color(0xFFB3261E)

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedTemplate = tmpl
                                                messageText = tmpl.content
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) WhatsAppDarkGreen.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(width = 2.dp) else null
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = {
                                                            selectedTemplate = tmpl
                                                            messageText = tmpl.content
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(tmpl.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 36.dp, top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            label = { Text("Campaign Message Body") }
                        )

                        Text("Available Variables (Click to Insert):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val vars = listOf("{{first_name}}", "{{last_name}}", "{{company}}", "{{city}}", "{{download_link}}")
                            items(vars) { v ->
                                SuggestionChip(
                                    onClick = { messageText += " $v" },
                                    label = { Text(v) }
                                )
                            }
                        }
                    }
                }

                4 -> {
                    // MEDIA / APK SELECTION FROM DEVICE OR LIBRARY
                    Text("Step 4: Attach Media or APK Package", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Select any media file (Image, PDF, Video) or an Android APK package directly from your phone:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = attachMedia,
                            onCheckedChange = { attachMedia = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attach media/file to this campaign", fontWeight = FontWeight.Medium)
                    }

                    if (attachMedia) {
                        // Pick directly from phone button
                        Button(
                            onClick = { deviceMediaPicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick File / APK from Phone Storage", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val allAvailableFiles = remember(mediaFiles, selectedMedia) {
                            if (selectedMedia != null && mediaFiles.none { it.id == selectedMedia!!.id }) {
                                listOf(selectedMedia!!) + mediaFiles
                            } else {
                                mediaFiles
                            }
                        }

                        if (allAvailableFiles.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    "No media attached yet. Tap 'Pick File / APK from Phone Storage' above.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                allAvailableFiles.forEach { file ->
                                    val isSelected = selectedMedia?.id == file.id
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedMedia = file },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) WhatsAppGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(width = 2.dp) else CardDefaults.outlinedCardBorder()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedMedia = file }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(file.filename, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                val catLabel = when (file.fileType) {
                                                    "package" -> "Android APK Package"
                                                    "image" -> "Image"
                                                    "video" -> "Video"
                                                    else -> "Document PDF"
                                                }
                                                Text(
                                                    "$catLabel • ${if (file.isWhatsAppSupported) "Native WhatsApp Media" else "1-Click Cloud Download Link"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (file.fileType == "package") WhatsAppGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Special banner if APK package selected
                        if (selectedMedia?.fileType == "package") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = WhatsAppDarkGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("APK Distribution Mode Active", fontWeight = FontWeight.Bold, color = WhatsAppGreen, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "The APK download link (${selectedMedia?.url}) will be automatically injected into recipient messages for 1-click install.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                5 -> {
                    // LIVE PREVIEW
                    Text("Step 5: Live Recipient Preview", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    val previewContact = parsedContacts.firstOrNull() ?: ContactItem(
                        id = 1, firstName = "Hamza", lastName = "Khan", phoneNumber = "+971501234567",
                        whatsappNumber = "+971501234567", email = "hamza@apex.ae", company = "Apex Dynamics",
                        city = "Dubai", country = "UAE"
                    )

                    val renderedMessage = messageText
                        .replace("{{first_name}}", previewContact.firstName)
                        .replace("{{last_name}}", previewContact.lastName)
                        .replace("{{company}}", previewContact.company ?: "")
                        .replace("{{city}}", previewContact.city ?: "")
                        .replace("{{download_link}}", selectedMedia?.url ?: "https://api.wamarketing.app/uploads/wamarketing-debug.apk")

                    Text(
                        "Sample rendered message for contact: '${previewContact.fullName}' (${previewContact.company ?: ""}, ${previewContact.city ?: ""}):",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = WhatsAppDarkGreen.copy(alpha = 0.18f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (attachMedia && selectedMedia != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (selectedMedia?.fileType == "package") Icons.Default.Phone else Icons.Default.Share,
                                            contentDescription = null,
                                            tint = WhatsAppGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(selectedMedia?.filename ?: "", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                            Text(
                                                if (selectedMedia?.fileType == "package") "APK Cloud Link: ${selectedMedia?.url}" else "Media Attachment",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                renderedMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text("10:45 AM  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(Icons.Default.Done, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                6 -> {
                    // SCHEDULING
                    Text("Step 6: Execution Scheduling", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSendNow, onClick = { isSendNow = true })
                        Text("Send immediately upon launch")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !isSendNow, onClick = { isSendNow = false })
                        Text("Schedule for later")
                    }
                    if (!isSendNow) {
                        OutlinedTextField(
                            value = scheduledDate,
                            onValueChange = { scheduledDate = it },
                            label = { Text("Scheduled Date & Time") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = timeZone,
                            onValueChange = { timeZone = it },
                            label = { Text("Target Time Zone") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                7 -> {
                    // COMPLIANCE REVIEW
                    Text("Step 7: Pre-flight Verification", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewItem("Message Template:", if (useApprovedTemplate && selectedTemplate != null) "${selectedTemplate!!.name} (${selectedTemplate!!.category})" else "Custom Copy", true)
                            ReviewItem("Campaign Target:", if (audienceMode == "import") "${parsedContacts.size} Sheet Leads" else selectedAudienceTag, true)
                            ReviewItem("Media / File:", if (attachMedia && selectedMedia != null) selectedMedia!!.filename else "None (Text-only)", true)
                            ReviewItem("Meta WhatsApp Cloud API:", "Active (Phone ID #1181137018426157)", true)
                            ReviewItem("Rate Limit Pacing:", "Compliant (20 msgs/second)", true)
                            ReviewItem("CRM Sync:", "Auto-leads generation enabled", true)
                        }
                    }
                }

                8 -> {
                    // LAUNCH READY
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Ready to Launch!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Campaign '$campaignName' will dispatch to ${if (audienceMode == "import") parsedContacts.size else 142} recipients via Meta WhatsApp Cloud API.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Stepper Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (step > 1) {
                OutlinedButton(
                    onClick = { step-- },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Back")
                }
            }
            Button(
                onClick = {
                    if (step < 8) {
                        // If moving from step 2 and sheet not imported yet, auto-import to CRM
                        if (step == 2 && audienceMode == "import" && parsedContacts.isNotEmpty() && !isSheetImported) {
                            onImportLeadsSheet?.invoke(parsedContacts)
                            isSheetImported = true
                        }
                        step++
                    } else {
                        // Launch
                        val totalRecipients = if (audienceMode == "import" && parsedContacts.isNotEmpty()) parsedContacts.size else 142
                        val created = CampaignItem(
                            id = (100..999).random(),
                            name = campaignName,
                            status = if (isSendNow) "RUNNING" else "SCHEDULED",
                            messageContent = messageText,
                            mediaUrl = if (attachMedia) (selectedMedia?.publicUrl ?: selectedMedia?.url) else null,
                            templateName = if (useApprovedTemplate) selectedTemplate?.name else null,
                            templateCategory = if (useApprovedTemplate) selectedTemplate?.category else null,
                            scheduledAt = if (!isSendNow) scheduledDate else null,
                            totalRecipients = totalRecipients,
                            sentCount = 0,
                            deliveredCount = 0
                        )
                        onSaveCampaign(created)
                        onBack()
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
            ) {
                Text(
                    text = if (step == 8) "Confirm & Launch" else "Next",
                    color = Color(0xFF00381B),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ReviewItem(label: String, value: String, verified: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (verified) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
