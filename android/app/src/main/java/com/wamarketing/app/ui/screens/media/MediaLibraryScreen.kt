package com.wamarketing.app.ui.screens.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wamarketing.app.model.MediaFileItem
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.components.EmptyStateView
import com.wamarketing.app.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreen(
    mediaFiles: List<MediaFileItem>,
    onAddMedia: (MediaFileItem) -> Unit,
    onDeleteMedia: (Int) -> Unit,
    onUseInCampaign: ((MediaFileItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") } // All, image, video, document, package
    var searchQuery by remember { mutableStateOf("") }
    var showManualUrlDialog by remember { mutableStateOf(false) }

    // System File Picker Launcher for any file from device storage (Images, PDFs, Videos, APKs)
    val deviceFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            processAndAddDeviceFile(context, fileUri, onAddMedia)
        }
    }

    // System Picker specifically for images / videos from gallery
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            processAndAddDeviceFile(context, fileUri, onAddMedia)
        }
    }

    val filteredFiles = remember(mediaFiles, selectedCategory, searchQuery) {
        mediaFiles.filter { file ->
            val matchCat = if (selectedCategory == "All") true else file.fileType.equals(selectedCategory, ignoreCase = true)
            val matchSearch = if (searchQuery.isBlank()) true else file.filename.contains(searchQuery, ignoreCase = true)
            matchCat && matchSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { deviceFilePickerLauncher.launch("*/*") },
                containerColor = WhatsAppGreen,
                contentColor = Color(0xFF00381B)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Upload from Device")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Media & Asset Library",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Upload and manage images, PDFs, videos & APKs from device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge("${mediaFiles.size} Files", "blue")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Device Upload Action Card (PROMINENT BUTTONS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WhatsAppDarkGreen.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Direct Device Upload",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = WhatsAppGreen
                    )
                    Text(
                        "Select any APK, image, document, or video directly from phone storage:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { deviceFilePickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick from Phone", color = Color(0xFF00381B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { galleryPickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(0.9f).height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(
                            onClick = { showManualUrlDialog = true }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Add URL Link", tint = AccentBlue)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search assets by filename...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf(
                    "All" to "All",
                    "image" to "Images",
                    "document" to "PDFs",
                    "video" to "Videos",
                    "package" to "APKs"
                )
                categories.forEach { (catKey, label) ->
                    FilterChip(
                        selected = selectedCategory == catKey,
                        onClick = { selectedCategory = catKey },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Media Files List
            if (filteredFiles.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Share,
                    title = "No files found",
                    message = "Tap 'Pick from Phone' to select an APK package, PDF document, image, or video from your device."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { file ->
                        MediaFileCard(
                            file = file,
                            onCopyUrl = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Media URL", file.url)
                                clipboard.setPrimaryClip(clip)
                            },
                            onDelete = { onDeleteMedia(file.id) },
                            onUse = if (onUseInCampaign != null) { { onUseInCampaign(file) } } else null
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showManualUrlDialog) {
        UploadMediaDialog(
            onDismiss = { showManualUrlDialog = false },
            onUpload = { newFile ->
                onAddMedia(newFile)
                showManualUrlDialog = false
            }
        )
    }
}

private fun processAndAddDeviceFile(
    context: Context,
    fileUri: Uri,
    onAddMedia: (MediaFileItem) -> Unit
) {
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

    val isNative = fileType in listOf("image", "video", "document")
    val reason = if (!isNative) "APK files are shared via direct 1-click cloud download link" else null

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
    onAddMedia(newItem)
}

@Composable
fun MediaFileCard(
    file: MediaFileItem,
    onCopyUrl: () -> Unit,
    onDelete: () -> Unit,
    onUse: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (file.fileType) {
                    "image" -> Icons.Default.Star
                    "video" -> Icons.Default.PlayArrow
                    "document" -> Icons.Default.Email
                    "package" -> Icons.Default.Phone
                    else -> Icons.Default.Share
                }
                val iconBg = when (file.fileType) {
                    "image" -> AccentBlue.copy(alpha = 0.15f)
                    "video" -> AccentCyan.copy(alpha = 0.15f)
                    "document" -> AccentAmber.copy(alpha = 0.15f)
                    "package" -> WhatsAppGreen.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val iconTint = when (file.fileType) {
                    "image" -> AccentBlue
                    "video" -> AccentCyan
                    "document" -> AccentAmber
                    "package" -> WhatsAppGreen
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.filename,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val sizeFormatted = formatFileSize(file.fileSizeBytes)
                    Text(
                        "$sizeFormatted • Added ${file.createdAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // WhatsApp Compatibility Banner
            if (file.isWhatsAppSupported) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = WhatsAppGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Native WhatsApp Media Support",
                            style = MaterialTheme.typography.labelSmall,
                            color = WhatsAppGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentAmber.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            file.whatsAppUnsupportedReason ?: "Shared via direct 1-click cloud download link",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCopyUrl,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Link", style = MaterialTheme.typography.labelSmall)
                }

                if (onUse != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onUse,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Use in Campaign", color = Color(0xFF00381B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadMediaDialog(
    onDismiss: () -> Unit,
    onUpload: (MediaFileItem) -> Unit
) {
    var filename by remember { mutableStateOf("New_Product_Catalog.pdf") }
    var fileType by remember { mutableStateOf("document") } // image, video, document, package
    var fileUrl by remember { mutableStateOf("https://api.wamarketing.app/uploads/catalog.pdf") }
    var fileSizeMb by remember { mutableStateOf("3.5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Cloud Link Asset", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Register an external URL or web resource for WhatsApp distributions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "image" to "Image",
                        "document" to "PDF",
                        "video" to "Video",
                        "package" to "APK"
                    ).forEach { (catKey, label) ->
                        FilterChip(
                            selected = fileType == catKey,
                            onClick = {
                                fileType = catKey
                                if (catKey == "package" && !filename.endsWith(".apk")) {
                                    filename = "MyApplication.apk"
                                    fileUrl = "https://api.wamarketing.app/uploads/app.apk"
                                    fileSizeMb = "15.0"
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = fileUrl,
                    onValueChange = { fileUrl = it },
                    label = { Text("File URL / Download Link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fileSizeMb,
                    onValueChange = { fileSizeMb = it },
                    label = { Text("File Size (MB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sizeBytes = ((fileSizeMb.toDoubleOrNull() ?: 1.0) * 1024 * 1024).toLong()
                    val isNative = fileType != "package"
                    val note = if (!isNative) "APK files are shared via direct 1-click cloud download link" else null
                    val mime = when (fileType) {
                        "image" -> "image/jpeg"
                        "video" -> "video/mp4"
                        "package" -> "application/vnd.android.package-archive"
                        else -> "application/pdf"
                    }
                    val created = MediaFileItem(
                        id = (1000..9999).random(),
                        filename = filename,
                        fileType = fileType,
                        mimeType = mime,
                        fileSizeBytes = sizeBytes,
                        publicUrl = fileUrl,
                        isWhatsAppSupported = isNative,
                        whatsAppUnsupportedReason = note,
                        createdAt = "Just now"
                    )
                    onUpload(created)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
            ) {
                Text("Add to Library", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
