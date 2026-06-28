import SwiftUI

struct ThemeColors {
    static let darkGradients: [[Color]] = [
        [Color(hex: "#22d3ee"), Color(hex: "#0f172a")],
        [Color(hex: "#38bdf8"), Color(hex: "#1e40af")],
        [Color(hex: "#4ade80"), Color(hex: "#065f46")],
        [Color(hex: "#94a3b8"), Color(hex: "#1e293b")],
        [Color(hex: "#fb923c"), Color(hex: "#7c2d12")]
    ]
    
    static let lightGradients: [[Color]] = [
        [Color(hex: "#bae6fd"), Color(hex: "#38bdf8"), Color(hex: "#0284c7")],
        [Color(hex: "#fef3c7"), Color(hex: "#7dd3fc"), Color(hex: "#0ea5e9")],
        [Color(hex: "#bbf7d0"), Color(hex: "#4ade80"), Color(hex: "#16a34a")],
        [Color(hex: "#f1f5f9"), Color(hex: "#94a3b8"), Color(hex: "#475569")],
        [Color(hex: "#fed7aa"), Color(hex: "#fb923c"), Color(hex: "#ea580c")]
    ]
    
    static func gradient(for index: Int, colorScheme: ColorScheme) -> [Color] {
        let safeIndex = abs(index) % 5
        return colorScheme == .dark ? darkGradients[safeIndex] : lightGradients[safeIndex]
    }
    
    static func gradient(for sound: Sound, colorScheme: ColorScheme) -> [Color] {
        return gradient(for: sound.soundIndex, colorScheme: colorScheme)
    }
    
    static func cardGradient(for index: Int, colorScheme: ColorScheme) -> LinearGradient {
        let colors = gradient(for: index, colorScheme: colorScheme)
        return LinearGradient(
            colors: colors,
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
    
    static func cardGradient(for sound: Sound, colorScheme: ColorScheme) -> LinearGradient {
        return cardGradient(for: sound.soundIndex, colorScheme: colorScheme)
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
