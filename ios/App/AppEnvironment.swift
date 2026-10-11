import Foundation
import SwiftUI
import Combine

@MainActor
public class AppEnvironment: ObservableObject {
    public static let shared = AppEnvironment()

    @Published public var contacts: [ContactItem] = []
    @Published public var campaigns: [CampaignItem] = []
    @Published public var templates: [TemplateItem] = []
    @Published public var conversations: [ConversationItem] = []
    @Published public var messagesMap: [Int: [MessageBubbleItem]] = [:]
    @Published public var whatsappAccounts: [WhatsAppAccountItem] = []
    @Published public var mediaFiles: [MediaFileItem] = []
    @Published public var stats: DashboardStats = DashboardStats()

    @Published public var selectedTab: Int = 0 // 0: Dashboard, 1: Campaigns, 2: Contacts, 3: Inbox, 4: Media, 5: AI, 6: Settings
    @Published public var notificationToast: String? = nil

    private init() {
        loadSeedData()
    }

    private func loadSeedData() {
        self.contacts = [
            ContactItem(id: 1, firstName: "Hamza", lastName: "Khan", phoneNumber: "+971501234567", email: "hamza@apex.ae", company: "Apex Dynamics", city: "Dubai", country: "UAE", notes: "Interested in 3-bed penthouses", tags: [TagItem(id: 1, name: "VIP", color: "#8B5CF6")]),
            ContactItem(id: 2, firstName: "Fatima", lastName: "Zahra", phoneNumber: "+971559876543", email: "fatima@emirates.ae", company: "Emirates Trading", city: "Abu Dhabi", country: "UAE", notes: "Requested pricing brochure", tags: [TagItem(id: 2, name: "New Lead", color: "#3B82F6")]),
            ContactItem(id: 3, firstName: "Tariq", lastName: "Mahmood", phoneNumber: "+923001234567", email: "tariq@paktrade.pk", company: "Pak Trade Co", city: "Lahore", country: "Pakistan", notes: "Met at exhibition", tags: [TagItem(id: 3, name: "Pakistan", color: "#14B8A6")])
        ]

        self.campaigns = [
            CampaignItem(id: 1, name: "Dubai Luxury Waterfront Launch", status: "RUNNING", messageContent: "Hello {{first_name}}, exclusive launch alert for {{company}} in {{city}}! Reply YES for details.", totalRecipients: 142, sentCount: 110, deliveredCount: 108, repliedCount: 28),
            CampaignItem(id: 2, name: "Pakistani Investors Briefing Q4", status: "SCHEDULED", messageContent: "Hi {{first_name}}, join our private wealth webinar this Thursday.", scheduledFor: "Tomorrow, 10:00 AM", totalRecipients: 85),
            CampaignItem(id: 3, name: "Saudi Tech Summit Follow-up", status: "COMPLETED", messageContent: "Thank you {{first_name}} for visiting our booth in Riyadh!", totalRecipients: 320, sentCount: 320, deliveredCount: 318, repliedCount: 64)
        ]

        self.conversations = [
            ConversationItem(id: 1, contactId: 1, contactName: "Hamza Khan", phoneNumber: "+971 50 123 4567", unreadCount: 2, lastMessage: "Please send the PDF brochure over here first. Thanks!", lastMessageAt: "10:22 AM"),
            ConversationItem(id: 2, contactId: 2, contactName: "Fatima Zahra", phoneNumber: "+971 55 987 6543", unreadCount: 0, lastMessage: "Sounds great, will review shortly.", lastMessageAt: "9:45 AM")
        ]

        self.messagesMap = [
            1: [
                MessageBubbleItem(id: 101, conversationId: 1, direction: "OUTBOUND", body: "Hello Hamza, thank you for connecting with us regarding the waterfront residences.", status: "READ", timestamp: "10:15 AM"),
                MessageBubbleItem(id: 102, conversationId: 1, direction: "INBOUND", body: "Please send the PDF brochure over here first. Thanks!", status: "DELIVERED", timestamp: "10:22 AM")
            ]
        ]

        self.whatsappAccounts = [
            WhatsAppAccountItem(
                id: 1,
                businessName: "Test number",
                phoneNumber: "+1 555 197 7671",
                wabaId: "2080401999220434",
                phoneNumberId: "1181137018426157",
                status: "CONNECTED",
                isDefault: true
            )
        ]

        self.mediaFiles = [
            MediaFileItem(id: 1, filename: "Dubai_Penthouse_Brochure.pdf", fileType: "document", mimeType: "application/pdf", sizeBytes: 2450000, url: "https://api.wamarketing.app/uploads/brochure.pdf"),
            MediaFileItem(id: 2, filename: "Luxury_Waterfront_Villa.jpg", fileType: "image", mimeType: "image/jpeg", sizeBytes: 1850000, url: "https://images.unsplash.com/photo-1613490493576-7fde63acd811?w=800"),
            MediaFileItem(id: 3, filename: "WAMarketing_Android_App.apk", fileType: "package", mimeType: "application/vnd.android.package-archive", sizeBytes: 12880000, url: "https://api.wamarketing.app/uploads/wamarketing-debug.apk", isWhatsAppSupported: false, distributionNote: "APK files are shared via direct 1-click cloud download link")
        ]
    }

    public func addContact(_ contact: ContactItem) {
        self.contacts.insert(contact, at: 0)
        self.stats.totalContacts += 1
        showToast("Contact added: \(contact.fullName)")
    }

    public func addCampaign(_ campaign: CampaignItem) {
        self.campaigns.insert(campaign, at: 0)
        self.stats.activeCampaigns += 1
        showToast("Campaign '\(campaign.name)' launched")
    }

    public func addMediaFile(_ file: MediaFileItem) {
        self.mediaFiles.insert(file, at: 0)
        showToast("Uploaded '\(file.filename)' to Media Library")
    }

    public func sendMessage(contact: ContactItem, text: String) {
        let convId = conversations.first(where: { $0.contactId == contact.id })?.id ?? Int.random(in: 1000...9999)
        let newBubble = MessageBubbleItem(id: Int.random(in: 1000...9999), conversationId: convId, direction: "OUTBOUND", body: text)

        var list = messagesMap[convId] ?? []
        list.append(newBubble)
        messagesMap[convId] = list

        if let idx = conversations.firstIndex(where: { $0.id == convId }) {
            conversations[idx].lastMessage = text
            conversations[idx].lastMessageAt = "Just now"
        } else {
            conversations.insert(ConversationItem(id: convId, contactId: contact.id, contactName: contact.fullName, phoneNumber: contact.phoneNumber, lastMessage: text), at: 0)
        }
        self.stats.messagesSent += 1
        showToast("Message sent to \(contact.fullName)")
    }

    public func showToast(_ msg: String) {
        self.notificationToast = msg
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            if self.notificationToast == msg {
                self.notificationToast = nil
            }
        }
    }
}
