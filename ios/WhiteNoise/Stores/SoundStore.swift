import Foundation
import SwiftUI

struct Sound: Identifiable, Codable, Equatable {
    var id: String
    var name: String
    var resId: Int = 0
    var url: String?
    var bgImageUrl: String?
    var bgVideoUrl: String?
    var localPath: String?
    var bgImageLocalPath: String?
    var bgVideoLocalPath: String?
    var bgVideoTaskId: String?
    var isCustom: Bool = false
    var isPinned: Bool = false
    var isDeleted: Bool = false
    var isNetwork: Bool = false
    var fileSize: Int64 = 0
    var lastMessage: String = ""
    var lastTime: TimeInterval = 0
    var themeIndex: Int = 0
    var createTime: TimeInterval = Date().timeIntervalSince1970
    
    enum CodingKeys: String, CodingKey {
        case id, name, resId, url, bgImageUrl, bgVideoUrl
        case localPath, bgImageLocalPath, bgVideoLocalPath, bgVideoTaskId
        case isCustom, isPinned, isDeleted, isNetwork
        case fileSize, lastMessage, lastTime, themeIndex, createTime
    }
    
    init(id: String, name: String, resId: Int = 0) {
        self.id = id
        self.name = name
        self.resId = resId
        self.themeIndex = abs(id.hashValue) % 5
    }
    
    init(id: String, name: String, url: String, bgImageUrl: String?) {
        self.id = id
        self.name = name
        self.resId = 0
        self.url = url
        self.bgImageUrl = bgImageUrl
        self.isCustom = true
        self.themeIndex = abs(id.hashValue) % 5
    }
    
    static func fromNetwork(url: String, name: String, bgImageUrl: String? = nil) -> Sound {
        var sound = Sound(id: "net_" + url.md5, name: name, resId: 0)
        sound.url = url
        sound.bgImageUrl = bgImageUrl
        sound.isNetwork = true
        sound.isCustom = false
        return sound
    }
    
    var soundIndex: Int {
        if isCustom { return abs(id.hashValue) % 5 }
        for (i, defaultName) in SoundStore.defaultNames.enumerated() {
            if name == defaultName { return i }
        }
        return abs(id.hashValue) % 5
    }
    
    var chatBgColors: [Color] {
        return SoundStore.chatBgColors[soundIndex]
    }
    
    var chatBgColorsLight: [Color] {
        return SoundStore.chatBgColorsLight[soundIndex]
    }
}

extension String {
    var md5: String {
        let data = Data(self.utf8)
        let hash = data.withUnsafeBytes { bytes -> [UInt8] in
            var hash = [UInt8](repeating: 0, count: Int(CC_MD5_DIGEST_LENGTH))
            CC_MD5(bytes.baseAddress, CC_LONG(data.count), &hash)
            return hash
        }
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}

class SoundStore: ObservableObject {
    static let shared = SoundStore()
    
    @Published var sounds: [Sound] = []
    
    static let defaultNames = ["雨声", "海浪", "森林", "风声", "篝火"]
    
    static let chatBgColors: [[Color]] = [
        [Color(red: 0.13, green: 0.83, blue: 0.93), Color(red: 0.06, green: 0.09, blue: 0.16)],
        [Color(red: 0.22, green: 0.74, blue: 0.97), Color(red: 0.12, green: 0.25, blue: 0.69)],
        [Color(red: 0.29, green: 0.87, blue: 0.50), Color(red: 0.02, green: 0.37, blue: 0.27)],
        [Color(red: 0.58, green: 0.64, blue: 0.72), Color(red: 0.12, green: 0.16, blue: 0.23)],
        [Color(red: 0.98, green: 0.57, blue: 0.24), Color(red: 0.49, green: 0.18, blue: 0.07)]
    ]
    
    static let chatBgColorsLight: [[Color]] = [
        [Color(red: 0.73, green: 0.90, blue: 0.99), Color(red: 0.49, green: 0.83, blue: 0.99), Color(red: 0.05, green: 0.65, blue: 0.91)],
        [Color(red: 0.99, green: 0.95, blue: 0.78), Color(red: 0.99, green: 0.90, blue: 0.54), Color(red: 0.22, green: 0.74, blue: 0.97)],
        [Color(red: 0.86, green: 0.99, blue: 0.90), Color(red: 0.52, green: 0.94, blue: 0.67), Color(red: 0.09, green: 0.64, blue: 0.29)],
        [Color(red: 0.95, green: 0.96, blue: 0.98), Color(red: 0.79, green: 0.84, blue: 0.88), Color(red: 0.39, green: 0.45, blue: 0.55)],
        [Color(red: 1.00, green: 0.84, blue: 0.67), Color(red: 0.99, green: 0.73, blue: 0.45), Color(red: 0.92, green: 0.35, blue: 0.05)]
    ]
    
    private let userDefaultsKey = "whitenoise_store_v2_sounds"
    
    private init() {
        load()
    }
    
    func getAll() -> [Sound] {
        return sounds
    }
    
    func findById(_ id: String) -> Sound? {
        return sounds.first { $0.id == id }
    }
    
    func addSound(_ sound: Sound) {
        if let index = sounds.firstIndex(where: { $0.id == sound.id }) {
            sounds[index] = sound
        } else {
            sounds.append(sound)
        }
        sortSounds()
        save()
    }
    
    func deleteSound(id: String) {
        if let index = sounds.firstIndex(where: { $0.id == id }) {
            if sounds[index].isCustom {
                sounds.remove(at: index)
            } else {
                sounds[index].isDeleted = true
            }
            save()
        }
    }
    
    func updateSound(_ sound: Sound) {
        if let index = sounds.firstIndex(where: { $0.id == sound.id }) {
            sounds[index] = sound
            sortSounds()
            save()
        }
    }
    
    func save() {
        do {
            let data = try JSONEncoder().encode(sounds)
            UserDefaults.standard.set(data, forKey: userDefaultsKey)
        } catch {
            print("SoundStore save error: \(error)")
        }
    }
    
    private func load() {
        sounds = []
        for (i, name) in SoundStore.defaultNames.enumerated() {
            let sound = Sound(id: "built_in_\(i)", name: name, resId: i + 1)
            sounds.append(sound)
        }
        
        guard let data = UserDefaults.standard.data(forKey: userDefaultsKey) else {
            sortSounds()
            return
        }
        
        do {
            let savedSounds = try JSONDecoder().decode([Sound].self, from: data)
            for savedSound in savedSounds {
                if !savedSound.isCustom && !savedSound.isNetwork {
                    if let index = sounds.firstIndex(where: { $0.id == savedSound.id }) {
                        sounds[index].isPinned = savedSound.isPinned
                        sounds[index].isDeleted = savedSound.isDeleted
                        sounds[index].lastMessage = savedSound.lastMessage
                        sounds[index].lastTime = savedSound.lastTime
                        sounds[index].bgImageUrl = savedSound.bgImageUrl
                        sounds[index].bgVideoUrl = savedSound.bgVideoUrl
                        sounds[index].bgVideoLocalPath = savedSound.bgVideoLocalPath
                        sounds[index].bgVideoTaskId = savedSound.bgVideoTaskId
                    }
                } else {
                    sounds.append(savedSound)
                }
            }
        } catch {
            print("SoundStore load error: \(error)")
        }
        
        sortSounds()
    }
    
    private func sortSounds() {
        sounds.sort { a, b in
            if a.isPinned != b.isPinned {
                return a.isPinned && !b.isPinned
            }
            return a.createTime < b.createTime
        }
    }
    
    func togglePin(id: String) {
        if let index = sounds.firstIndex(where: { $0.id == id }) {
            sounds[index].isPinned.toggle()
            sortSounds()
            save()
        }
    }
    
    func markDeleted(id: String, deleted: Bool) {
        if let index = sounds.firstIndex(where: { $0.id == id }) {
            sounds[index].isDeleted = deleted
            save()
        }
    }
    
    func setLastMessage(id: String, message: String) {
        if let index = sounds.firstIndex(where: { $0.id == id }) {
            sounds[index].lastMessage = message
            sounds[index].lastTime = Date().timeIntervalSince1970
            save()
        }
    }
    
    func getHomeList() -> [Sound] {
        return sounds.filter { !$0.isDeleted }
    }
    
    func getLibraryList() -> [Sound] {
        return sounds
    }
    
    static func matchBuiltinName(_ name: String?) -> String {
        guard let name = name, !name.isEmpty else { return defaultNames[0] }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        for cn in defaultNames {
            if cn == trimmed { return cn }
            if trimmed.contains(cn) { return cn }
            if cn.contains(trimmed) { return cn }
        }
        if containsAny(trimmed, keys: "雨", "rain") { return "雨声" }
        if containsAny(trimmed, keys: "海", "浪", "ocean", "wave") { return "海浪" }
        if containsAny(trimmed, keys: "森", "林", "forest", "鸟") { return "森林" }
        if containsAny(trimmed, keys: "风", "wind") { return "风声" }
        if containsAny(trimmed, keys: "火", "篝", "burn", "fire") { return "篝火" }
        return defaultNames[0]
    }
    
    private static func containsAny(_ src: String, keys: String...) -> Bool {
        let lower = src.lowercased()
        for k in keys {
            if lower.contains(k.lowercased()) { return true }
        }
        return false
    }
}
