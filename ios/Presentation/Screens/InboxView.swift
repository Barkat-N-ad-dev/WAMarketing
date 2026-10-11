import SwiftUI

public struct InboxView: View {
    @EnvironmentObject var env: AppEnvironment
    @State private var selectedConversation: ConversationItem? = nil

    public var body: some View {
        NavigationStack {
            List(env.conversations) { conv in
                NavigationLink(value: conv) {
                    HStack(spacing: 12) {
                        Circle()
                            .fill(Color.waGreen.opacity(0.2))
                            .frame(width: 48, height: 48)
                            .overlay {
                                Text(String(conv.contactName.prefix(1)))
                                    .font(.headline.weight(.bold))
                                    .foregroundColor(.waGreen)
                            }

                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(conv.contactName)
                                    .font(.subheadline.weight(.bold))
                                Spacer()
                                Text(conv.lastMessageAt)
                                    .font(.caption2)
                                    .foregroundColor(.gray)
                            }

                            HStack {
                                Text(conv.lastMessage)
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                    .lineLimit(1)
                                Spacer()
                                if conv.unreadCount > 0 {
                                    Text("\(conv.unreadCount)")
                                        .font(.caption2.weight(.bold))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.waGreen)
                                        .foregroundColor(.black)
                                        .clipShape(Capsule())
                                }
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
                .listRowBackground(Color.waBgDark)
            }
            .listStyle(.plain)
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("Inbox (Two-Way)")
            .navigationDestination(for: ConversationItem.self) { conv in
                ChatDetailView(conversation: conv)
            }
        }
    }
}

struct ChatDetailView: View {
    let conversation: ConversationItem
    @EnvironmentObject var env: AppEnvironment
    @State private var replyText: String = ""

    var messages: [MessageBubbleItem] {
        env.messagesMap[conversation.id] ?? []
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(messages) { msg in
                        let isOut = msg.direction == "OUTBOUND"
                        HStack {
                            if isOut { Spacer() }
                            VStack(alignment: isOut ? .trailing : .leading, spacing: 4) {
                                Text(msg.body)
                                    .font(.subheadline)
                                    .foregroundColor(.white)
                                HStack(spacing: 4) {
                                    Text(msg.timestamp)
                                        .font(.caption2)
                                        .foregroundColor(.white.opacity(0.6))
                                    if isOut {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.system(size: 9))
                                            .foregroundColor(.waGreen)
                                    }
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(isOut ? Color.waBubbleOut : Color.waBubbleIn)
                            .cornerRadius(14)
                            if !isOut { Spacer() }
                        }
                    }
                }
                .padding()
            }

            // Composer
            HStack(spacing: 8) {
                TextField("Type WhatsApp reply...", text: $replyText)
                    .padding(10)
                    .background(Color.waCardDark)
                    .cornerRadius(20)

                Button {
                    let text = replyText.trimmingCharacters(in: .whitespaces)
                    if !text.isEmpty {
                        let contact = env.contacts.first(where: { $0.id == conversation.contactId }) ?? ContactItem(id: conversation.contactId, firstName: conversation.contactName, phoneNumber: conversation.phoneNumber)
                        env.sendMessage(contact: contact, text: text)
                        replyText = ""
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
        .navigationTitle(conversation.contactName)
        .navigationBarTitleDisplayMode(.inline)
    }
}
