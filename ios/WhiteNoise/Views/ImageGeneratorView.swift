import SwiftUI

struct ImageGeneratorView: View {
    let sound: Sound
    
    @EnvironmentObject var soundStore: SoundStore
    @Environment(\.dismiss) var dismiss
    
    @State private var prompt: String = ""
    @State private var generatedImageUrl: String?
    @State private var isGenerating = false
    @State private var errorMessage: String?
    @State private var showError = false
    @State private var successMessage: String?
    @State private var showSuccess = false
    @State private var showVideoGenerator = false
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("提示词")
                            .font(.headline)
                        
                        TextField("输入图片生成提示词...", text: $prompt, axis: .vertical)
                            .textFieldStyle(.plain)
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(Color(.secondarySystemGroupedBackground))
                            )
                            .lineLimit(3...6)
                    }
                    
                    Button {
                        generateImage()
                    } label: {
                        HStack {
                            if isGenerating {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Image(systemName: "wand.and.stars")
                            }
                            Text(isGenerating ? "生成中..." : "生成图片")
                                .font(.headline)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(isGenerating ? Color.gray : Color.accentColor)
                        )
                        .foregroundColor(.white)
                    }
                    .disabled(isGenerating || prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    
                    if let imageUrl = generatedImageUrl {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("生成结果")
                                .font(.headline)
                            
                            AsyncImage(url: URL(string: imageUrl)) { phase in
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
                                    Image(systemName: "photo")
                                        .font(.system(size: 40))
                                        .foregroundColor(.gray)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 200)
                                @unknown default:
                                    EmptyView()
                                }
                            }
                            
                            HStack(spacing: 12) {
                                Button {
                                    applyAsBackground()
                                } label: {
                                    Label("应用为背景", systemImage: "checkmark.circle.fill")
                                        .font(.subheadline)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .background(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .fill(Color.green.opacity(0.15))
                                        )
                                        .foregroundColor(.green)
                                }
                                
                                Button {
                                    showVideoGenerator = true
                                } label: {
                                    Label("生成视频", systemImage: "video.fill")
                                        .font(.subheadline)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .background(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .fill(Color.blue.opacity(0.15))
                                        )
                                        .foregroundColor(.blue)
                                }
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("智能配图")
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
            .onAppear {
                if prompt.isEmpty {
                    prompt = defaultPrompt()
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
            .sheet(isPresented: $showVideoGenerator) {
                VideoGeneratorView(sound: sound, initialImageUrl: generatedImageUrl)
                    .environmentObject(soundStore)
            }
        }
        .navigationBarBackButtonHidden(true)
    }
    
    private func defaultPrompt() -> String {
        switch sound.soundIndex {
        case 0:
            return "细雨绵绵的夜晚，雨滴轻轻敲打窗棂，温暖的灯光透出柔和的光晕"
        case 1:
            return "宁静的海边，海浪轻柔地拍打着沙滩，夕阳洒下金色的光芒"
        case 2:
            return "阳光穿过茂密的森林，树叶随风轻轻摇曳，鸟鸣声声"
        case 3:
            return "山谷间微风拂过，草浪随风起伏，远处山峦若隐若现"
        case 4:
            return "夜晚篝火熊熊燃烧，火星点点升空，周围是静谧的森林"
        default:
            return "宁静美好的自然风景，治愈系风格"
        }
    }
    
    private func generateImage() {
        let trimmedPrompt = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPrompt.isEmpty else { return }
        
        isGenerating = true
        generatedImageUrl = nil
        
        Task {
            do {
                if let url = try await AIService.shared.generateImage(prompt: trimmedPrompt, soundName: sound.name) {
                    generatedImageUrl = url
                } else {
                    errorMessage = "图片生成失败，请重试"
                    showError = true
                }
            } catch {
                errorMessage = error.localizedDescription
                showError = true
            }
            
            isGenerating = false
        }
    }
    
    private func applyAsBackground() {
        guard let imageUrl = generatedImageUrl,
              var sound = soundStore.findById(sound.id) else {
            return
        }
        
        sound.bgImageUrl = imageUrl
        soundStore.updateSound(sound)
        
        successMessage = "背景图已应用"
        showSuccess = true
    }
}
