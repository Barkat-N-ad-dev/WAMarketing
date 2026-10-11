import SwiftUI

public struct AiAssistantView: View {
    @State private var messages: [ChatMessage] = [
        ChatMessage(role: "assistant", content: "Welcome to WA Marketing AI Assistant!\n\nI can help you with WhatsApp Cloud API setup, importing contacts, campaign scheduling, and media sharing.\n\nDeveloper: Barkat Ali (https://barkatai.ihsanraza.com/#about)")
    ]
    @State private var inputPrompt: String = ""
    @State private var isThinking: Bool = false

    struct ChatMessage: Identifiable {
        let id = UUID()
        let role: String
        let content: String
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(messages) { msg in
                            let isUser = msg.role == "user"
                            HStack {
                                if isUser { Spacer() }
                                VStack(alignment: isUser ? .trailing : .leading, spacing: 4) {
                                    Text(isUser ? "You" : "WA Marketing AI")
                                        .font(.caption2.weight(.bold))
                                        .foregroundColor(isUser ? .black : .waGreen)
                                    Text(msg.content)
                                        .font(.subheadline)
                                        .foregroundColor(isUser ? .black : .white)
                                }
                                .padding(12)
                                .background(isUser ? Color.waGreen : Color.waCardDark)
                                .cornerRadius(14)
                                if !isUser { Spacer() }
                            }
                        }

                        if isThinking {
                            HStack {
                                ProgressView()
                                Text("AI Assistant is thinking...")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                Spacer()
                            }
                            .padding(.horizontal)
                        }
                    }
                    .padding()
                }

                // Input box
                HStack(spacing: 8) {
                    TextField("Ask anything about WA Marketing...", text: $inputPrompt)
                        .padding(10)
                        .background(Color.waCardDark)
                        .cornerRadius(20)

                    Button {
                        let q = inputPrompt.trimmingCharacters(in: .whitespaces)
                        if !q.isEmpty {
                            messages.append(ChatMessage(role: "user", content: q))
                            inputPrompt = ""
                            isThinking = true
                            Task {
                                try? await Task.sleep(nanoseconds: 600_000_000)
                                let reply: String
                                let lower = q.lowercased()
                                if lower.contains("barkat") || lower.contains("developer") {
                                    reply = "WA Marketing was developed by **Barkat Ali**, senior full-stack mobile & cloud engineer.\nPortfolio: https://barkatai.ihsanraza.com/#about"
                                } else if lower.contains("excel") || lower.contains("pdf") || lower.contains("import") {
                                    reply = "To import leads: Go to Contacts -> Import. Both CSV, Excel (.xlsx/.xls) and PDF files are parsed and validated automatically."
                                } else if lower.contains("whatsapp") || lower.contains("token") {
                                    reply = "To connect WhatsApp: Navigate to Settings -> Connect WhatsApp. Enter Phone Number ID & Token, then tap 'Test & Verify with Meta API'."
                                } else {
                                    reply = "WA Marketing provides complete two-way WhatsApp communication, CRM pipelines, campaign scheduling, and secure APK distribution."
                                }
                                isThinking = false
                                messages.append(ChatMessage(role: "assistant", content: reply))
                            }
                        }
                    } label: {
                        Image(systemName: "paperplane.fill")
                            .foregroundColor(.black)
                            .padding(10)
                            .background(Color.waGreen)
                            .clipShape(Circle())
                    }
                }
                .padding()
                .background(Color.waBgDark)
            }
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("AI Assistant")
        }
    }
}
