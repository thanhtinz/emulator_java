import SwiftUI

@main
struct MobiCoreApp: App {

    @StateObject private var client = MobiCoreClient()

    private var colourScheme: ColorScheme? {
        switch client.theme {
        case ThemeChoice.dark: return .dark
        case ThemeChoice.system: return nil
        default: return .light
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(client)
                // Whatever the user chose last time, from the moment the app
                // opens; "theo hệ thống" leaves the decision to the phone.
                .preferredColorScheme(colourScheme)
                .task { client.loadTheme() }
        }
    }
}

/// Tab shell. Detail, settings and the emulator are pushed on top of a tab
/// rather than being tabs of their own.
///
/// Three tabs, not four: the library tab held a search box over the games the
/// home screen already listed, so it moved there. A tab that duplicates its
/// neighbour makes people look in two places for one thing.
struct RootView: View {

    var body: some View {
        TabView {
            NavigationStack { HomeView() }
                .tabItem { Label("Trang chủ", systemImage: "house") }
            NavigationStack { ToolsView() }
                .tabItem { Label("Công cụ", systemImage: "wrench.and.screwdriver") }
            NavigationStack { SettingsView() }
                .tabItem { Label("Cài đặt", systemImage: "gearshape") }
        }
        .tint(Palette.accent)
        .background(Palette.background)
    }
}
