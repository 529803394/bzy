import SwiftUI

struct ProfileView: View {
    @EnvironmentObject var soundStore: SoundStore
    @Environment(\.colorScheme) private var colorScheme
    @State private var showSettings = false
    @State private var showTimer = false
    @State private var showAbout = false
    
    private let version = "2.23.29"
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                headerSection
                menuSection
                versionFooter
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, 32)
        }
        .background(Color(.systemGroupedBackground))
        .sheet(isPresented: $showSettings) {
            NavigationStack {
                SettingsView()
            }
        }
        .sheet(isPresented: $showTimer) {
            NavigationStack {
                TimerView()
            }
        }
    }
    
    private var headerSection: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 0.133, green: 0.827, blue: 0.933),
                                Color(red: 0.094, green: 0.576, blue: 0.686)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 72, height: 72)
                
                Image(systemName: "person.fill")
                    .font(.system(size: 36, weight: .medium))
                    .foregroundColor(.white)
            }
            .shadow(color: Color.black.opacity(0.15), radius: 8, x: 0, y: 4)
            
            VStack(alignment: .leading, spacing: 6) {
                Text("未登录")
                    .font(.title3)
                    .fontWeight(.semibold)
                
                Text("点击登录体验更多功能")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(.tertiary)
        }
        .padding(20)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
    }
    
    private var menuSection: some View {
        VStack(spacing: 0) {
            menuItem(
                icon: "heart.fill",
                iconColor: .pink,
                title: "我的收藏",
                action: { print("我的收藏") }
            )
            
            Divider()
                .padding(.leading, 56)
            
            menuItem(
                icon: "arrow.down.circle.fill",
                iconColor: .blue,
                title: "下载管理",
                action: { print("下载管理") }
            )
            
            Divider()
                .padding(.leading, 56)
            
            menuItem(
                icon: "timer",
                iconColor: .orange,
                title: "定时关闭",
                action: { showTimer = true }
            )
            
            Divider()
                .padding(.leading, 56)
            
            menuItem(
                icon: "moon.fill",
                iconColor: .purple,
                title: "睡眠模式",
                action: { print("睡眠模式") }
            )
            
            Divider()
                .padding(.leading, 56)
            
            menuItem(
                icon: "gearshape.fill",
                iconColor: .gray,
                title: "设置",
                action: { showSettings = true }
            )
            
            Divider()
                .padding(.leading, 56)
            
            menuItem(
                icon: "info.circle.fill",
                iconColor: .cyan,
                title: "关于我们",
                action: { showAbout = true }
            )
        }
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
    }
    
    private func menuItem(icon: String, iconColor: Color, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(iconColor)
                    .frame(width: 32, height: 32)
                
                Text(title)
                    .font(.body)
                    .foregroundColor(.primary)
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(.tertiary)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var versionFooter: some View {
        VStack(spacing: 4) {
            Text("版本 \(version)")
                .font(.footnote)
                .foregroundColor(.secondary)
            
            Text("© 2024 WhiteNoise")
                .font(.caption)
                .foregroundColor(.tertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 20)
    }
}

#Preview {
    NavigationStack {
        ProfileView()
            .environmentObject(SoundStore.shared)
    }
}
