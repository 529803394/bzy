import Foundation
import SwiftUI

struct ChatMessage: Identifiable, Codable, Equatable {
    var id: String = UUID().uuidString
    var time: TimeInterval
    var text: String
    var fromUser: Bool
    
    enum CodingKeys: String, CodingKey {
        case id, time, text, fromUser
    }
    
    init(text: String, fromUser: Bool) {
        self.text = text
        self.fromUser = fromUser
        self.time = Date().timeIntervalSince1970
    }
}

class MessageStore: ObservableObject {
    static let shared = MessageStore()
    
    private var messagesCache: [String: [ChatMessage]] = [:]
    
    private let keyPrefix = "wn_msgs_"
    
    private init() {}
    
    func getMessages(for soundId: String) -> [ChatMessage] {
        if let cached = messagesCache[soundId] {
            return cached
        }
        
        let key = keyPrefix + soundId
        guard let data = UserDefaults.standard.data(forKey: key) else {
            messagesCache[soundId] = []
            return []
        }
        
        do {
            let messages = try JSONDecoder().decode([ChatMessage].self, from: data)
            messagesCache[soundId] = messages
            return messages
        } catch {
            print("MessageStore load error: \(error)")
            messagesCache[soundId] = []
            return []
        }
    }
    
    func addMessage(_ message: ChatMessage, for soundId: String) {
        var messages = getMessages(for: soundId)
        messages.append(message)
        messagesCache[soundId] = messages
        saveMessages(messages, for: soundId)
        objectWillChange.send()
    }
    
    func clearMessages(for soundId: String) {
        messagesCache[soundId] = []
        let key = keyPrefix + soundId
        UserDefaults.standard.removeObject(forKey: key)
        objectWillChange.send()
    }
    
    private func saveMessages(_ messages: [ChatMessage], for soundId: String) {
        let key = keyPrefix + soundId
        do {
            let data = try JSONEncoder().encode(messages)
            UserDefaults.standard.set(data, forKey: key)
        } catch {
            print("MessageStore save error: \(error)")
        }
    }
}
