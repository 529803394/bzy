import Foundation

struct RecResult: Codable {
    var soundName: String
    var shortReason: String
    var detailedReason: String
    var creativeName: String?
    var creativeDesc: String?
    var recipe: String?
    var recipeReason: String?
    
    enum CodingKeys: String, CodingKey {
        case soundName = "sound_name"
        case shortReason = "short_reason"
        case detailedReason = "detailed_reason"
        case creativeName = "creative_name"
        case creativeDesc = "creative_desc"
        case recipe
        case recipeReason = "recipe_reason"
    }
}

class AIService {
    static let shared = AIService()
    
    private let deepSeekAPIKey = "YOUR_DEEPSEEK_API_KEY"
    private let zhipuAPIKey = "YOUR_ZHIPU_API_KEY"
    
    private let deepSeekBaseURL = "https://api.deepseek.com/v1"
    private let zhipuBaseURL = "https://open.bigmodel.cn/api/paas/v4"
    
    private init() {}
    
    func chat(soundName: String, userText: String, history: [ChatMessage]) async throws -> String {
        do {
            let systemPrompt = "你是一个温柔、简洁、像朋友一样的「\(soundName)」陪伴助手"
            
            var messages: [[String: String]] = []
            messages.append(["role": "system", "content": systemPrompt])
            
            let recentHistory = Array(history.suffix(6))
            for msg in recentHistory {
                let role = msg.fromUser ? "user" : "assistant"
                messages.append(["role": role, "content": msg.text])
            }
            
            messages.append(["role": "user", "content": userText])
            
            let body: [String: Any] = [
                "model": "deepseek-chat",
                "messages": messages,
                "temperature": 0.85,
                "max_tokens": 220
            ]
            
            let url = URL(string: "\(deepSeekBaseURL)/chat/completions")!
            guard let data = try await postJSON(url: url, apiKey: deepSeekAPIKey, body: body) else {
                throw NSError(domain: "AIService", code: -1, userInfo: [NSLocalizedDescriptionKey: "无响应数据"])
            }
            
            if let content = extractDeepSeekContent(from: data) {
                return content
            } else {
                throw NSError(domain: "AIService", code: -2, userInfo: [NSLocalizedDescriptionKey: "解析响应失败"])
            }
        } catch {
            throw error
        }
    }
    
    func recommend(time: String, weather: String, recentHistory: [ChatMessage]) async throws -> RecResult {
        do {
            let systemPrompt = "你是一个专业的白噪音推荐助手。请根据用户的时间、天气和最近聊天记录，推荐合适的白噪音声音。"
            
            var historyText = ""
            for msg in recentHistory.suffix(6) {
                historyText += "\(msg.fromUser ? "用户" : "助手"): \(msg.text)\n"
            }
            
            let userPrompt = """
            当前时间：\(time)
            当前天气：\(weather)
            最近聊天记录：
            \(historyText)
            
            请推荐一个最适合的白噪音声音，返回JSON格式，包含以下字段：
            - soundName: 声音名称（如：雨声、海浪、森林、风声、篝火）
            - shortReason: 简短推荐理由（20字以内）
            - detailedReason: 详细推荐理由（100字以内）
            - creativeName: 创意名称
            - creativeDesc: 创意描述
            - recipe: 搭配配方
            - recipeReason: 配心理由
            
            只返回JSON，不要其他文字。
            """
            
            let messages: [[String: String]] = [
                ["role": "system", "content": systemPrompt],
                ["role": "user", "content": userPrompt]
            ]
            
            let body: [String: Any] = [
                "model": "deepseek-chat",
                "messages": messages,
                "temperature": 0.8,
                "max_tokens": 500,
                "response_format": ["type": "json_object"]
            ]
            
            let url = URL(string: "\(deepSeekBaseURL)/chat/completions")!
            guard let data = try await postJSON(url: url, apiKey: deepSeekAPIKey, body: body) else {
                throw NSError(domain: "AIService", code: -1, userInfo: [NSLocalizedDescriptionKey: "无响应数据"])
            }
            
            guard let content = extractDeepSeekContent(from: data) else {
                throw NSError(domain: "AIService", code: -2, userInfo: [NSLocalizedDescriptionKey: "解析响应失败"])
            }
            
            guard let jsonData = content.data(using: .utf8) else {
                throw NSError(domain: "AIService", code: -3, userInfo: [NSLocalizedDescriptionKey: "数据转换失败"])
            }
            
            let decoder = JSONDecoder()
            let result = try decoder.decode(RecResult.self, from: jsonData)
            return result
        } catch {
            throw error
        }
    }
    
    func generateImage(prompt: String, soundName: String) async throws -> String? {
        do {
            let fullPrompt = "白噪音\(soundName)背景图，\(prompt)，高清，治愈系，唯美风格"
            
            let body: [String: Any] = [
                "model": "cogview-3-plus",
                "prompt": fullPrompt
            ]
            
            let url = URL(string: "\(zhipuBaseURL)/images/generations")!
            guard let data = try await postJSON(url: url, apiKey: zhipuAPIKey, body: body) else {
                return nil
            }
            
            if let url = extractZhipuImageUrl(from: data) {
                return url
            } else {
                return nil
            }
        } catch {
            return nil
        }
    }
    
    func submitVideoTask(imageUrl: String, prompt: String) async throws -> String? {
        do {
            let body: [String: Any] = [
                "model": "cogvideox-3",
                "image_url": imageUrl,
                "prompt": prompt,
                "duration": 5,
                "quality": "speed"
            ]
            
            let url = URL(string: "\(zhipuBaseURL)/videos/generations")!
            guard let data = try await postJSON(url: url, apiKey: zhipuAPIKey, body: body) else {
                return nil
            }
            
            if let taskId = extractJSONField(json: data, key: "id") {
                return taskId
            } else {
                return nil
            }
        } catch {
            return nil
        }
    }
    
    func queryVideoResult(taskId: String) async throws -> (status: String, videoUrl: String?, error: String?) {
        do {
            let url = URL(string: "\(zhipuBaseURL)/async-result/\(taskId)")!
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.setValue("Bearer \(zhipuAPIKey)", forHTTPHeaderField: "Authorization")
            
            let (data, _) = try await URLSession.shared.data(for: request)
            
            let status = extractJSONField(json: data, key: "task_status") ?? "PROCESSING"
            
            if status == "SUCCESS" {
                let videoUrl = extractZhipuVideoUrl(from: data)
                return (status: status, videoUrl: videoUrl, error: nil)
            } else if status == "FAIL" {
                let errorMsg = extractJSONField(json: data, key: "error")
                return (status: status, videoUrl: nil, error: errorMsg)
            } else {
                return (status: status, videoUrl: nil, error: nil)
            }
        } catch {
            return (status: "FAIL", videoUrl: nil, error: error.localizedDescription)
        }
    }
    
    private func postJSON(url: URL, apiKey: String, body: [String: Any]) async throws -> Data? {
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            
            let (data, response) = try await URLSession.shared.data(for: request)
            
            if let httpResponse = response as? HTTPURLResponse {
                if httpResponse.statusCode >= 200 && httpResponse.statusCode < 300 {
                    return data
                } else {
                    let errorText = String(data: data, encoding: .utf8) ?? "未知错误"
                    throw NSError(domain: "AIService", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "HTTP \(httpResponse.statusCode): \(errorText)"])
                }
            }
            
            return data
        } catch {
            throw error
        }
    }
    
    private func extractJSONField(json data: Data, key: String) -> String? {
        do {
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                return json[key] as? String
            }
            return nil
        } catch {
            return nil
        }
    }
    
    private func jsonEscape(_ string: String) -> String {
        return string
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
            .replacingOccurrences(of: "\t", with: "\\t")
    }
    
    private func extractDeepSeekContent(from data: Data) -> String? {
        do {
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let choices = json["choices"] as? [[String: Any]],
               let firstChoice = choices.first,
               let message = firstChoice["message"] as? [String: Any],
               let content = message["content"] as? String {
                return content
            }
            return nil
        } catch {
            return nil
        }
    }
    
    private func extractZhipuImageUrl(from data: Data) -> String? {
        do {
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let data = json["data"] as? [[String: Any]],
               let firstData = data.first,
               let url = firstData["url"] as? String {
                return url
            }
            return nil
        } catch {
            return nil
        }
    }
    
    private func extractZhipuVideoUrl(from data: Data) -> String? {
        do {
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let data = json["data"] as? [[String: Any]],
               let firstData = data.first,
               let url = firstData["video_url"] as? String {
                return url
            }
            return nil
        } catch {
            return nil
        }
    }
}
