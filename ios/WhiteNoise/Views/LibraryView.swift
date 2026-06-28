import SwiftUI

struct LibraryView: View {
    @EnvironmentObject var soundStore: SoundStore
    @EnvironmentObject var audioPlayer: AudioPlayer
    @Environment(\.colorScheme) private var colorScheme
    
    @State private var viewMode: ViewMode = .grid
    @State private var selectedSegment: Segment = .all
    @State private var showAddSound = false
    
    enum ViewMode: String, CaseIterable, Identifiable {
        case grid = "square.grid.2x2.fill"
        case list = "list.dash"
        
        var id: String { rawValue }
    }
    
    enum Segment: String, CaseIterable, Identifiable {
        case all = "全部"
        case deleted = "已删除"
        case custom = "自定义"
        
        var id: String { rawValue }
    }
    
    private var filteredSounds: [Sound] {
        let all = soundStore.getLibraryList()
        switch selectedSegment {
        case .all:
            return all
        case .deleted:
            return all.filter { $0.isDeleted }
        case .custom:
            return all.filter { $0.isCustom }
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            segmentControl
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)
            
            if filteredSounds.isEmpty {
                emptyState
            } else {
                ScrollView {
                    if viewMode == .grid {
                        gridContent
                            .padding(.horizontal, 16)
                            .padding(.bottom, 20)
                    } else {
                        listContent
                            .padding(.bottom, 20)
                    }
                }
            }
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("乐库")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 16) {
                    Button {
                        showAddSound = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                            .foregroundColor(.cyan)
                    }
                    
                    Button {
                        toggleViewMode()
                    } label: {
                        Image(systemName: viewMode == .grid ? "list.dash" : "square.grid.2x2.fill")
                            .font(.title3)
                            .foregroundColor(.primary)
                    }
                }
            }
        }
        .sheet(isPresented: $showAddSound) {
            Text("添加自定义声音")
                .presentationDetents([.medium])
        }
    }
    
    private var segmentControl: some View {
        HStack(spacing: 0) {
            ForEach(Segment.allCases) { segment in
                Button {
                    selectedSegment = segment
                } label: {
                    Text(segment.rawValue)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(selectedSegment == segment ? .white : .secondary)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 16)
                        .background(
                            selectedSegment == segment
                                ? Color(red: 0.133, green: 0.827, blue: 0.933)
                                : Color.clear
                        )
                        .clipShape(Capsule())
                }
            }
        }
        .padding(4)
        .background(Color(.systemBackground))
        .clipShape(Capsule())
        .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 2)
    }
    
    private var gridContent: some View {
        LazyVGrid(
            columns: [
                GridItem(.flexible(), spacing: 12),
                GridItem(.flexible(), spacing: 12)
            ],
            spacing: 12
        ) {
            ForEach(filteredSounds) { sound in
                SoundCardView(sound: sound) {
                    togglePlay(sound: sound)
                }
                .opacity(sound.isDeleted ? 0.5 : 1.0)
                .overlay(alignment: .topTrailing) {
                    if sound.isDeleted {
                        deletedBadge
                            .padding(10)
                    }
                }
                .contextMenu {
                    if sound.isDeleted {
                        Button {
                            restoreSound(sound)
                        } label: {
                            Label("恢复", systemImage: "arrow.uturn.backward")
                        }
                    } else {
                        Button(role: .destructive) {
                            deleteSound(sound)
                        } label: {
                            Label("删除", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .padding(.top, 12)
    }
    
    private var listContent: some View {
        LazyVStack(spacing: 0) {
            ForEach(filteredSounds) { sound in
                listRow(sound: sound)
                
                if sound.id != filteredSounds.last?.id {
                    Divider()
                        .padding(.leading, 72)
                }
            }
        }
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
        .padding(.top, 12)
    }
    
    private func listRow(sound: Sound) -> some View {
        Button {
            togglePlay(sound: sound)
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    ThemeColors.cardGradient(for: sound, colorScheme: colorScheme)
                        .frame(width: 48, height: 48)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    
                    Image(systemName: "music.note")
                        .font(.title2)
                        .foregroundColor(.white)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(sound.name)
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(sound.isCustom ? "自定义" : (sound.isDeleted ? "已删除" : "内置"))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                if sound.isDeleted {
                    Button {
                        restoreSound(sound)
                    } label: {
                        Image(systemName: "arrow.uturn.backward.circle.fill")
                            .font(.title2)
                            .foregroundColor(.green)
                    }
                    .buttonStyle(PlainButtonStyle())
                } else {
                    Image(systemName: "play.circle.fill")
                        .font(.title2)
                        .foregroundColor(.cyan)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .opacity(sound.isDeleted ? 0.6 : 1.0)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var emptyState: some View {
        VStack(spacing: 16) {
            Spacer()
            
            Image(systemName: emptyStateIcon)
                .font(.system(size: 64))
                .foregroundColor(.secondary)
                .opacity(0.6)
            
            Text(emptyStateTitle)
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.secondary)
            
            Text(emptyStateSubtitle)
                .font(.subheadline)
                .foregroundColor(.tertiary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            if selectedSegment == .custom {
                Button {
                    showAddSound = true
                } label: {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                        Text("添加自定义声音")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color(red: 0.133, green: 0.827, blue: 0.933))
                    .clipShape(Capsule())
                }
                .padding(.top, 8)
            }
            
            Spacer()
        }
    }
    
    private var emptyStateIcon: String {
        switch selectedSegment {
        case .all:
            return "music.note.list"
        case .deleted:
            return "trash"
        case .custom:
            return "plus.circle"
        }
    }
    
    private var emptyStateTitle: String {
        switch selectedSegment {
        case .all:
            return "暂无声音"
        case .deleted:
            return "暂无已删除声音"
        case .custom:
            return "暂无自定义声音"
        }
    }
    
    private var emptyStateSubtitle: String {
        switch selectedSegment {
        case .all:
            return "添加一些声音开始你的白噪音之旅"
        case .deleted:
            return "删除的声音会显示在这里，可以随时恢复"
        case .custom:
            return "上传你喜欢的声音，打造专属白噪音库"
        }
    }
    
    private var deletedBadge: some View {
        Image(systemName: "trash.fill")
            .font(.caption)
            .foregroundColor(.white)
            .padding(6)
            .background(Color.black.opacity(0.6))
            .clipShape(Circle())
    }
    
    private func toggleViewMode() {
        withAnimation(.easeInOut(duration: 0.2)) {
            viewMode = viewMode == .grid ? .list : .grid
        }
    }
    
    private func togglePlay(sound: Sound) {
        guard !sound.isDeleted else { return }
        
        if audioPlayer.currentSoundId == sound.id && audioPlayer.isPlaying {
            audioPlayer.pause()
        } else {
            audioPlayer.play(sound: sound)
        }
    }
    
    private func deleteSound(_ sound: Sound) {
        soundStore.deleteSound(id: sound.id)
    }
    
    private func restoreSound(_ sound: Sound) {
        soundStore.markDeleted(id: sound.id, deleted: false)
    }
}

#Preview {
    NavigationStack {
        LibraryView()
            .environmentObject(SoundStore.shared)
            .environmentObject(AudioPlayer.shared)
    }
}
