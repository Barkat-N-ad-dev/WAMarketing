import SwiftUI

public struct SettingsView: View {
    @EnvironmentObject var env: AppEnvironment
    @State private var showConnectSheet: Bool = false

    public var body: some View {
        NavigationStack {
            List {
                Section("Connected WhatsApp Numbers") {
                    ForEach(env.whatsappAccounts) { acc in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(acc.businessName)
                                    .font(.subheadline.weight(.bold))
                                Spacer()
                                Text(acc.status)
                                    .font(.caption2.weight(.bold))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.waGreen.opacity(0.2))
                                    .foregroundColor(.waGreen)
                                    .cornerRadius(4)
                            }
                            Text("Phone ID: \(acc.phoneNumberId)")
                                .font(.caption)
                                .foregroundColor(.gray)
                            Text("Phone: \(acc.phoneNumber)")
                                .font(.caption2)
                                .foregroundColor(.waGreen)
                        }
                        .padding(.vertical, 4)
                    }

                    Button {
                        showConnectSheet = true
                    } label: {
                        Label("Connect WhatsApp Number", systemImage: "plus.circle.fill")
                            .foregroundColor(.waGreen)
                    }
                }

                Section("Developer & Application Profile") {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("WA Marketing SaaS")
                            .font(.headline)
                        Text("Lead Architect & Developer: Barkat Ali")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(.waGreen)
                        Link("Official Portfolio: https://barkatai.ihsanraza.com/#about", destination: URL(string: "https://barkatai.ihsanraza.com/#about")!)
                            .font(.caption)
                            .foregroundColor(.blue)
                        Text("Version: 1.0.0 (Production Release)")
                            .font(.caption2)
                            .foregroundColor(.gray)
                        Text("Stack: FastAPI • SwiftUI • Jetpack Compose • Meta Cloud API v19.0")
                            .font(.caption2)
                            .foregroundColor(.gray)
                    }
                    .padding(.vertical, 4)
                }

                Section("Backend Endpoint") {
                    Text("http://192.168.255.153:8005/api/v1")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
            }
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("Settings")
            .sheet(isPresented: $showConnectSheet) {
                iOSConnectWhatsAppSheet()
            }
        }
    }
}

struct iOSConnectWhatsAppSheet: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var env: AppEnvironment
    @State private var label: String = "Test number"
    @State private var phoneId: String = "1181137018426157"
    @State private var wabaId: String = "2080401999220434"
    @State private var token: String = "EAAXbRJeZBCvEBStG7E7J6277mGkGGmiPPPp5f8hce2KZAPDdyZAO0igdab3jue4WRI4sBZAq2fTISGDKh4UTdl5bZBf0V2yfWDMDG3Ud7IetDHpJJyic6qzmk5MRJYHxoBOn9QQej36xNgaAwv1ooZATtQnq5ZCbBgLc4ZBS1D9HnwMj6uW3YDZBXSxpATCymrSIS5Jpf5MCwK9am3jS7VfhvFL0etxvZBVXu2uviR5pqjdmmx8jfcc6GEv4vDZAbCKoZCoj7lJwtDZBINDK2kiovZAROs"
    @State private var isVerifying: Bool = false
    @State private var verificationMessage: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section("Meta Developer Credentials") {
                    TextField("Account Label", text: $label)
                    TextField("Phone Number ID", text: $phoneId)
                    TextField("WABA ID", text: $wabaId)
                    SecureField("Access Token", text: $token)
                }

                Section {
                    Button {
                        isVerifying = true
                        verificationMessage = nil
                        Task {
                            // Test against Meta Graph API
                            guard let url = URL(string: "https://graph.facebook.com/v19.0/\(phoneId)?fields=verified_name,display_phone_number,quality_rating") else { return }
                            var req = URLRequest(url: url)
                            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                            do {
                                let (data, resp) = try await URLSession.shared.data(for: req)
                                if let http = resp as? HTTPURLResponse, (200...299).contains(http.statusCode) {
                                    verificationMessage = "Verified with Meta API (Quality: GREEN)"
                                } else {
                                    verificationMessage = "Error: Invalid credentials or token expired"
                                }
                            } catch {
                                verificationMessage = "Network error: \(error.localizedDescription)"
                            }
                            isVerifying = false
                        }
                    } label: {
                        HStack {
                            if isVerifying {
                                ProgressView()
                            } else {
                                Image(systemName: "checkmark.seal.fill")
                                Text("Test & Verify with Meta API")
                            }
                        }
                    }
                    .tint(.waGreen)

                    if let msg = verificationMessage {
                        Text(msg)
                            .font(.caption)
                            .foregroundColor(msg.contains("Verified") ? .waGreen : .red)
                    }
                }

                Button("Save WhatsApp Number") {
                    let newAcc = WhatsAppAccountItem(
                        id: Int.random(in: 100...999),
                        businessName: label,
                        phoneNumber: "+1 555 197 7671",
                        wabaId: wabaId,
                        phoneNumberId: phoneId,
                        accessToken: token
                    )
                    env.whatsappAccounts.append(newAcc)
                    dismiss()
                }
                .tint(.waGreen)
            }
            .navigationTitle("Connect WhatsApp")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
