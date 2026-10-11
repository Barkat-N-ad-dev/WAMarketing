import SwiftUI

@main
struct WAMarketingApp: App {
    @StateObject private var appEnvironment = AppEnvironment.shared

    var body: some Scene {
        WindowGroup {
            AppNavigation()
                .environmentObject(appEnvironment)
                .preferredColorScheme(.dark)
        }
    }
}
