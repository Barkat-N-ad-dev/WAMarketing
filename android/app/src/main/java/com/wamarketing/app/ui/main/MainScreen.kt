package com.wamarketing.app.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.wamarketing.app.data.SessionManager
import com.wamarketing.app.data.WAMarketingRepository
import com.wamarketing.app.model.*
import com.wamarketing.app.theme.*
import com.wamarketing.app.ui.navigation.AppDestination
import com.wamarketing.app.ui.screens.ai.AiAssistantScreen
import com.wamarketing.app.ui.screens.analytics.AnalyticsScreen
import com.wamarketing.app.ui.screens.automation.AutomationScreen
import com.wamarketing.app.ui.screens.campaigns.CampaignBuilderScreen
import com.wamarketing.app.ui.screens.campaigns.CampaignsScreen
import com.wamarketing.app.ui.screens.contacts.ContactsScreen
import com.wamarketing.app.ui.screens.contacts.ImportContactsScreen
import com.wamarketing.app.ui.screens.crm.CrmPipelineScreen
import com.wamarketing.app.ui.screens.dashboard.DashboardScreen
import com.wamarketing.app.ui.screens.inbox.InboxScreen
import com.wamarketing.app.ui.screens.media.MediaLibraryScreen
import com.wamarketing.app.ui.screens.settings.SettingsScreen
import com.wamarketing.app.ui.components.BsBotFloatingBubble
import com.wamarketing.app.ui.components.BsBotDialog
import com.wamarketing.app.ui.screens.templates.TemplatesScreen
import com.wamarketing.app.ui.screens.welcome.WelcomeScreen
import com.wamarketing.app.ui.screens.wallet.WalletRechargeDialog
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: WAMarketingRepository,
    sessionManager: SessionManager,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        repository.notificationEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    val hasSeenOnboarding by sessionManager.hasSeenOnboarding.collectAsState()

    if (!hasSeenOnboarding) {
        WelcomeScreen(
            onGetStarted = { sessionManager.completeOnboarding() },
            modifier = modifier
        )
        return
    }

    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }
    var showCampaignBuilder by remember { mutableStateOf(false) }
    var showImportContacts by remember { mutableStateOf(false) }
    var showBsBotDialog by remember { mutableStateOf(false) }

    val stats by repository.stats.collectAsState()
    val contacts by repository.contacts.collectAsState()
    val campaigns by repository.campaigns.collectAsState()
    val templates by repository.templates.collectAsState()
    val conversations by repository.conversations.collectAsState()
    val leads by repository.leads.collectAsState()
    val automations by repository.automations.collectAsState()
    val whatsappAccounts by repository.whatsappAccounts.collectAsState()
    val mediaFiles by repository.mediaFiles.collectAsState()
    val messagesMap by repository.messagesMap.collectAsState()
    val walletBalance by repository.walletBalance.collectAsState()
    val walletTransactions by repository.walletTransactions.collectAsState()

    var showWalletDialogInMain by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF00381B))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("WAMarketing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Enterprise SaaS Suite", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                AppDestination.values().forEach { destination ->
                    NavigationDrawerItem(
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.title) },
                        selected = currentDestination == destination && !showCampaignBuilder && !showImportContacts,
                        onClick = {
                            currentDestination = destination
                            showCampaignBuilder = false
                            showImportContacts = false
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = WhatsAppGreen.copy(alpha = 0.2f),
                            selectedIconColor = WhatsAppGreen,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable {
                            showWalletDialogInMain = true
                            scope.launch { drawerState.close() }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = WhatsAppGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = WhatsAppDarkGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Wallet Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$%.2f USD".format(Locale.US, walletBalance), fontWeight = FontWeight.Bold, color = WhatsAppDarkGreen)
                            }
                        }
                        Text("Top Up +", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = WhatsAppDarkGreen)
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(WhatsAppGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Cloud API Status: Active", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("FastAPI + SQLite Store", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            val activeTitle = when {
                                showCampaignBuilder -> "Campaign Wizard"
                                showImportContacts -> "Import Leads Sheet"
                                else -> currentDestination.title
                            }
                            Text(
                                text = activeTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            val subtitle = when {
                                showCampaignBuilder -> "Create, segment & launch outreach"
                                showImportContacts -> "Batch CSV contact importer"
                                currentDestination == AppDestination.DASHBOARD -> "Official Meta Cloud API • Analytics"
                                currentDestination == AppDestination.MEDIA_LIBRARY -> "Manage images, PDFs, videos & APKs"
                                currentDestination == AppDestination.INBOX -> "Two-Way WhatsApp Customer Conversations"
                                else -> "WAMarketing Enterprise Platform"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        AssistChip(
                            onClick = { showWalletDialogInMain = true },
                            label = {
                                Text(
                                    "$%.2f".format(Locale.US, walletBalance),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WhatsAppDarkGreen
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = WhatsAppDarkGreen
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = WhatsAppGreen.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, WhatsAppGreen)
                        )
                        IconButton(onClick = { currentDestination = AppDestination.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    val primaryDestinations = listOf(
                        AppDestination.DASHBOARD,
                        AppDestination.CONTACTS,
                        AppDestination.CAMPAIGNS,
                        AppDestination.INBOX,
                        AppDestination.CRM
                    )

                    primaryDestinations.forEach { dest ->
                        val isSelected = currentDestination == dest && !showCampaignBuilder && !showImportContacts
                        NavigationBarItem(
                            icon = { Icon(dest.icon, contentDescription = dest.title) },
                            label = { Text(dest.title, style = MaterialTheme.typography.labelSmall) },
                            selected = isSelected,
                            onClick = {
                                currentDestination = dest
                                showCampaignBuilder = false
                                showImportContacts = false
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00381B),
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = WhatsAppGreen
                            )
                        )
                    }

                    // More Menu Button opens Drawer
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.MoreVert, contentDescription = "More") },
                        label = { Text("More", style = MaterialTheme.typography.labelSmall) },
                        selected = currentDestination in listOf(
                            AppDestination.MEDIA_LIBRARY,
                            AppDestination.TEMPLATES,
                            AppDestination.AUTOMATION,
                            AppDestination.AI_ASSISTANT,
                            AppDestination.ANALYTICS,
                            AppDestination.SETTINGS
                        ),
                        onClick = { scope.launch { drawerState.open() } }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    showCampaignBuilder -> {
                        CampaignBuilderScreen(
                            templates = templates,
                            mediaFiles = mediaFiles,
                            onImportLeadsSheet = { newContacts ->
                                repository.importLeadsSheet(newContacts)
                            },
                            onAddMedia = { newMedia ->
                                repository.addMediaFile(newMedia)
                            },
                            onBack = { showCampaignBuilder = false },
                            onSaveCampaign = { newCamp ->
                                repository.addCampaign(newCamp)
                                showCampaignBuilder = false
                                currentDestination = AppDestination.CAMPAIGNS
                            }
                        )
                    }
                    showImportContacts -> {
                        ImportContactsScreen(
                            onBack = { showImportContacts = false },
                            onImportContacts = { newContacts ->
                                repository.importLeadsSheet(newContacts)
                            },
                            onImportComplete = {
                                showImportContacts = false
                                currentDestination = AppDestination.CONTACTS
                            }
                        )
                    }
                    else -> {
                        when (currentDestination) {
                            AppDestination.DASHBOARD -> DashboardScreen(
                                stats = stats,
                                onNavigate = { dest ->
                                    currentDestination = dest
                                    if (dest == AppDestination.CAMPAIGNS) showCampaignBuilder = false
                                }
                            )
                            AppDestination.CONTACTS -> ContactsScreen(
                                contacts = contacts,
                                onAddContact = { repository.addContact(it) },
                                onDeleteContact = { repository.deleteContact(it) },
                                onOpenImport = { showImportContacts = true },
                                onSendMessageToContact = { contact, text, tmpl ->
                                    repository.sendMessageToContact(contact, text, tmpl)
                                    currentDestination = AppDestination.INBOX
                                }
                            )
                            AppDestination.CAMPAIGNS -> CampaignsScreen(
                                campaigns = campaigns,
                                onCreateNewCampaign = { showCampaignBuilder = true },
                                onLaunchCampaign = { campId ->
                                    val camp = campaigns.find { it.id == campId }
                                    if (camp != null) {
                                        repository.addCampaign(camp.copy(status = "RUNNING"))
                                    }
                                }
                            )
                            AppDestination.MEDIA_LIBRARY -> MediaLibraryScreen(
                                mediaFiles = mediaFiles,
                                onAddMedia = { repository.addMediaFile(it) },
                                onDeleteMedia = { repository.deleteMediaFile(it) },
                                onUseInCampaign = {
                                    showCampaignBuilder = true
                                }
                            )
                            AppDestination.TEMPLATES -> TemplatesScreen(
                                templates = templates,
                                onCreateTemplate = { repository.addTemplate(it) },
                                onSyncMeta = { repository.syncTemplatesFromMeta() }
                            )
                            AppDestination.INBOX -> {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                InboxScreen(
                                    conversations = conversations,
                                    contacts = contacts,
                                    templates = templates,
                                    messagesMap = messagesMap,
                                    onSendMessage = { convId, text, tmpl, mediaUrl ->
                                        val conv = conversations.find { it.id == convId }
                                        val targetContact = contacts.find { it.id == conv?.contactId }
                                            ?: ContactItem(
                                                id = conv?.contactId ?: (5000..9999).random(),
                                                firstName = conv?.contactName?.split(" ")?.firstOrNull() ?: "Customer",
                                                lastName = conv?.contactName?.split(" ")?.drop(1)?.joinToString(" ") ?: "",
                                                phoneNumber = conv?.phoneNumber ?: "",
                                                whatsappNumber = conv?.phoneNumber ?: "",
                                                optInStatus = "opted_in"
                                            )
                                        repository.sendMessageToContact(targetContact, text, tmpl, mediaUrl)
                                    },
                                    onSendFile = { convId, phoneNumber, fileUri, caption ->
                                        repository.uploadAndSendFileDirectly(context, convId, phoneNumber, fileUri, caption)
                                    },
                                    onReceiveMessage = { fromPhone, text, mediaUrl ->
                                        repository.recordInboundMessage(fromPhone, text, mediaUrl)
                                    },
                                    onSyncBackend = {
                                        repository.syncWithBackend()
                                    },
                                    onFetchMessages = { convId ->
                                        repository.fetchMessagesForConversation(convId)
                                    }
                                )
                            }
                            AppDestination.CRM -> CrmPipelineScreen(
                                leads = leads
                            )
                            AppDestination.AUTOMATION -> AutomationScreen(
                                automations = automations
                            )
                            AppDestination.AI_ASSISTANT -> AiAssistantScreen()
                            AppDestination.ANALYTICS -> AnalyticsScreen(
                                stats = stats
                            )
                            AppDestination.SETTINGS -> SettingsScreen(
                                accounts = whatsappAccounts,
                                walletBalance = walletBalance,
                                walletTransactions = walletTransactions,
                                onRechargeWallet = { amount, method ->
                                    repository.rechargeWallet(amount, method)
                                },
                                onConnectAccount = { acc ->
                                    sessionManager.maintainSession()
                                    repository.addWhatsAppAccount(acc)
                                },
                                onSetDefaultAccount = { repository.setDefaultWhatsAppAccount(it) },
                                onDisconnectAccount = { repository.disconnectWhatsAppAccount(it) },
                                onRestoreAccount = { repository.restoreWhatsAppAccount(it) },
                                onSyncAccount = { repository.syncWhatsAppAccount(it) },
                                onResetClientState = { repository.resetToCleanClientState() },
                                onPurgeDemoData = { repository.purgeAllDemoData() },
                                onUpdateBackendUrl = { repository.setBackendUrl(it) },
                                onUpdateGeminiKey = { repository.setGeminiApiKey(it) },
                                onLogout = {
                                    sessionManager.logout()
                                    currentDestination = AppDestination.DASHBOARD
                                }
                            )
                        }
                    }
                }

                // Moveable Floating BS Bot Chat Icon Overlay (Drag anywhere on screen)
                BsBotFloatingBubble(
                    onClick = { showBsBotDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 90.dp, end = 12.dp)
                )

                if (showBsBotDialog) {
                    BsBotDialog(
                        onDismiss = { showBsBotDialog = false },
                        backendUrl = repository.getBackendUrl()
                    )
                }

                if (showWalletDialogInMain) {
                    WalletRechargeDialog(
                        currentBalance = walletBalance,
                        transactions = walletTransactions,
                        repository = repository,
                        onDismiss = { showWalletDialogInMain = false },
                        onRecharge = { amount, method ->
                            repository.rechargeWallet(amount, method)
                        }
                    )
                }
            }
        }
    }
}
