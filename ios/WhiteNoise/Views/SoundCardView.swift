import SwiftUI

struct SoundCardView: View {
    let sound: Sound
    var onTap: (() -> Void)? = nil
    
    @EnvironmentObject var audioPlayer: AudioPlayer
    @Environment(\.colorScheme) private var colorScheme
    
    private var isPlaying: Bool {
        audioPlayer.isPlaying && audioPlayer.currentSoundId == sound.id
    }
    
    var body: some View {
        Button(action: {
            onTap?()
        }) {
            cardContent
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var cardContent: some View {
        ZStack(alignment: .bottomTrailing) {
            if let bgImageUrl = sound.bgImageUrl, !bgImageUrl.isEmpty {
                backgroundImageView(url: bgImageUrl)
            } else if let bgImageLocalPath = sound.bgImageLocalPath, !bgImageLocalPath.isEmpty {
                localBackgroundImageView(path: bgImageLocalPath)
            } else {
                gradientBackground
            }
            
            VStack(alignment: .leading, spacing: 0) {
                Spacer()
                
                Text(sound.name)
                    .font(.title3)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .shadow(color: .black.opacity(0.3), radius: 2, x: 0, y: 1)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            
            playButton
                .padding(12)
        }
        .frame(height: 160)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: Color.black.opacity(0.15), radius: 8, x: 0, y: 4)
    }
    
    private var gradientBackground: some View {
        ThemeColors.cardGradient(for: sound, colorScheme: colorScheme)
    }
    
    private func backgroundImageView(url: String) -> some View {
        AsyncImage(url: URL(string: url)) { phase in
            switch phase {
            case .empty:
                gradientBackground
                    .overlay(
                        ProgressView()
                            .tint(.white)
                    )
            case .success(let image):
                image
                    .resizable()
                    .scaledToFill()
                    .overlay(
                        LinearGradient(
                            colors: [.clear, .black.opacity(0.4)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
            case .failure:
                gradientBackground
            @unknown default:
                gradientBackground
            }
        }
    }
    
    private func localBackgroundImageView(path: String) -> some View {
        let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
        let fullPath = (documentsPath as NSString).appendingPathComponent(path)
        
        return Image(uiImage: UIImage(contentsOfFile: fullPath) ?? UIImage())
            .resizable()
            .scaledToFill()
            .overlay(
                LinearGradient(
                    colors: [.clear, .black.opacity(0.4)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
    }
    
    private var playButton: some View {
        Button(action: togglePlay) {
            ZStack {
                Circle()
                    .fill(.white.opacity(0.9))
                    .frame(width: 44, height: 44)
                    .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.primary)
                    .offset(x: isPlaying ? 0 : 2)
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func togglePlay() {
        if isPlaying {
            audioPlayer.pause()
        } else {
            audioPlayer.play(sound: sound)
        }
    }
}
