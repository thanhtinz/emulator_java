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
                .tabItem { Label("Home", systemImage: "house") }
            NavigationStack { LibraryView() }
                .tabItem { Label("Library", systemImage: "gamecontroller") }
            NavigationStack { ToolsView() }
                .tabItem { Label("Tools", systemImage: "wrench.and.screwdriver") }
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
        .tint(Palette.accent)
        .background(Palette.background)
    }
}
