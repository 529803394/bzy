import SwiftUI

enum AppearanceMode: String, CaseIterable, Identifiable {
    case system = "跟随系统"
    case light = "浅色"
    case dark = "深色"
    
    var id: String { rawValue }
}

struct SettingsView: View {
    @EnvironmentObject var audioPlayer: AudioPlayer
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    
    @AppStorage("volume") private var volume: Double = 0.7
    @AppStorage("timerDuration") private var timerDuration: Int = 0
    @AppStorage("isLooping") private var isLooping: Bool = true
    @AppStorage("appearanceMode") private var appearanceMode: String = AppearanceMode.system.rawValue
    @AppStorage("textSize") private var textSize: Double = 1.0
    
    @State private var showTimer = false
    
    private let version = "2.23.29"
    
    var body: some View {
        List {
            playbackSection
            displaySection
            aboutSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        .background(Color(.systemGroupedBackground))
        .onAppear {
            volume = Double(audioPlayer.volume)
            isLooping = audioPlayer.isLooping
        }
        .onChange(of: volume) { _, newValue in
            audioPlayer.setVolume(Float(newValue))
        }
        .onChange(of: isLooping) { _, newValue in
            audioPlayer.isLooping = newValue
        }
        .sheet(isPresented: $showTimer) {
            NavigationStack {
                TimerView()
            }
        }
    }
    
    private var playbackSection: some View {
        Section {
            VStack(spacing: 12) {
                HStack {
                    Image(systemName: "speaker.wave.2.fill")
                        .foregroundColor(.cyan)
                        .frame(width: 24)
                    Text("音量")
                        .font(.body)
                    Spacer()
                    Text("\(Int(volume * 100))%")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .monospacedDigit()
                }
                
                Slider(value: $volume, in: 0...1) {
                } minimumValueLabel: {
                    Image(systemName: "speaker.fill")
                        .foregroundColor(.secondary)
                } maximumValueLabel: {
                    Image(systemName: "speaker.wave.3.fill")
                        .foregroundColor(.secondary)
                }
                .tint(.cyan)
            }
            .padding(.vertical, 4)
            
            Button {
                showTimer = true
            } label: {
                HStack {
                    Image(systemName: "timer")
                        .foregroundColor(.cyan)
                        .frame(width: 24)
                    Text("定时关闭")
                        .foregroundColor(.primary)
                    Spacer()
                    Text(timerDuration > 0 ? formatMinutes(timerDuration) : "未开启")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(.tertiary)
                }
            }
            
            Toggle(isOn: $isLooping) {
                HStack {
                    Image(systemName: "repeat")
                        .foregroundColor(.cyan)
                        .frame(width: 24)
                    Text("循环播放")
                }
            }
            .tint(.cyan)
        } header: {
            Text("播放设置")
        }
    }
    
    private var displaySection: some View {
        Section {
            Picker(selection: $appearanceMode) {
                ForEach(AppearanceMode.allCases) { mode in
                    Text(mode.rawValue).tag(mode.rawValue)
                }
            } label: {
                HStack {
                    Image(systemName: "moon.fill")
                        .foregroundColor(.cyan)
                        .frame(width: 24)
                    Text("深色模式")
                }
            }
            .pickerStyle(.navigationLink)
            .tint(.cyan)
            
            VStack(spacing: 12) {
                HStack {
                    Image(systemName: "textformat.size")
                        .foregroundColor(.cyan)
                        .frame(width: 24)
                    Text("文字大小")
                        .font(.body)
                    Spacer()
                    Text("\(Int(textSize * 100))%")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .monospacedDigit()
                }
                
                Slider(value: $textSize, in: 0.8...1.4, step: 0.1)
                    .tint(.cyan)
            }
            .padding(.vertical, 4)
        } header: {
            Text("显示设置")
        }
    }
    
    private var aboutSection: some View {
        Section {
            HStack {
                Image(systemName: "info.circle")
                    .foregroundColor(.cyan)
                    .frame(width: 24)
                Text("版本号")
                Spacer()
                Text(version)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            Button {
                print("给我们评分")
            } label: {
                HStack {
                    Image(systemName: "star.fill")
                        .foregroundColor(.cyan)
                        .frame(width: 24)
                    Text("给我们评分")
                        .foregroundColor(.primary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(.tertiary)
                }
            }
            
            Button {
                print("隐私政策")
            } label: {
                HStack {
                    Image(systemName: "hand.raised.fill")
                        .foregroundColor(.cyan)
                        .frame(width: 24)
                    Text("隐私政策")
                        .foregroundColor(.primary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(.tertiary)
                }
            }
            
            Button {
                print("用户协议")
            } label: {
                HStack {
                    Image(systemName: "doc.text.fill")
                        .foregroundColor(.cyan)
                        .frame(width: 24)
                    Text("用户协议")
                        .foregroundColor(.primary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(.tertiary)
                }
            }
        } header: {
            Text("关于")
        }
    }
    
    private func formatMinutes(_ minutes: Int) -> String {
        if minutes >= 60 {
            let hours = minutes / 60
            let mins = minutes % 60
            if mins > 0 {
                return "\(hours)小时\(mins)分钟"
            }
            return "\(hours)小时"
        }
        return "\(minutes)分钟"
    }
}

#Preview {
    NavigationStack {
        SettingsView()
            .environmentObject(AudioPlayer.shared)
    }
}
