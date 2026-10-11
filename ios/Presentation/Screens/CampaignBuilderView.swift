import SwiftUI

public struct CampaignsListView: View {
    @EnvironmentObject var env: AppEnvironment
    @State private var showBuilder: Bool = false

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ForEach(env.campaigns) { camp in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(camp.name)
                                    .font(.headline)
                                Spacer()
                                Text(camp.status)
                                    .font(.caption.weight(.bold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(camp.status == "RUNNING" ? Color.waGreen.opacity(0.2) : Color.blue.opacity(0.2))
                                    .foregroundColor(camp.status == "RUNNING" ? .waGreen : .blue)
                                    .cornerRadius(6)
                            }

                            Text(camp.messageContent)
                                .font(.subheadline)
                                .foregroundColor(.gray)
                                .lineLimit(2)

                            Divider()

                            HStack {
                                Label("\(camp.totalRecipients) Total", systemImage: "person.2")
                                Spacer()
                                Label("\(camp.deliveredCount) Delivered", systemImage: "checkmark.circle")
                                Spacer()
                                Label("\(camp.repliedCount) Replies", systemImage: "bubble.left")
                            }
                            .font(.caption2)
                            .foregroundColor(.gray)
                        }
                        .padding()
                        .background(Color.waCardDark)
                        .cornerRadius(12)
                        .padding(.horizontal)
                    }
                }
                .padding(.vertical)
            }
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("Campaigns")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showBuilder = true
                    } label: {
                        Label("New Campaign", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $showBuilder) {
                iOSCampaignBuilderSheet()
            }
        }
    }
}

struct iOSCampaignBuilderSheet: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var env: AppEnvironment

    @State private var step: Int = 1
    @State private var name: String = "Q4 VIP Outreach"
    @State private var messageText: String = "Hello {{first_name}}, exclusive announcement for {{company}} in {{city}}! Get our app here: {{download_link}}"
    @State private var selectedAudience: String = "All Contacts"
    @State private var attachMedia: Bool = true
    @State private var selectedMediaName: String = "Dubai_Penthouse_Brochure.pdf"

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                // Step Indicator
                HStack {
                    ForEach(1...5, id: \.self) { s in
                        Circle()
                            .fill(s <= step ? Color.waGreen : Color.gray.opacity(0.3))
                            .frame(width: 28, height: 28)
                            .overlay {
                                Text("\(s)")
                                    .font(.caption2.weight(.bold))
                                    .foregroundColor(s <= step ? .black : .white)
                            }
                        if s < 5 {
                            Rectangle()
                                .fill(s < step ? Color.waGreen : Color.gray.opacity(0.3))
                                .frame(height: 2)
                        }
                    }
                }
                .padding(.horizontal)

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        switch step {
                        case 1:
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Step 1: Campaign Name")
                                    .font(.headline)
                                TextField("Enter campaign title", text: $name)
                                    .padding()
                                    .background(Color.waCardDark)
                                    .cornerRadius(10)
                            }
                        case 2:
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Step 2: Select Audience")
                                    .font(.headline)
                                Picker("Audience", selection: $selectedAudience) {
                                    Text("All Contacts (\(env.contacts.count))").tag("All Contacts")
                                    Text("VIP Leads").tag("VIP Leads")
                                    Text("Dubai Segment").tag("Dubai Segment")
                                }
                                .pickerStyle(.segmented)
                            }
                        case 3:
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Step 3: Message Copy & Variables")
                                    .font(.headline)
                                TextEditor(text: $messageText)
                                    .padding(8)
                                    .background(Color.waCardDark)
                                    .cornerRadius(10)
                                    .frame(height: 120)

                                Text("Available Variables: {{first_name}}, {{last_name}}, {{company}}, {{city}}, {{download_link}}")
                                    .font(.caption2)
                                    .foregroundColor(.gray)
                            }
                        case 4:
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Step 4: Attach Media or APK")
                                    .font(.headline)
                                Toggle("Attach Media Asset", isOn: $attachMedia)
                                if attachMedia {
                                    Picker("Media File", selection: $selectedMediaName) {
                                        ForEach(env.mediaFiles) { file in
                                            Text(file.filename).tag(file.filename)
                                        }
                                    }
                                    .pickerStyle(.inline)
                                }
                            }
                        case 5:
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Step 5: Live Clean Preview")
                                    .font(.headline)
                                let sample = env.contacts.first
                                let rendered = messageText
                                    .replacingOccurrences(of: "{{first_name}}", with: sample?.firstName ?? "Hamza")
                                    .replacingOccurrences(of: "{{company}}", with: sample?.company ?? "Apex")
                                    .replacingOccurrences(of: "{{city}}", with: sample?.city ?? "Dubai")
                                    .replacingOccurrences(of: "{{download_link}}", with: "https://api.wamarketing.app/uploads/wamarketing-debug.apk")

                                VStack(alignment: .leading, spacing: 6) {
                                    Text("Recipient: \(sample?.fullName ?? "Hamza Khan")")
                                        .font(.caption.weight(.bold))
                                        .foregroundColor(.waGreen)
                                    Text(rendered)
                                        .font(.subheadline)
                                }
                                .padding()
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.waBubbleOut)
                                .cornerRadius(12)
                            }
                        default:
                            EmptyView()
                        }
                    }
                    .padding()
                }

                HStack {
                    if step > 1 {
                        Button("Back") {
                            step -= 1
                        }
                        .foregroundColor(.gray)
                    }
                    Spacer()
                    if step < 5 {
                        Button("Continue") {
                            step += 1
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(Color.waGreen)
                        .foregroundColor(.black)
                        .cornerRadius(8)
                    } else {
                        Button("Launch Campaign") {
                            let newCamp = CampaignItem(
                                id: Int.random(in: 100...999),
                                name: name,
                                status: "RUNNING",
                                messageContent: messageText,
                                totalRecipients: env.contacts.count,
                                sentCount: 1,
                                deliveredCount: 1
                            )
                            env.addCampaign(newCamp)
                            dismiss()
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(Color.waGreen)
                        .foregroundColor(.black)
                        .cornerRadius(8)
                    }
                }
                .padding()
            }
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("Campaign Wizard")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
