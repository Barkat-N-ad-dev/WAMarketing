package com.wamarketing.app.model

data class TagItem(
    val id: Int,
    val name: String,
    val color: String
)

data class ContactItem(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val whatsappNumber: String,
    val email: String? = null,
    val company: String? = null,
    val city: String? = null,
    val country: String? = null,
    val notes: String = "",
    val leadStatus: String = "New Lead",
    val customerStatus: String = "Lead",
    val optInStatus: String = "opted_in", // opted_in, opted_out, unknown
    val tags: List<TagItem> = emptyList(),
    val lastInteraction: String? = null
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

data class CampaignItem(
    val id: Int,
    val name: String,
    val status: String, // DRAFT, SCHEDULED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    val messageContent: String,
    val mediaUrl: String? = null,
    val templateName: String? = null,
    val templateCategory: String? = null,
    val scheduledAt: String? = null,
    val totalRecipients: Int = 0,
    val sentCount: Int = 0,
    val deliveredCount: Int = 0,
    val readCount: Int = 0,
    val failedCount: Int = 0,
    val replyCount: Int = 0,
    val optOutCount: Int = 0,
    val createdAt: String = ""
)

data class TemplateItem(
    val id: Int,
    val name: String,
    val category: String, // Marketing, Utility, Authentication, Follow-up, Welcome, Appointment, Support
    val language: String = "en",
    val content: String,
    val templateType: String, // "Local App Template" vs "WhatsApp Approved Template"
    val status: String = "APPROVED", // APPROVED, PENDING, REJECTED
    val variables: List<String> = emptyList(),
    val buttonText: String? = null,
    val buttonUrl: String? = null
)

data class ConversationItem(
    val id: Int,
    val contactId: Int,
    val contactName: String,
    val phoneNumber: String,
    val status: String, // OPEN, PENDING, RESOLVED
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageAt: String = "",
    val internalNotes: String = "",
    val tags: List<TagItem> = emptyList()
)

data class MessageBubbleItem(
    val id: Int,
    val conversationId: Int,
    val direction: String, // INBOUND, OUTBOUND
    val messageType: String, // text, image, video, document, template
    val body: String,
    val mediaUrl: String? = null,
    val status: String, // SENT, DELIVERED, READ, FAILED
    val timestamp: String
)

data class LeadItem(
    val id: Int,
    val contactId: Int,
    val contactName: String,
    val phoneNumber: String,
    val company: String? = null,
    val stage: String, // New Lead, Contacted, Interested, Follow-up, Negotiation, Customer, Closed
    val dealValue: Double = 0.0,
    val notes: String = "",
    val nextFollowUp: String? = null
)

data class AutomationNodeItem(
    val id: String,
    val type: String, // trigger, send_whatsapp, wait, condition, add_tag, remove_tag, update_crm, ai_reply, end
    val title: String,
    val subtitle: String
)

data class AutomationItem(
    val id: Int,
    val name: String,
    val triggerType: String,
    val isActive: Boolean,
    val totalRuns: Int,
    val nodes: List<AutomationNodeItem> = emptyList()
)

data class ChartPoint(
    val label: String,
    val value: Float
)

data class DashboardStats(
    val totalContacts: Int,
    val activeCampaigns: Int,
    val scheduledCampaigns: Int,
    val messagesSent: Int,
    val messagesDelivered: Int,
    val messagesFailed: Int,
    val replies: Int,
    val optOuts: Int,
    val leads: Int,
    val customers: Int,
    val deliveryRatePercent: Float,
    val responseRatePercent: Float,
    val messagesOverTime: List<ChartPoint>,
    val campaignPerformance: List<ChartPoint>,
    val contactGrowth: List<ChartPoint>
)

data class WhatsAppAccountItem(
    val id: Int,
    val businessName: String,
    val phoneNumber: String,
    val wabaId: String,
    val phoneNumberId: String,
    val status: String, // CONNECTED, PENDING, DISCONNECTED
    val isDefault: Boolean,
    val connectedAt: String,
    val profileName: String? = null,
    val profilePictureUrl: String? = null,
    val qualityRating: String = "GREEN", // GREEN, YELLOW, RED, UNKNOWN
    val verificationStatus: String = "VERIFIED", // VERIFIED, PENDING
    val accessToken: String? = null
)

data class MediaFileItem(
    val id: Int,
    val filename: String,
    val fileType: String, // image, video, document, package, archive
    val mimeType: String,
    val fileSizeBytes: Long,
    val publicUrl: String? = null,
    val isWhatsAppSupported: Boolean = true,
    val whatsAppUnsupportedReason: String? = null,
    val createdAt: String = "Today"
) {
    val url: String get() = publicUrl ?: ""
}

data class WalletTransactionItem(
    val id: String,
    val type: String, // "RECHARGE", "DEDUCTION"
    val amount: Double,
    val paymentMethod: String, // "UPI", "Card", "Stripe", "PayPal", "Meta Cloud Deduction"
    val description: String,
    val date: String,
    val status: String = "SUCCESS",
    val referenceId: String? = null
)

data class WalletInfo(
    val balance: Double = 50.00,
    val currency: String = "USD",
    val marketingRatePerMsg: Double = 0.05,
    val utilityRatePerMsg: Double = 0.02,
    val serviceRatePerMsg: Double = 0.01,
    val freeTierMessagesRemaining: Int = 1000
)
