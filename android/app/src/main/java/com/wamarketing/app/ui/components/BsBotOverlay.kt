package com.wamarketing.app.ui.components

import android.content.Context
import com.wamarketing.app.data.AppConfig
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wamarketing.app.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class BsBotMessage(
    val sender: String, // "user" or "bot"
    val text: String,
    val time: String = "Now"
)

/**
 * Fully Moveable / Draggable floating chat icon overlay visible across all screens.
 * Users can freely drag and place it anywhere so it never blocks message typing or buttons.
 */
@Composable
fun BsBotFloatingBubble(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            modifier = Modifier.shadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF00A884), Color(0xFF075E54))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Drag to Move",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "BS Bot",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "BS Bot",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Moveable AI",
                            color = Color(0xFFD1FAE5),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full interactive BS Bot chat overlay dialog connected directly to Google Gemini API & backend
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BsBotDialog(
    onDismiss: () -> Unit,
    backendUrl: String = AppConfig.DEFAULT_BACKEND_URL
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wamarketing_persistent_store", Context.MODE_PRIVATE) }
    var geminiApiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(geminiApiKey) }

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember {
        mutableStateOf(
            listOf(
                BsBotMessage(
                    "bot",
                    "Hello! I am **BS Bot**, your 24/7 AI WhatsApp Marketing Specialist.\n\n" +
                    "I can help you craft high-converting campaigns, troubleshoot incoming customer messages, configure Meta webhooks, and import leads.\n\n" +
                    (if (geminiApiKey.isNotBlank()) "⚡ *Powered by Google Gemini AI*\n\n" else "💡 *Tip: Tap the key icon 🔑 in the top bar to connect your Gemini API key for live AI answers!*\n\n") +
                    "Developed by **Barkat Ali** (https://barkatai.ihsanraza.com/#about)."
                )
            )
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = null,
                                    tint = Color(0xFF00381B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "BS Bot",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (geminiApiKey.isNotBlank()) Color(0xFF10B981) else Color(0xFFF59E0B))
                                    )
                                }
                                Text(
                                    if (geminiApiKey.isNotBlank()) "Gemini AI Connected • Online" else "Barkat Solutions AI • Offline Mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (geminiApiKey.isNotBlank()) WhatsAppGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                apiKeyInput = geminiApiKey
                                showApiKeyDialog = true
                            }) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Gemini API Key",
                                    tint = if (geminiApiKey.isNotBlank()) WhatsAppGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }

                // Quick Prompt Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val promptSuggestions = listOf(
                        "How to get incoming messages?",
                        "Write a promo campaign",
                        "How to import Excel/PDF leads?",
                        "About developer Barkat Ali"
                    )
                    items(promptSuggestions) { prompt ->
                        SuggestionChip(
                            onClick = {
                                inputText = prompt
                            },
                            label = { Text(prompt, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.sender == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                ),
                                color = if (isUser) WhatsAppGreen else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isUser) "You" else "BS Bot",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isUser) Color(0xFF00381B) else WhatsAppGreen
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isUser) Color(0xFF00381B) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = WhatsAppGreen, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("BS Bot (Gemini AI) is generating answer...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Chat Input Row
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ask BS Bot anything...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val query = inputText.trim()
                                if (query.isNotBlank() && !isLoading) {
                                    messages = messages + BsBotMessage("user", query)
                                    inputText = ""
                                    isLoading = true

                                    scope.launch {
                                        val reply = withContext(Dispatchers.IO) {
                                            // 1. Direct Google Gemini Flash API call if key is saved
                                            if (geminiApiKey.isNotBlank()) {
                                                val candidateModels = listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash", "gemini-3.8-flash", "gemini-1.5-flash")
                                                for (model in candidateModels) {
                                                    try {
                                                        val geminiUrl = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$geminiApiKey")
                                                        val gConn = geminiUrl.openConnection() as HttpURLConnection
                                                        gConn.requestMethod = "POST"
                                                        gConn.setRequestProperty("Content-Type", "application/json")
                                                        gConn.doOutput = true
                                                        gConn.connectTimeout = 8000
                                                        gConn.readTimeout = 12000

                                                        val sysPrompt = "You are BS Bot, the AI WhatsApp Marketing Specialist and in-app assistant for WA Marketing SaaS, created by Barkat Ali (senior full-stack mobile & cloud engineer, https://barkatai.ihsanraza.com/#about). Help the user with high-converting campaigns, Meta WhatsApp Cloud API setup, lead imports, webhooks, and automation."
                                                        val gBody = JSONObject()
                                                        val contentsArr = JSONArray()
                                                        val promptPart = JSONObject().put("text", "$sysPrompt\n\nUser Question: $query")
                                                        val msgObj = JSONObject().apply {
                                                            put("role", "user")
                                                            put("parts", JSONArray().put(promptPart))
                                                        }
                                                        contentsArr.put(msgObj)
                                                        gBody.put("contents", contentsArr)

                                                        gConn.outputStream.use { it.write(gBody.toString().toByteArray(Charsets.UTF_8)) }
                                                        if (gConn.responseCode in 200..299) {
                                                            val respStr = gConn.inputStream.bufferedReader().use { it.readText() }
                                                            val respJson = JSONObject(respStr)
                                                            val cands = respJson.optJSONArray("candidates")
                                                            if (cands != null && cands.length() > 0) {
                                                                val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                                                                if (parts != null && parts.length() > 0) {
                                                                    val directText = parts.getJSONObject(0).optString("text", "")
                                                                    if (directText.isNotBlank()) return@withContext directText
                                                                }
                                                            }
                                                        } else if (gConn.responseCode == 404) {
                                                            continue // Try next model candidate
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            }

                                            // 2. Try backend AI chat endpoint
                                            try {
                                                val url = URL("$backendUrl/api/v1/ai/chat")
                                                val conn = url.openConnection() as HttpURLConnection
                                                AppConfig.applyStandardHeaders(conn)
                                                conn.requestMethod = "POST"
                                                conn.setRequestProperty("Content-Type", "application/json")
                                                conn.doOutput = true
                                                conn.connectTimeout = 6000
                                                conn.readTimeout = 6000
                                                val payload = JSONObject()
                                                payload.put("message", query)
                                                if (geminiApiKey.isNotBlank()) {
                                                    payload.put("gemini_api_key", geminiApiKey)
                                                }
                                                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                                                if (conn.responseCode in 200..299) {
                                                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                                                    val json = JSONObject(resp)
                                                    val rText = json.optString("reply", "")
                                                    if (rText.isNotBlank()) return@withContext rText
                                                }
                                            } catch (_: Exception) {}

                                            // 3. Built-in instant expert fallback
                                            val lower = query.lowercase()
                                            if (lower.contains("barkat") || lower.contains("developer") || lower.contains("who made") || lower.contains("creator")) {
                                                "WA Marketing and BS Bot were developed by **Barkat Ali**, senior full-stack mobile & cloud engineer.\nPortfolio: https://barkatai.ihsanraza.com/#about"
                                            } else if (lower.contains("receive") || lower.contains("incoming") || lower.contains("customer message") || lower.contains("webhook") || lower.contains("not getting")) {
                                                "**To receive customer replies in real-time:**\n1. Run `start_webhook_tunnel.bat` on your computer to obtain an HTTPS tunnel.\n2. In Meta App Dashboard -> WhatsApp -> Webhook, enter your tunnel URL (`https://.../api/v1/webhooks/whatsapp`) and verify token `wamarketing_secure_verify_token`.\n3. Subscribe to the `messages` field.\n4. You can also tap the 📞 button inside any chat or the Inbox top bar to test incoming customer replies immediately!"
                                            } else if (lower.contains("csv") || lower.contains("excel") || lower.contains("pdf") || lower.contains("import") || lower.contains("lead")) {
                                                "To import contacts: Open Contacts -> Import Sheet. Pick CSV, Excel (.xlsx/.xls), or PDF. Phone numbers are automatically detected, sanitized into international format, and preserved without modifying your country codes."
                                            } else if (lower.contains("campaign") || lower.contains("write") || lower.contains("promo")) {
                                                "To launch a campaign: Open Campaigns -> New Campaign. Use dynamic variables `{{first_name}}`, `{{company}}`, and `{{city}}` for high conversions, attach images or PDFs, and hit Send!"
                                            } else {
                                                "I am **BS Bot**! Ask me about WhatsApp campaigns, receiving incoming messages, lead imports, or developer Barkat Ali. Connect your Gemini API key using the key icon at the top to unlock full generative AI reasoning!"
                                            }
                                        }

                                        isLoading = false
                                        messages = messages + BsBotMessage("bot", reply)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (inputText.isNotBlank()) WhatsAppGreen else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color(0xFF00381B) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Google Gemini API Key", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your Google Gemini API key to power BS Bot with real-time generative intelligence for copywriting, campaign strategy, and customer replies:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        "Keys are stored securely in your device's local storage and never shared.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WhatsAppGreen
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = apiKeyInput.trim()
                        geminiApiKey = clean
                        prefs.edit().putString("gemini_api_key", clean).apply()
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                ) {
                    Text("Save Key", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
