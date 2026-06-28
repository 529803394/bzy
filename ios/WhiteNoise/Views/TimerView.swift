import SwiftUI
import Combine

struct TimerView: View {
    @EnvironmentObject var audioPlayer: AudioPlayer
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    
    @AppStorage("timerDuration") private var savedDuration: Int = 0
    
    @State private var selectedMinutes: Int = 0
    @State private var remainingTime: TimeInterval = 0
    @State private var isTimerRunning: Bool = false
    @State private var timer: Timer? = nil
    
    private let timerOptions: [Int] = [0, 15, 30, 45, 60, 90, 120]
    
    var body: some View {
        VStack(spacing: 32) {
            timerDisplaySection
            
            if !isTimerRunning {
                timerOptionsSection
            }
            
            Spacer()
            
            actionButton
        }
        .padding(.horizontal, 24)
        .padding(.top, 32)
        .padding(.bottom, 48)
        .background(Color(.systemGroupedBackground))
        .navigationTitle("定时关闭")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if isTimerRunning {
                    Button("取消") {
                        cancelTimer()
                    }
                    .foregroundColor(.red)
                }
            }
        }
        .onAppear {
            selectedMinutes = savedDuration
        }
    }
    
    private var timerDisplaySection: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(
                        Color(.systemGray5),
                        lineWidth: 12
                    )
                    .frame(width: 240, height: 240)
                
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(
                        LinearGradient(
                            colors: [
                                Color(red: 0.133, green: 0.827, blue: 0.933),
                                Color(red: 0.094, green: 0.576, blue: 0.686)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        style: StrokeStyle(
                            lineWidth: 12,
                            lineCap: .round
                        )
                    )
                    .frame(width: 240, height: 240)
                    .rotationEffect(.degrees(-90))
                    .animation(.easeInOut(duration: 0.3), value: progress)
                
                VStack(spacing: 8) {
                    Text(formattedTime)
                        .font(.system(size: 48, weight: .bold))
                        .fontDesign(.rounded)
                    
                    Text(isTimerRunning ? "播放将自动停止" : "选择定时时长")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }
            .shadow(color: Color.black.opacity(0.08), radius: 12, x: 0, y: 4)
        }
        .frame(maxWidth: .infinity)
    }
    
    private var timerOptionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("选择时长")
                .font(.headline)
                .fontWeight(.semibold)
                .padding(.horizontal, 4)
            
            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12)
                ],
                spacing: 12
            ) {
                ForEach(timerOptions, id: \.self) { minutes in
                    TimerOptionButton(
                        minutes: minutes,
                        isSelected: selectedMinutes == minutes,
                        action: {
                            selectedMinutes = minutes
                        }
                    )
                }
            }
        }
    }
    
    private var actionButton: some View {
        Button {
            if isTimerRunning {
                cancelTimer()
            } else if selectedMinutes > 0 {
                startTimer()
            }
        } label: {
            Text(isTimerRunning ? "取消定时" : (selectedMinutes > 0 ? "开始计时" : "请选择时长"))
                .font(.headline)
                .fontWeight(.semibold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(
                    isTimerRunning
                        ? Color.red.opacity(selectedMinutes > 0 ? 1 : 0.5)
                        : Color(red: 0.133, green: 0.827, blue: 0.933).opacity(selectedMinutes > 0 ? 1 : 0.5)
                )
                .clipShape(RoundedRectangle(cornerRadius: 26))
                .shadow(color: (isTimerRunning ? Color.red : Color(red: 0.133, green: 0.827, blue: 0.933)).opacity(0.3), radius: 8, x: 0, y: 4)
        }
        .disabled(!isTimerRunning && selectedMinutes == 0)
    }
    
    private var progress: Double {
        guard selectedMinutes > 0 else { return 0 }
        let total = Double(selectedMinutes * 60)
        return isTimerRunning ? (remainingTime / total) : 1.0
    }
    
    private var formattedTime: String {
        let time = isTimerRunning ? remainingTime : Double(selectedMinutes * 60)
        let hours = Int(time) / 3600
        let minutes = (Int(time) % 3600) / 60
        let seconds = Int(time) % 60
        
        if hours > 0 {
            return String(format: "%02d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%02d:%02d", minutes, seconds)
    }
    
    private func startTimer() {
        remainingTime = Double(selectedMinutes * 60)
        isTimerRunning = true
        savedDuration = selectedMinutes
        
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if remainingTime > 0 {
                remainingTime -= 1
            } else {
                timerFired()
            }
        }
    }
    
    private func cancelTimer() {
        timer?.invalidate()
        timer = nil
        isTimerRunning = false
        remainingTime = 0
        savedDuration = 0
        selectedMinutes = 0
    }
    
    private func timerFired() {
        timer?.invalidate()
        timer = nil
        isTimerRunning = false
        remainingTime = 0
        savedDuration = 0
        selectedMinutes = 0
        audioPlayer.pause()
    }
}

struct TimerOptionButton: View {
    let minutes: Int
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text(displayText)
                    .font(.headline)
                    .fontWeight(.semibold)
                    .foregroundColor(isSelected ? .white : .primary)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 64)
            .background(
                isSelected
                    ? LinearGradient(
                        colors: [
                            Color(red: 0.133, green: 0.827, blue: 0.933),
                            Color(red: 0.094, green: 0.576, blue: 0.686)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    : nil
            )
            .background(isSelected ? Color.clear : Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .shadow(color: isSelected ? Color(red: 0.133, green: 0.827, blue: 0.933).opacity(0.4) : Color.black.opacity(0.06), radius: isSelected ? 8 : 4, x: 0, y: 2)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var displayText: String {
        if minutes == 0 {
            return "不开启"
        } else if minutes >= 60 {
            let hours = minutes / 60
            let mins = minutes % 60
            if mins > 0 {
                return "\(hours)h\(mins)m"
            }
            return "\(hours)小时"
        }
        return "\(minutes)分钟"
    }
}

#Preview {
    NavigationStack {
        TimerView()
            .environmentObject(AudioPlayer.shared)
    }
}
