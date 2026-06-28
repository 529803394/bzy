import SwiftUI

struct MoreMenuSheet: View {
    @Environment(\.dismiss) var dismiss
    let sound: Sound
    var onGenerateImage: () -> Void
    var onGenerateVideo: () -> Void
    var onShowTasks: () -> Void
    var onShare: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(Color.secondary.opacity(0.3))
                .frame(width: 40, height: 4)
                .padding(.top, 12)
                .padding(.bottom, 20)
            
            Text(sound.name)
                .font(.headline)
                .padding(.bottom, 20)
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 20) {
                MenuItemButton(
                    icon: "photo.on.rectangle.angled",
                    title: "智能配图",
                    action: {
                        dismiss()
                        onGenerateImage()
                    }
                )
                
                MenuItemButton(
                    icon: "video.fill",
                    title: "生成视频",
                    action: {
                        dismiss()
                        onGenerateVideo()
                    }
                )
                
                MenuItemButton(
                    icon: "list.bullet.rectangle",
                    title: "后台任务",
                    action: {
                        dismiss()
                        onShowTasks()
                    }
                )
                
                MenuItemButton(
                    icon: "square.and.arrow.up",
                    title: "分享",
                    action: {
                        dismiss()
                        onShare()
                    }
                )
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 30)
        }
        .background(.ultraThinMaterial)
        .presentationDetents([.height(280)])
        .presentationDragIndicator(.hidden)
    }
}

struct MenuItemButton: View {
    let icon: String
    let title: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title2)
                    .frame(width: 50, height: 50)
                    .background(
                        Circle()
                            .fill(Color.accentColor.opacity(0.15))
                    )
                
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.primary)
            }
        }
    }
}
