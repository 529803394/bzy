import Foundation
import SwiftUI

struct Task: Identifiable, Codable, Equatable {
    var id: String { taskId }
    var taskId: String
    var type: String
    var status: String
    var result: String?
    var error: String?
    var soundId: String
    var createTime: TimeInterval
    var updateTime: TimeInterval
    var title: String
    
    enum CodingKeys: String, CodingKey {
        case taskId, type, status, result, error
        case soundId, createTime, updateTime, title
    }
    
    init(taskId: String, type: String, soundId: String) {
        self.taskId = taskId
        self.type = type
        self.soundId = soundId
        self.status = TaskStatus.pending.rawValue
        self.result = nil
        self.error = nil
        self.createTime = Date().timeIntervalSince1970
        self.updateTime = Date().timeIntervalSince1970
        self.title = type == TaskType.video.rawValue ? "背景视频生成" : "后台任务"
    }
    
    var statusText: String {
        switch status {
        case TaskStatus.pending.rawValue: return "等待中"
        case TaskStatus.processing.rawValue: return "处理中"
        case TaskStatus.success.rawValue: return "已完成"
        case TaskStatus.failed.rawValue: return "失败"
        case TaskStatus.timeout.rawValue: return "超时"
        default: return status
        }
    }
    
    var isFinished: Bool {
        return status == TaskStatus.success.rawValue
            || status == TaskStatus.failed.rawValue
            || status == TaskStatus.timeout.rawValue
    }
}

enum TaskType: String {
    case video = "video"
}

enum TaskStatus: String {
    case pending = "pending"
    case processing = "processing"
    case success = "success"
    case failed = "failed"
    case timeout = "timeout"
}

class TaskStore: ObservableObject {
    static let shared = TaskStore()
    
    @Published var tasks: [Task] = []
    
    private let userDefaultsKey = "task_store_tasks"
    
    private init() {
        load()
    }
    
    func getTasksBySoundId(_ soundId: String) -> [Task] {
        return tasks
            .filter { $0.soundId == soundId }
            .sorted { $0.createTime > $1.createTime }
    }
    
    func addTask(_ task: Task) {
        if let index = tasks.firstIndex(where: { $0.taskId == task.taskId }) {
            tasks[index] = task
        } else {
            tasks.append(task)
        }
        sortTasks()
        save()
    }
    
    func deleteTask(id: String) {
        tasks.removeAll { $0.taskId == id }
        save()
    }
    
    func findById(_ id: String) -> Task? {
        return tasks.first { $0.taskId == id }
    }
    
    func updateStatus(taskId: String, status: String, result: String? = nil, error: String? = nil) {
        if let index = tasks.firstIndex(where: { $0.taskId == taskId }) {
            tasks[index].status = status
            if let result = result {
                tasks[index].result = result
            }
            if let error = error {
                tasks[index].error = error
            }
            tasks[index].updateTime = Date().timeIntervalSince1970
            sortTasks()
            save()
        }
    }
    
    func clearAll() {
        tasks = []
        save()
    }
    
    private func save() {
        do {
            let data = try JSONEncoder().encode(tasks)
            UserDefaults.standard.set(data, forKey: userDefaultsKey)
        } catch {
            print("TaskStore save error: \(error)")
        }
    }
    
    private func load() {
        guard let data = UserDefaults.standard.data(forKey: userDefaultsKey) else {
            return
        }
        
        do {
            tasks = try JSONDecoder().decode([Task].self, from: data)
            sortTasks()
        } catch {
            print("TaskStore load error: \(error)")
        }
    }
    
    private func sortTasks() {
        tasks.sort { $0.createTime > $1.createTime }
    }
}
