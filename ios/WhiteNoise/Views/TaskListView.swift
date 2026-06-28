import SwiftUI

struct TaskListView: View {
    let soundId: String
    
    @EnvironmentObject var taskStore: TaskStore
    @EnvironmentObject var soundStore: SoundStore
    @Environment(\.dismiss) var dismiss
    
    @State private var errorMessage: String?
    @State private var showError = false
    @State private var successMessage: String?
    @State private var showSuccess = false
    
    var tasks: [Task] {
        taskStore.getTasksBySoundId(soundId)
    }
    
    var body: some View {
        NavigationStack {
            Group {
                if tasks.isEmpty {
                    emptyState
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(tasks) { task in
                                taskRow(task)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 12)
                    }
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("后台任务")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.body)
                            .foregroundColor(.primary)
                    }
                }
            }
            .alert("错误", isPresented: $showError) {
                Button("确定", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "未知错误")
            }
            .alert("成功", isPresented: $showSuccess) {
                Button("确定", role: .cancel) {}
            } message: {
                Text(successMessage ?? "操作成功")
            }
        }
        .navigationBarBackButtonHidden(true)
    }
    
    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "tray")
                .font(.system(size: 48))
                .foregroundColor(.gray.opacity(0.5))
            
            Text("暂无后台任务")
                .font(.title3)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private func taskRow(_ task: Task) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(task.title)
                    .font(.headline)
                
                Spacer()
                
                statusLabel(for: task.status)
            }
            
            Text("ID: \(task.taskId)")
                .font(.caption)
                .foregroundColor(.gray)
            
            if let error = task.error {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
                    .lineLimit(2)
            }
            
            HStack(spacing: 12) {
                Spacer()
                
                Button {
                    applyTaskResult(task)
                } label: {
                    Label("应用", systemImage: "checkmark.circle.fill")
                        .font(.caption)
                        .foregroundColor(.green)
                }
                .disabled(!(task.status == TaskStatus.success.rawValue && task.result != nil))
                .opacity((task.status == TaskStatus.success.rawValue && task.result != nil) ? 1 : 0.3)
                
                Button {
                    refreshTask(task)
                } label: {
                    Label("刷新", systemImage: "arrow.clockwise.circle.fill")
                        .font(.caption)
                        .foregroundColor(.blue)
                }
                
                Button {
                    deleteTask(task)
                } label: {
                    Label("删除", systemImage: "trash.circle.fill")
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }
    
    private func statusLabel(for status: String) -> some View {
        let (text, color) = statusInfo(for: status)
        return Text(text)
            .font(.caption)
            .fontWeight(.medium)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                Capsule()
                    .fill(color.opacity(0.15))
            )
            .foregroundColor(color)
    }
    
    private func statusInfo(for status: String) -> (text: String, color: Color) {
        switch status {
        case TaskStatus.pending.rawValue:
            return ("等待中", .gray)
        case TaskStatus.processing.rawValue:
            return ("处理中", .blue)
        case TaskStatus.success.rawValue:
            return ("已完成", .green)
        case TaskStatus.failed.rawValue:
            return ("失败", .red)
        case TaskStatus.timeout.rawValue:
            return ("超时", .orange)
        default:
            return (status, .gray)
        }
    }
    
    private func applyTaskResult(_ task: Task) {
        guard task.status == TaskStatus.success.rawValue,
              let result = task.result,
              var sound = soundStore.findById(soundId) else {
            return
        }
        
        if task.type == TaskType.video.rawValue {
            sound.bgVideoUrl = result
            soundStore.updateSound(sound)
            successMessage = "视频背景已应用"
            showSuccess = true
        }
    }
    
    private func refreshTask(_ task: Task) {
        Task {
            do {
                let (status, videoUrl, error) = try await AIService.shared.queryVideoResult(taskId: task.taskId)
                
                let mappedStatus: String
                let result: String?
                let taskError: String?
                
                switch status {
                case "SUCCESS":
                    mappedStatus = TaskStatus.success.rawValue
                    result = videoUrl
                    taskError = nil
                case "FAIL":
                    mappedStatus = TaskStatus.failed.rawValue
                    result = nil
                    taskError = error ?? "生成失败"
                case "PROCESSING":
                    mappedStatus = TaskStatus.processing.rawValue
                    result = nil
                    taskError = nil
                default:
                    mappedStatus = TaskStatus.processing.rawValue
                    result = nil
                    taskError = nil
                }
                
                taskStore.updateStatus(
                    taskId: task.taskId,
                    status: mappedStatus,
                    result: result,
                    error: taskError
                )
            } catch {
                errorMessage = error.localizedDescription
                showError = true
            }
        }
    }
    
    private func deleteTask(_ task: Task) {
        taskStore.deleteTask(id: task.taskId)
    }
}
