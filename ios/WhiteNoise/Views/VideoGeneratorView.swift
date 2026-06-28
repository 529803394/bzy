import SwiftUI

struct VideoGeneratorView: View {
    let sound: Sound
    var initialImageUrl: String? = nil
    
    @EnvironmentObject var soundStore: SoundStore
    @EnvironmentObject var taskStore: TaskStore
    @Environment(\.dismiss) var dismiss
    
    @State private var prompt: String = "画面缓缓流动，柔和自然"
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    @State private var showError = false
    @State private var successMessage: String?
    @State private var showSuccess = false
    @State private var showTaskList = false
    
    private var imageUrl: String? {
        initialImageUrl ?? sound.bgImageUrl
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("背景图预览")
                            .font(.headline)
                        
                        if let bgImageUrl = imageUrl {
                            AsyncImage(url: URL(string: bgImageUrl)) { phase in
                                switch phase {
                                case .empty:
                                    ProgressView()
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 200)
                                case .success(let image):
                                    image
                                        .resizable()
                                        .scaledToFit()
                                        .cornerRadius(12)
                                case .failure:
                                    placeholderImage
                                @unknown default:
                                    EmptyView()
                                }
                            }
                        } else {
                            placeholderImage
                        }
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("视频提示词")
                            .font(.headline)
                        
                        TextField("输入视频生成提示词...", text: $prompt, axis: .vertical)
                            .textFieldStyle(.plain)
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(Color(.secondarySystemGroupedBackground))
                            )
                            .lineLimit(3...6)
                    }
                    
                    Button {
                        submitVideoTask()
                    } label: {
                        HStack {
                            if isSubmitting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Image(systemName: "video.fill")
                            }
                            Text(isSubmitting ? "提交中..." : "生成视频")
                                .font(.headline)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(canSubmit ? Color.accentColor : Color.gray)
                        )
                        .foregroundColor(.white)
                    }
                    .disabled(!canSubmit || isSubmitting)
                    
                    if showTaskList {
                        NavigationLink(destination: TaskListView(soundId: sound.id).environmentObject(taskStore), isActive: $showTaskList) {
                            EmptyView()
                        }
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("生成视频")
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
            .alert("提交成功", isPresented: $showSuccess) {
                Button("查看任务") {
                    showTaskList = true
                }
                Button("关闭", role: .cancel) {}
            } message: {
                Text(successMessage ?? "视频任务已提交，请到后台任务查看进度")
            }
        }
        .navigationBarBackButtonHidden(true)
    }
    
    private var canSubmit: Bool {
        imageUrl != nil && !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    private var placeholderImage: some View {
        VStack(spacing: 8) {
            Image(systemName: "photo")
                .font(.system(size: 40))
                .foregroundColor(.gray)
            Text("暂无背景图")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 200)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }
    
    private func submitVideoTask() {
        guard let bgImageUrl = imageUrl else {
            errorMessage = "请先生成或设置背景图"
            showError = true
            return
        }
        
        let trimmedPrompt = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPrompt.isEmpty else { return }
        
        isSubmitting = true
        
        Task {
            do {
                if let taskId = try await AIService.shared.submitVideoTask(imageUrl: bgImageUrl, prompt: trimmedPrompt) {
                    var task = Task(taskId: taskId, type: TaskType.video.rawValue, soundId: sound.id)
                    task.title = "背景视频生成"
                    taskStore.addTask(task)
                    
                    var sound = sound
                    sound.bgVideoTaskId = taskId
                    soundStore.updateSound(sound)
                    
                    successMessage = "视频任务已提交，请到后台任务查看进度"
                    showSuccess = true
                } else {
                    errorMessage = "提交视频任务失败，请重试"
                    showError = true
                }
            } catch {
                errorMessage = error.localizedDescription
                showError = true
            }
            
            isSubmitting = false
        }
    }
}
