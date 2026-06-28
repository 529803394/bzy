import SwiftUI

@main
struct WhiteNoiseApp: App {
    @StateObject private var soundStore = SoundStore.shared
    @StateObject private var taskStore = TaskStore.shared
    @StateObject private var messageStore = MessageStore.shared
    @StateObject private var audioPlayer = AudioPlayer.shared
    
    @AppStorage("themeMode") private var themeMode: String = "system"
    
    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(soundStore)
                .environmentObject(taskStore)
                .environmentObject(messageStore)
                .environmentObject(audioPlayer)
                .tint(Color(hex: "22d3ee"))
                .preferredColorScheme(colorScheme)
        }
    }
    
    private var colorScheme: ColorScheme? {
        switch themeMode {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
}
