import SwiftUI

@main
struct MobiCoreApp: App {

    @StateObject private var client = MobiCoreClient()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(client)
                .preferredColorScheme(.dark)
        }
    }
}

/// Tab shell. Detail, settings and the emulator are pushed on top of a tab
/// rather than being tabs of their own.
struct RootView: View {

    var body: some View {
        TabView {
            NavigationStack { HomeView() }
                .tabItem { Label("Trang chủ", systemImage: "house") }
            NavigationStack { LibraryView() }
                .tabItem { Label("Thư viện", systemImage: "gamecontroller") }
            NavigationStack { ToolsView() }
                .tabItem { Label("Công cụ", systemImage: "wrench.and.screwdriver") }
            NavigationStack { SettingsView() }
                .tabItem { Label("Cài đặt", systemImage: "gearshape") }
        }
        .tint(Palette.accent)
        .background(Palette.background)
    }
}
