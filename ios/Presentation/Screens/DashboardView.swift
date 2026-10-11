import SwiftUI

public struct DashboardView: View {
    @EnvironmentObject var env: AppEnvironment

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    // Header Card
                    VStack(alignment: .leading, spacing: 6) {
                        Text("WA Marketing SaaS")
                            .font(.title2.weight(.bold))
                        Text("Enterprise WhatsApp Automation & CRM • Barkat Ali")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                    .padding(.horizontal)

                    // Stats Grid
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        StatCard(title: "Total Contacts", value: "\(env.stats.totalContacts)", icon: "person.2.fill", color: .waGreen)
                        StatCard(title: "Active Campaigns", value: "\(env.stats.activeCampaigns)", icon: "paperplane.fill", color: .blue)
                        StatCard(title: "Messages Sent", value: "\(env.stats.messagesSent)", icon: "checkmark.circle.fill", color: .purple)
                        StatCard(title: "Replies Received", value: "\(env.stats.replies)", icon: "bubble.left.fill", color: .orange)
                    }
                    .padding(.horizontal)

                    // Quick Actions
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Quick Actions")
                            .font(.headline)
                            .padding(.horizontal)

                        HStack(spacing: 12) {
                            Button {
                                env.selectedTab = 1
                            } label: {
                                Label("New Campaign", systemImage: "plus.circle.fill")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.waGreen)
                                    .foregroundColor(.black)
                                    .cornerRadius(10)
                            }

                            Button {
                                env.selectedTab = 2
                            } label: {
                                Label("Import Leads", systemImage: "arrow.down.doc.fill")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.waCardDark)
                                    .foregroundColor(.white)
                                    .cornerRadius(10)
                            }
                        }
                        .padding(.horizontal)
                    }

                    // Recent Campaigns
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Recent Campaigns")
                            .font(.headline)
                            .padding(.horizontal)

                        ForEach(env.campaigns) { camp in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(camp.name)
                                        .font(.subheadline.weight(.medium))
                                    Text("\(camp.sentCount)/\(camp.totalRecipients) delivered • \(camp.repliedCount) replies")
                                        .font(.caption2)
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                Text(camp.status)
                                    .font(.caption2.weight(.bold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(camp.status == "RUNNING" ? Color.waGreen.opacity(0.2) : Color.blue.opacity(0.2))
                                    .foregroundColor(camp.status == "RUNNING" ? .waGreen : .blue)
                                    .cornerRadius(6)
                            }
                            .padding()
                            .background(Color.waCardDark)
                            .cornerRadius(12)
                            .padding(.horizontal)
                        }
                    }
                }
                .padding(.vertical)
            }
            .background(Color.waBgDark.ignoresSafeArea())
            .navigationTitle("Dashboard")
        }
    }
}

struct StatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                Spacer()
            }
            Text(value)
                .font(.title2.weight(.bold))
            Text(title)
                .font(.caption)
                .foregroundColor(.gray)
        }
        .padding()
        .background(Color.waCardDark)
        .cornerRadius(12)
    }
}
