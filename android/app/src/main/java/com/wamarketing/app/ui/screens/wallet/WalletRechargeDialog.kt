package com.wamarketing.app.ui.screens.wallet

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wamarketing.app.data.WAMarketingRepository
import com.wamarketing.app.model.WalletTransactionItem
import com.wamarketing.app.theme.WhatsAppDarkGreen
import com.wamarketing.app.theme.WhatsAppGreen
import com.wamarketing.app.ui.components.StatusBadge
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletRechargeDialog(
    currentBalance: Double,
    transactions: List<WalletTransactionItem>,
    repository: WAMarketingRepository? = null,
    onDismiss: () -> Unit,
    onRecharge: ((amount: Double, paymentMethod: String) -> Unit)? = null
) {
    val activeRepo = repository ?: WAMarketingRepository.getInstance()

    // Dialog Steps: 0 = Input Details, 1 = 3D Secure / OTP Verification, 2 = Success Receipt
    var checkoutStep by remember { mutableStateOf(0) }
    var currentTab by remember { mutableStateOf(0) } // 0: Top-Up, 1: History, 2: Meta Rates

    var selectedAmount by remember { mutableStateOf(25.0) }
    var customAmountText by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("UPI") } // "UPI", "Card", "Stripe", "PayPal"

    // Form fields
    var upiId by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    var cardHolder by remember { mutableStateOf("") }
    var stripeEmail by remember { mutableStateOf("") }
    var payPalEmail by remember { mutableStateOf("") }

    // Validation & Error states
    var formError by remember { mutableStateOf<String?>(null) }
    var upiError by remember { mutableStateOf<String?>(null) }
    var cardError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }

    // OTP Verification states
    var activeSessionId by remember { mutableStateOf("") }
    var maskedDestination by remember { mutableStateOf("") }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var userPhoneNumber by remember { mutableStateOf("+92 309 369 7069") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }

    // Resend countdown
    var resendCountdown by remember { mutableStateOf(60) }
    var isResending by remember { mutableStateOf(false) }

    // Success receipt data
    var settledReferenceId by remember { mutableStateOf("") }
    var settledAmount by remember { mutableStateOf(0.0) }
    var settledMethod by remember { mutableStateOf("") }

    val quickAmounts = listOf(10.0, 25.0, 50.0, 100.0, 250.0)
    val finalAmount = if (customAmountText.isNotBlank()) {
        customAmountText.toDoubleOrNull() ?: selectedAmount
    } else {
        selectedAmount
    }

    // Countdown timer effect
    LaunchedEffect(checkoutStep, resendCountdown) {
        if (checkoutStep == 1 && resendCountdown > 0) {
            delay(1000)
            resendCountdown -= 1
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isSubmitting && !isVerifyingOtp) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(WhatsAppGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (checkoutStep) {
                                    1 -> Icons.Default.Lock
                                    2 -> Icons.Default.CheckCircle
                                    else -> Icons.Default.ShoppingCart
                                },
                                contentDescription = null,
                                tint = Color(0xFF00381B),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                when (checkoutStep) {
                                    1 -> "3D Secure 2FA Verification"
                                    2 -> "Recharge Successful"
                                    else -> "Meta Messaging Wallet"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                when (checkoutStep) {
                                    1 -> "Bank Authorization & Security Check"
                                    2 -> "Payment Settled & Wallet Credited"
                                    else -> "Production Cloud API Balance Top-Up"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (checkoutStep != 2) {
                        IconButton(onClick = onDismiss, enabled = !isSubmitting && !isVerifyingOtp) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Balance Card (Only visible when selecting or viewing tabs)
                if (checkoutStep == 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = WhatsAppDarkGreen),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "AVAILABLE BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WhatsAppGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "$%.2f USD".format(Locale.US, currentBalance),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Capacity: ~%d Marketing / ~%d Utility messages".format(
                                        (currentBalance / 0.05).toInt(),
                                        (currentBalance / 0.02).toInt()
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            StatusBadge(
                                text = if (currentBalance > 5.0) "Active" else "Low Balance",
                                statusType = if (currentBalance > 5.0) "success" else "warning"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Navigation Tabs
                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = WhatsAppDarkGreen,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            text = { Text("Recharge", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            text = { Text("History (${transactions.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            text = { Text("Meta Rates", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Main Step Container
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        checkoutStep == 1 -> {
                            // STEP 1: 3D Secure / OTP Verification Screen
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = WhatsAppDarkGreen,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Payment Authorization Required",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "To charge $%.2f USD via %s, please enter the 6-digit One-Time Password (OTP) sent to your registered device.".format(
                                                Locale.US, finalAmount, selectedPaymentMethod
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = WhatsAppGreen.copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Phone, contentDescription = null, tint = WhatsAppDarkGreen, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Sent to: $maskedDestination",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = WhatsAppDarkGreen
                                                )
                                            }
                                        }
                                    }
                                }

                                // 6-digit OTP input
                                OutlinedTextField(
                                    value = enteredOtp,
                                    onValueChange = {
                                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                            enteredOtp = it
                                            otpError = null
                                        }
                                    },
                                    label = { Text("Enter 6-Digit OTP Code") },
                                    placeholder = { Text("• • • • • •") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 6.sp,
                                        textAlign = TextAlign.Center,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    isError = otpError != null,
                                    modifier = Modifier.fillMaxWidth(0.85f),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                otpError?.let { err ->
                                    Text(
                                        err,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Resend Timer Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (resendCountdown > 0) {
                                        Text(
                                            "Resend OTP in ${resendCountdown}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        TextButton(
                                            onClick = {
                                                isResending = true
                                                activeRepo.resendWalletOtp(activeSessionId) { success, msg ->
                                                    isResending = false
                                                    if (success) {
                                                        resendCountdown = 60
                                                        otpError = null
                                                    } else {
                                                        otpError = msg
                                                    }
                                                }
                                            },
                                            enabled = !isResending
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isResending) "Sending..." else "Resend OTP", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Actions: Back or Authorize
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            checkoutStep = 0
                                            enteredOtp = ""
                                            otpError = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isVerifyingOtp
                                    ) {
                                        Text("Back")
                                    }

                                    Button(
                                        onClick = {
                                            if (enteredOtp.length != 6) {
                                                otpError = "Please enter the full 6-digit OTP code."
                                                return@Button
                                            }
                                            isVerifyingOtp = true
                                            otpError = null

                                            activeRepo.verifyWalletOtpAndComplete(activeSessionId, enteredOtp) { result ->
                                                isVerifyingOtp = false
                                                if (result.success) {
                                                    settledReferenceId = result.referenceId ?: "PG-TXN-${(100000..999999).random()}"
                                                    settledAmount = finalAmount
                                                    settledMethod = selectedPaymentMethod
                                                    checkoutStep = 2
                                                    onRecharge?.invoke(finalAmount, selectedPaymentMethod)
                                                } else {
                                                    otpError = result.message
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(2f),
                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isVerifyingOtp && enteredOtp.length == 6
                                    ) {
                                        if (isVerifyingOtp) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color(0xFF00381B),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Verifying...", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00381B), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Authorize $%.2f".format(Locale.US, finalAmount), color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        checkoutStep == 2 -> {
                            // STEP 2: Succeeded Receipt
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(WhatsAppGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF00381B),
                                        modifier = Modifier.size(42.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    "Payment Verified & Settled!",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = WhatsAppDarkGreen
                                )

                                Text(
                                    "Your wallet has been credited successfully.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ReceiptRow("Amount Credited", "+$%.2f USD".format(Locale.US, settledAmount), highlight = true)
                                        ReceiptRow("Payment Method", settledMethod)
                                        ReceiptRow("Gateway Reference", settledReferenceId)
                                        ReceiptRow("Status", "SUCCESS • VERIFIED")
                                        ReceiptRow("New Available Balance", "$%.2f USD".format(Locale.US, currentBalance + settledAmount))
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Done • Return to App", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        else -> {
                            // STEP 0: Selection & Form Entry
                            when (currentTab) {
                                0 -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        item {
                                            Text(
                                                "1. Select Recharge Amount",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                quickAmounts.forEach { amt ->
                                                    val isSelected = selectedAmount == amt && customAmountText.isBlank()
                                                    Surface(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                selectedAmount = amt
                                                                customAmountText = ""
                                                            },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (isSelected) WhatsAppGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                        border = if (isSelected) BorderStroke(2.dp, WhatsAppDarkGreen) else null
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.padding(vertical = 10.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                "+$${amt.toInt()}",
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isSelected) Color(0xFF00381B) else MaterialTheme.colorScheme.onSurface,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(
                                                value = customAmountText,
                                                onValueChange = { customAmountText = it },
                                                label = { Text("Or enter custom amount ($ USD)") },
                                                leadingIcon = { Text("$", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp), color = WhatsAppDarkGreen) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                singleLine = true
                                            )
                                        }

                                        item {
                                            Text(
                                                "2. Choose Payment Method",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            val methods = listOf("UPI", "Card", "Stripe", "PayPal")
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                methods.forEach { method ->
                                                    val isSelected = selectedPaymentMethod == method
                                                    Surface(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                selectedPaymentMethod = method
                                                                formError = null
                                                                upiError = null
                                                                cardError = null
                                                                emailError = null
                                                            },
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = if (isSelected) WhatsAppGreen.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                        border = BorderStroke(
                                                            if (isSelected) 2.dp else 1.dp,
                                                            if (isSelected) WhatsAppGreen else MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = when (method) {
                                                                    "UPI" -> Icons.Default.Send
                                                                    "Card" -> Icons.Default.AccountBox
                                                                    "Stripe" -> Icons.Default.Lock
                                                                    else -> Icons.Default.CheckCircle
                                                                },
                                                                contentDescription = null,
                                                                tint = if (isSelected) WhatsAppDarkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                method,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = if (isSelected) WhatsAppDarkGreen else MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        item {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                border = CardDefaults.outlinedCardBorder()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    when (selectedPaymentMethod) {
                                                        "UPI" -> {
                                                            Text("Unified Payments Interface (UPI)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                            Text("Requires valid VPA in format 'username@bank' (e.g., yourname@okhdfcbank or 9876543210@paytm).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            OutlinedTextField(
                                                                value = upiId,
                                                                onValueChange = {
                                                                    upiId = it
                                                                    upiError = null
                                                                    formError = null
                                                                },
                                                                label = { Text("UPI ID / VPA") },
                                                                placeholder = { Text("username@bank") },
                                                                leadingIcon = { Icon(Icons.Default.Send, contentDescription = null) },
                                                                isError = upiError != null,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(10.dp),
                                                                singleLine = true
                                                            )

                                                            upiError?.let {
                                                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                                            }

                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                listOf("@okaxis", "@okhdfcbank", "@paytm", "@ybl").forEach { suffix ->
                                                                    AssistChip(
                                                                        onClick = {
                                                                            val prefix = upiId.substringBefore("@").ifBlank { "business" }
                                                                            upiId = "$prefix$suffix"
                                                                            upiError = null
                                                                        },
                                                                        label = { Text(suffix, style = MaterialTheme.typography.labelSmall) }
                                                                    )
                                                                }
                                                            }

                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text("Registered WhatsApp Number (for 2FA OTP)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                                                            OutlinedTextField(
                                                                value = userPhoneNumber,
                                                                onValueChange = {
                                                                    userPhoneNumber = it
                                                                    phoneError = null
                                                                    formError = null
                                                                },
                                                                label = { Text("Mobile Number (E.164)") },
                                                                placeholder = { Text("+92 309 369 7069") },
                                                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                                isError = phoneError != null,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(10.dp),
                                                                singleLine = true
                                                            )
                                                            phoneError?.let {
                                                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                                            }
                                                            Text(
                                                                "A genuine 6-digit OTP code will be sent to your WhatsApp account at this number. No balance is credited until verified.",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        "Card" -> {
                                                            Text("Credit / Debit Card (3D Secure)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                            Text("Visa, MasterCard, American Express, RuPay supported with Luhn checksum validation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            OutlinedTextField(
                                                                value = cardNumber,
                                                                onValueChange = {
                                                                    if (it.length <= 19) {
                                                                        cardNumber = it
                                                                        cardError = null
                                                                    }
                                                                },
                                                                label = { Text("Card Number (16 Digits)") },
                                                                placeholder = { Text("4242 4242 4242 4242") },
                                                                leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null) },
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                                isError = cardError != null,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(10.dp),
                                                                singleLine = true
                                                            )

                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                OutlinedTextField(
                                                                    value = cardExpiry,
                                                                    onValueChange = {
                                                                        if (it.length <= 5) {
                                                                            cardExpiry = it
                                                                            cardError = null
                                                                        }
                                                                    },
                                                                    label = { Text("MM/YY") },
                                                                    placeholder = { Text("12/28") },
                                                                    modifier = Modifier.weight(1f),
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    singleLine = true
                                                                )
                                                                OutlinedTextField(
                                                                    value = cardCvv,
                                                                    onValueChange = {
                                                                        if (it.length <= 4) {
                                                                            cardCvv = it
                                                                            cardError = null
                                                                        }
                                                                    },
                                                                    label = { Text("CVV") },
                                                                    placeholder = { Text("123") },
                                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                                                    modifier = Modifier.weight(1f),
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    singleLine = true
                                                                )
                                                            }

                                                            OutlinedTextField(
                                                                value = cardHolder,
                                                                onValueChange = {
                                                                    cardHolder = it
                                                                    cardError = null
                                                                },
                                                                label = { Text("Cardholder Name") },
                                                                placeholder = { Text("Barkat Ali") },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(10.dp),
                                                                singleLine = true
                                                            )

                                                            OutlinedTextField(
                                                                value = userPhoneNumber,
                                                                onValueChange = {
                                                                    userPhoneNumber = it
                                                                    phoneError = null
                                                                },
                                                                label = { Text("Bank Registered Mobile Number") },
                                                                placeholder = { Text("+92 309 369 7069") },
                                                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(10.dp),
                                                                singleLine = true
                                                            )

                                                            cardError?.let {
                                                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                        "Stripe" -> {
                                                            Text("Stripe Instant Payment", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                            Text("Direct, tokenized 1-click international checkout powered by Stripe Elements.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            OutlinedTextField(
                                                                value = stripeEmail,
                                                                onValueChange = {
                                                                    stripeEmail = it
                                                                    emailError = null
                                                                },
                                                                label = { Text("Receipt Email Address") },
                                                                placeholder = { Text("client@company.com") },
                                                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                                                isError = emailError != null,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(10.dp),
                                                                singleLine = true
                                                            )
                                                            emailError?.let {
                                                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                        "PayPal" -> {
                                                            Text("PayPal One-Touch", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                            Text("Instant wallet deduction linked to your PayPal account or PayPal balance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            OutlinedTextField(
                                                                value = payPalEmail,
                                                                onValueChange = {
                                                                    payPalEmail = it
                                                                    emailError = null
                                                                },
                                                                label = { Text("PayPal Account Email") },
                                                                placeholder = { Text("user@paypal.com") },
                                                                leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                                                isError = emailError != null,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(10.dp),
                                                                singleLine = true
                                                            )
                                                            emailError?.let {
                                                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        formError?.let { err ->
                                            item {
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    // Transaction History Tab
                                    if (transactions.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "No wallet transactions recorded yet.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(transactions) { tx ->
                                                val isRecharge = tx.type == "RECHARGE"
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(CircleShape)
                                                                    .background(
                                                                        if (isRecharge) WhatsAppGreen.copy(alpha = 0.2f)
                                                                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                                                    ),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isRecharge) Icons.Default.Add else Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = if (isRecharge) WhatsAppDarkGreen else MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Column {
                                                                Text(
                                                                    tx.description,
                                                                    fontWeight = FontWeight.Bold,
                                                                    style = MaterialTheme.typography.bodyMedium
                                                                )
                                                                Text(
                                                                    "${tx.date} • ${tx.paymentMethod}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                                tx.referenceId?.let { ref ->
                                                                    Text(
                                                                        "Ref: $ref",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Text(
                                                            "${if (isRecharge) "+" else "-"}$%.2f".format(Locale.US, tx.amount),
                                                            fontWeight = FontWeight.ExtraBold,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = if (isRecharge) WhatsAppDarkGreen else MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    // Meta Rates Tab
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        item {
                                            Text(
                                                "Official Meta WhatsApp Business Cloud Rates",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                "Fees are deducted per message delivered according to Meta's published pricing schedule.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        item {
                                            RateCard(
                                                title = "Marketing Template",
                                                rate = "$0.05 / message",
                                                description = "Promotional campaigns, offers, announcements, and product launches.",
                                                icon = Icons.Default.Share
                                            )
                                        }

                                        item {
                                            RateCard(
                                                title = "Utility Template",
                                                rate = "$0.02 / message",
                                                description = "Order confirmations, updates, account alerts, and billing receipts.",
                                                icon = Icons.Default.Build
                                            )
                                        }

                                        item {
                                            RateCard(
                                                title = "Service (Customer Care)",
                                                rate = "$0.01 / message",
                                                description = "User-initiated customer inquiries and 1-on-1 support conversations.",
                                                icon = Icons.Default.Email
                                            )
                                        }

                                        item {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                color = WhatsAppGreen.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, WhatsAppGreen)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        "🎁 Free Tier Included",
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF00381B),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        "Meta grants the first 1,000 Service conversations every month completely free for each WhatsApp Business Account.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF00381B).copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action for Step 0 Recharge
                if (checkoutStep == 0 && currentTab == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                formError = null
                                upiError = null
                                cardError = null
                                emailError = null
                                phoneError = null

                                // Validate details map
                                val detailsMap = mutableMapOf<String, String>()
                                when (selectedPaymentMethod) {
                                    "UPI" -> {
                                        val cleanUpi = upiId.trim()
                                        if (cleanUpi.isBlank() || !cleanUpi.contains("@") || cleanUpi.substringBefore("@").length < 2 || cleanUpi.substringAfter("@").length < 2) {
                                            upiError = "Invalid UPI ID. Must contain '@bank' (e.g. name@okhdfcbank or 9876543210@paytm)."
                                            formError = "Please enter a valid UPI address before proceeding."
                                            return@Button
                                        }
                                        val cleanPhone = userPhoneNumber.trim().replace(" ", "").replace("-", "")
                                        if (cleanPhone.length < 9 || !cleanPhone.matches(Regex("^\\+?[0-9]{9,16}$"))) {
                                            phoneError = "Please enter a valid registered mobile number (e.g. +92 309 369 7069) to receive the 2FA OTP."
                                            formError = phoneError
                                            return@Button
                                        }
                                        detailsMap["upi_id"] = cleanUpi
                                        detailsMap["phone_number"] = cleanPhone
                                    }
                                    "Card" -> {
                                        val cleanCard = cardNumber.replace(" ", "").replace("-", "")
                                        if (cleanCard.length !in 13..19) {
                                            cardError = "Please enter a valid card number (13-19 digits)."
                                            formError = cardError
                                            return@Button
                                        }
                                        // Luhn check
                                        var sum = 0
                                        val reversed = cleanCard.reversed()
                                        for (i in reversed.indices) {
                                            val digit = reversed[i] - '0'
                                            if (i % 2 == 1) {
                                                val doubled = digit * 2
                                                sum += if (doubled > 9) doubled - 9 else doubled
                                            } else {
                                                sum += digit
                                            }
                                        }
                                        if (sum % 10 != 0) {
                                            cardError = "Card number failed Luhn checksum validation. Please check digits."
                                            formError = cardError
                                            return@Button
                                        }
                                        if (!cardExpiry.matches(Regex("^(0[1-9]|1[0-2])/[0-9]{2}$"))) {
                                            cardError = "Invalid expiry date (MM/YY format required)."
                                            formError = cardError
                                            return@Button
                                        }
                                        if (cardCvv.length !in 3..4) {
                                            cardError = "Invalid CVV (3-4 digits required)."
                                            formError = cardError
                                            return@Button
                                        }
                                        if (cardHolder.trim().length < 2) {
                                            cardError = "Cardholder name is required."
                                            formError = cardError
                                            return@Button
                                        }
                                        val cleanPhone = userPhoneNumber.trim().replace(" ", "").replace("-", "")
                                        if (cleanPhone.length < 9) {
                                            phoneError = "Cardholder registered mobile number is required to receive bank OTP."
                                            formError = phoneError
                                            return@Button
                                        }
                                        detailsMap["card_number"] = cleanCard
                                        detailsMap["card_expiry"] = cardExpiry.trim()
                                        detailsMap["card_cvv"] = cardCvv.trim()
                                        detailsMap["card_holder"] = cardHolder.trim()
                                        detailsMap["phone_number"] = cleanPhone
                                    }
                                    "Stripe" -> {
                                        if (!stripeEmail.matches(Regex("^[^@]+@[^@]+\\.[^@]+$"))) {
                                            emailError = "Please enter a valid Stripe receipt email."
                                            formError = emailError
                                            return@Button
                                        }
                                        detailsMap["email"] = stripeEmail.trim()
                                    }
                                    "PayPal" -> {
                                        if (!payPalEmail.matches(Regex("^[^@]+@[^@]+\\.[^@]+$"))) {
                                            emailError = "Please enter a valid PayPal account email."
                                            formError = emailError
                                            return@Button
                                        }
                                        detailsMap["email"] = payPalEmail.trim()
                                    }
                                }

                                isSubmitting = true
                                activeRepo.initiateWalletRecharge(
                                    amount = finalAmount,
                                    paymentMethod = selectedPaymentMethod,
                                    details = detailsMap
                                ) { result ->
                                    isSubmitting = false
                                    if (result.success) {
                                        activeSessionId = result.sessionId ?: ""
                                        maskedDestination = result.maskedDestination ?: "registered device"
                                        enteredOtp = ""
                                        otpError = null
                                        resendCountdown = 60
                                        checkoutStep = 1
                                    } else {
                                        formError = result.message
                                    }
                                }
                            },
                            modifier = Modifier.weight(2f),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting && finalAmount >= 1.0
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF00381B),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Validating...", color = Color(0xFF00381B), fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF00381B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Proceed to Pay $%.2f".format(Locale.US, finalAmount),
                                    color = Color(0xFF00381B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (highlight) WhatsAppDarkGreen else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RateCard(
    title: String,
    rate: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = WhatsAppDarkGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(rate, fontWeight = FontWeight.ExtraBold, color = WhatsAppDarkGreen, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
