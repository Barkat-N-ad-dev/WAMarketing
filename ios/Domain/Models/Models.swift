import Foundation

public struct TagItem: Identifiable, Codable, Hashable {
    public let id: Int
    public let name: String
    public let color: String

    public init(id: Int, name: String, color: String = "#25D366") {
        self.id = id
        self.name = name
        self.color = color
    }
}

public struct ContactItem: Identifiable, Codable, Hashable {
    public let id: Int
    public var firstName: String
    public var lastName: String
    public var phoneNumber: String
    public var whatsappNumber: String
    public var email: String?
    public var company: String?
    public var city: String?
    public var country: String?
    public var notes: String
    public var leadStatus: String
    public var customerStatus: String
    public var optInStatus: String
    public var tags: [TagItem]

    public var fullName: String {
        lastName.isEmpty ? firstName : "\(firstName) \(lastName)"
    }

    public init(
        id: Int,
        firstName: String,
        lastName: String = "",
        phoneNumber: String,
        whatsappNumber: String = "",
        email: String? = nil,
        company: String? = nil,
        city: String? = nil,
        country: String? = nil,
        notes: String = "",
        leadStatus: String = "New Lead",
        customerStatus: String = "Lead",
        optInStatus: String = "opted_in",
        tags: [TagItem] = []
    ) {
        self.id = id
        self.firstName = firstName
        self.lastName = lastName
        self.phoneNumber = phoneNumber
        self.whatsappNumber = whatsappNumber.isEmpty ? phoneNumber : whatsappNumber
        self.email = email
        self.company = company
        self.city = city
        self.country = country
        self.notes = notes
        self.leadStatus = leadStatus
        self.customerStatus = customerStatus
        self.optInStatus = optInStatus
        self.tags = tags
    }
}

public struct CampaignItem: Identifiable, Codable, Hashable {
    public let id: Int
    public var name: String
    public var status: String // DRAFT, SCHEDULED, RUNNING, COMPLETED, PAUSED
    public var messageContent: String
    public var mediaUrl: String?
    public var scheduledFor: String?
    public var totalRecipients: Int
    public var sentCount: Int
    public var deliveredCount: Int
    public var readCount: Int
    public var failedCount: Int
    public var repliedCount: Int
    public var optOutCount: Int
    public var createdAt: String

    public init(
        id: Int,
        name: String,
        status: String = "DRAFT",
        messageContent: String,
        mediaUrl: String? = nil,
        scheduledFor: String? = nil,
        totalRecipients: Int = 0,
        sentCount: Int = 0,
        deliveredCount: Int = 0,
        readCount: Int = 0,
        failedCount: Int = 0,
        repliedCount: Int = 0,
        optOutCount: Int = 0,
        createdAt: String = "Today"
    ) {
        self.id = id
        self.name = name
        self.status = status
        self.messageContent = messageContent
        self.mediaUrl = mediaUrl
        self.scheduledFor = scheduledFor
        self.totalRecipients = totalRecipients
        self.sentCount = sentCount
        self.deliveredCount = deliveredCount
        self.readCount = readCount
        self.failedCount = failedCount
        self.repliedCount = repliedCount
        self.optOutCount = optOutCount
        self.createdAt = createdAt
    }
}

public struct TemplateItem: Identifiable, Codable, Hashable {
    public let id: Int
    public var name: String
    public var category: String
    public var language: String
    public var content: String
    public var templateType: String
    public var status: String
    public var variables: [String]

    public init(
        id: Int,
        name: String,
        category: String = "Marketing",
        language: String = "en",
        content: String,
        templateType: String = "WhatsApp Approved Template",
        status: String = "APPROVED",
        variables: [String] = []
    ) {
        self.id = id
        self.name = name
        self.category = category
        self.language = language
        self.content = content
        self.templateType = templateType
        self.status = status
        self.variables = variables
    }
}

public struct MessageBubbleItem: Identifiable, Codable, Hashable {
    public let id: Int
    public let conversationId: Int
    public let direction: String // INBOUND or OUTBOUND
    public let messageType: String
    public let body: String
    public let mediaUrl: String?
    public let status: String
    public let timestamp: String

    public init(
        id: Int,
        conversationId: Int,
        direction: String,
        messageType: String = "text",
        body: String,
        mediaUrl: String? = nil,
        status: String = "DELIVERED",
        timestamp: String = "Just now"
    ) {
        self.id = id
        self.conversationId = conversationId
        self.direction = direction
        self.messageType = messageType
        self.body = body
        self.mediaUrl = mediaUrl
        self.status = status
        self.timestamp = timestamp
    }
}

public struct ConversationItem: Identifiable, Codable, Hashable {
    public let id: Int
    public let contactId: Int
    public var contactName: String
    public var phoneNumber: String
    public var status: String
    public var unreadCount: Int
    public var lastMessage: String
    public var lastMessageAt: String
    public var internalNotes: String?

    public init(
        id: Int,
        contactId: Int,
        contactName: String,
        phoneNumber: String,
        status: String = "OPEN",
        unreadCount: Int = 0,
        lastMessage: String = "",
        lastMessageAt: String = "Just now",
        internalNotes: String? = nil
    ) {
        self.id = id
        self.contactId = contactId
        self.contactName = contactName
        self.phoneNumber = phoneNumber
        self.status = status
        self.unreadCount = unreadCount
        self.lastMessage = lastMessage
        self.lastMessageAt = lastMessageAt
        self.internalNotes = internalNotes
    }
}

public struct WhatsAppAccountItem: Identifiable, Codable, Hashable {
    public let id: Int
    public var businessName: String
    public var phoneNumber: String
    public var wabaId: String
    public var phoneNumberId: String
    public var status: String
    public var isDefault: Bool
    public var connectedAt: String
    public var profileName: String?
    public var qualityRating: String
    public var verificationStatus: String
    public var accessToken: String?

    public init(
        id: Int,
        businessName: String,
        phoneNumber: String,
        wabaId: String,
        phoneNumberId: String,
        status: String = "CONNECTED",
        isDefault: Bool = false,
        connectedAt: String = "Today",
        profileName: String? = nil,
        qualityRating: String = "GREEN",
        verificationStatus: String = "VERIFIED",
        accessToken: String? = nil
    ) {
        self.id = id
        self.businessName = businessName
        self.phoneNumber = phoneNumber
        self.wabaId = wabaId
        self.phoneNumberId = phoneNumberId
        self.status = status
        self.isDefault = isDefault
        self.connectedAt = connectedAt
        self.profileName = profileName ?? businessName
        self.qualityRating = qualityRating
        self.verificationStatus = verificationStatus
        self.accessToken = accessToken
    }
}

public struct MediaFileItem: Identifiable, Codable, Hashable {
    public let id: Int
    public var filename: String
    public var fileType: String // image, video, document, package
    public var mimeType: String
    public var sizeBytes: Int64
    public var url: String
    public var isWhatsAppSupported: Bool
    public var distributionNote: String?
    public var uploadedAt: String

    public init(
        id: Int,
        filename: String,
        fileType: String,
        mimeType: String,
        sizeBytes: Int64,
        url: String,
        isWhatsAppSupported: Bool = true,
        distributionNote: String? = nil,
        uploadedAt: String = "Today"
    ) {
        self.id = id
        self.filename = filename
        self.fileType = fileType
        self.mimeType = mimeType
        self.sizeBytes = sizeBytes
        self.url = url
        self.isWhatsAppSupported = isWhatsAppSupported
        self.distributionNote = distributionNote
        self.uploadedAt = uploadedAt
    }
}

public struct DashboardStats: Codable {
    public var totalContacts: Int
    public var activeCampaigns: Int
    public var messagesSent: Int
    public var messagesDelivered: Int
    public var replies: Int
    public var deliveryRate: Double
    public var responseRate: Double

    public init(
        totalContacts: Int = 120,
        activeCampaigns: Int = 3,
        messagesSent: Int = 890,
        messagesDelivered: Int = 865,
        replies: Int = 142,
        deliveryRate: Double = 97.2,
        responseRate: Double = 16.4
    ) {
        self.totalContacts = totalContacts
        self.activeCampaigns = activeCampaigns
        self.messagesSent = messagesSent
        self.messagesDelivered = messagesDelivered
        self.replies = replies
        self.deliveryRate = deliveryRate
        self.responseRate = responseRate
    }
}
