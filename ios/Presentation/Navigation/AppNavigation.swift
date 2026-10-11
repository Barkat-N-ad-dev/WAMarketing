import SwiftUI

public struct AppNavigation: View {
    @EnvironmentObject var env: AppEnvironment

    public var body: some View {
        TabView(selection: $env.selectedTab) {
            DashboardView()
                .tabItem {
                    Label("Dashboard", systemImage: "chart.bar.fill")
                }
                .tag(0)

            CampaignsListView()
                .tabItem {
                    Label("Campaigns", systemImage: "paperplane.fill")
                }
                .tag(1)

            ContactsView()
                .tabItem {
                    Label("Contacts", systemImage: "person.2.fill")
                }
                .tag(2)

            InboxView()
                .tabItem {
                    Label("Inbox", systemImage: "bubble.left.and.bubble.right.fill")
                }
                .tag(3)

            MediaLibraryView()
                .tabItem {
                    Label("Media", systemImage: "photo.on.rectangle.angled")
                }
                .tag(4)

            AiAssistantView()
                .tabItem {
                    Label("AI Assistant", systemImage: "sparkles")
                }
                .tag(5)

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gearshape.fill")
                }
                .tag(6)
        }
        .tint(.waGreen)
        .overlay(alignment: .bottom) {
            if let toast = env.notificationToast {
                Text(toast)
                    .font(.footnote.weight(.semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.black.opacity(0.85))
                    .clipShape(Capsule())
                    .padding(.bottom, 60)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }
}
