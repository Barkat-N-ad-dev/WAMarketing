# WAMarketing — Cross-Platform iOS Architecture Specification

## 1. Executive Overview

This specification defines the architectural blueprint for the official iOS client of **WAMarketing**. Built to pair seamlessly with the existing Python/FastAPI backend and PostgreSQL database, the iOS application shares 100% of the backend REST endpoints, WebSocket event contracts, and official Meta WhatsApp Cloud API integrations.

```
┌────────────────────────────────────────────────────────┐
│               WAMarketing iOS Client                   │
│   (SwiftUI • Swift 6 • Clean Architecture • MVVM)      │
└──────────────────────────┬─────────────────────────────┘
                           │ HTTPS (TLS 1.3) + WSS
                           ▼
┌────────────────────────────────────────────────────────┐
│             FastAPI Backend (Port 8000)                │
│    JWT Auth • Rate Limiting • Media • Webhooks         │
└──────────────┬───────────────────────────┬─────────────┘
               │                           │
               ▼                           ▼
┌──────────────────────────┐    ┌────────────────────────┐
│     PostgreSQL DB        │    │ Meta WhatsApp Cloud API│
│  (Supabase / Cloud SQL)  │    │      (v19.0 / v21.0)   │
└──────────────────────────┘    └────────────────────────┘
```

---

## 2. Technology Stack & Key Frameworks

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Language** | Swift 6.0 | Strict concurrency checking, modern language safety |
| **UI Framework** | SwiftUI (iOS 17+) | Declarative state-driven UI matching Material 3 ergonomics |
| **Architecture** | MVVM + Clean Architecture | Clear separation of UI, Domain Use Cases, and Data Repositories |
| **Networking** | `URLSession` + `async/await` | Native modern networking, zero third-party dependencies |
| **WebSockets** | `URLSessionWebSocketTask` | Real-time two-way inbox message streaming |
| **Local Persistence** | `SwiftData` / `Core Data` | Offline caching for contacts, templates, and chats |
| **Sensitive Storage** | `Keychain Services` | Hardware-backed secure storage for JWT access & refresh tokens |
| **Image Loading** | `AsyncImage` / `Nuke` | Smooth image caching for media previews and profile photos |
| **Push Notifications** | Apple Push Notifications (APNs) | Background wakeup for incoming customer WhatsApp replies |

---

## 3. Layered Directory & Module Structure

```
ios/
├── App/
│   ├── WAMarketingApp.swift           # Application entrypoint
│   └── AppEnvironment.swift           # Dependency injection container
├── Domain/
│   ├── Models/                        # Domain entities
│   │   ├── Contact.swift
│   │   ├── Campaign.swift
│   │   ├── MessageTemplate.swift
│   │   ├── Conversation.swift
│   │   ├── WhatsAppAccount.swift
│   │   └── MediaFile.swift
│   └── UseCases/                      # Business logic operations
│       ├── SendMessageUseCase.swift
│       ├── ImportContactsUseCase.swift
│       └── SyncWhatsAppProfileUseCase.swift
├── Data/
│   ├── Network/
│   │   ├── APIClient.swift            # HTTP & WebSocket networking client
│   │   ├── Endpoints.swift            # Typed endpoint declarations
│   │   └── DTOs/                      # Data Transfer Objects matching Pydantic
│   ├── Repositories/
│   │   ├── ContactsRepository.swift
│   │   ├── InboxRepository.swift
│   │   └── SettingsRepository.swift
│   └── Storage/
│       ├── KeychainManager.swift      # Secure JWT token persistence
│       └── LocalDatabase.swift        # SwiftData offline schema
└── Presentation/
    ├── DesignSystem/                  # WhatsApp colors, typography, badges
    ├── Navigation/
    │   └── AppNavigationRail.swift    # NavigationSplitView for iPad/iPhone
    └── Screens/
        ├── Welcome/
        ├── Dashboard/
        ├── Contacts/
        ├── Campaigns/
        ├── Inbox/
        └── Settings/
```

---

## 4. API Client & Shared Data Contracts

### 4.1 Authentication & Token Interceptor
Sensitive JWT tokens are stored exclusively in iOS Keychain:

```swift
import Foundation
import Security

actor KeychainManager {
    static let shared = KeychainManager()
    private let tokenKey = "com.wamarketing.app.jwt_token"

    func saveToken(_ token: String) throws {
        let data = Data(token.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: tokenKey,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        SecItemDelete(query as CFDictionary)
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError.saveFailed(status) }
    }

    func getToken() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: tokenKey,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
```

### 4.2 Data Models Matching Backend Pydantic Schemas

```swift
import Foundation

struct WhatsAppAccountDTO: Codable, Identifiable {
    let id: Int
    let businessId: Int
    let businessName: String
    let phoneNumber: String
    let wabaId: String
    let phoneNumberId: String
    let status: String
    let isDefault: Bool
    let profileName: String?
    let profilePictureUrl: String?
    let qualityRating: String
    let codeVerificationStatus: String
    let about: String?
    let vertical: String?
    let websites: String?
    let connectedAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case businessId = "business_id"
        case businessName = "business_name"
        case phoneNumber = "phone_number"
        case wabaId = "waba_id"
        case phoneNumberId = "phone_number_id"
        case status
        case isDefault = "is_default"
        case profileName = "profile_name"
        case profilePictureUrl = "profile_picture_url"
        case qualityRating = "quality_rating"
        case codeVerificationStatus = "code_verification_status"
        case about, vertical, websites
        case connectedAt = "connected_at"
    }
}
```

---

## 5. Official Meta Cloud API Compliance on iOS

1. **24-Hour Customer Window Enforcement**:
   - The UI disables freeform input and renders an approved template picker when communicating with contacts outside the 24-hour service window.
2. **Opt-Out Handling**:
   - If contact `opt_in_status == "opted_out"`, outbound actions are locked with visual warnings.
3. **Strict Media Type Restrictions**:
   - Media uploaded through the iOS photo picker (`PhotosPicker`) or document picker are validated for size (max 100MB) and format.
   - Files with unsupported MIME types or `.apk` files display clear badges: "Media Library Only — Not Supported for WhatsApp Outbound Broadcast".

---

## 6. Real-Time WebSocket Streaming

The iOS app connects to `wss://api.wamarketing.app/ws/inbox`:

```swift
import Foundation

final class WebSocketInboxClient: ObservableObject {
    private var webSocketTask: URLSessionWebSocketTask?
    @Published var latestEvent: String?

    func connect(token: String) {
        guard let url = URL(string: "wss://api.wamarketing.app/ws/inbox") else { return }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let session = URLSession(configuration: .default)
        webSocketTask = session.webSocketTask(with: request)
        webSocketTask?.resume()
        receiveMessage()
    }

    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            switch result {
            case .success(let message):
                if case .string(let text) = message {
                    DispatchQueue.main.async { self?.latestEvent = text }
                }
                self?.receiveMessage()
            case .failure(let error):
                print("WebSocket error: \(error.localizedDescription)")
            }
        }
    }

    func disconnect() {
        webSocketTask?.cancel(with: .goingAway, reason: nil)
    }
}
```

---

## 7. Delivery & Packaging Plan

1. **Universal App (iPhone & iPad)**:
   - Uses `NavigationSplitView` for iPad side-by-side Dashboard and Inbox.
   - Dynamic Type and VoiceOver accessibility auditing.
2. **App Store Review Security Compliance**:
   - Zero hardcoded WhatsApp Business API secrets or tokens.
   - Zero use of private APIs or unofficial WhatsApp automation hooks.
   - Official WhatsApp Meta Business Terms and privacy policy disclaimers.
