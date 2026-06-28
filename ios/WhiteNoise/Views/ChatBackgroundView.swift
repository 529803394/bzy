import SwiftUI
import AVKit

struct ChatBackgroundView: View {
    let sound: Sound
    @State private var animateGradient = false
    
    var body: some View {
        ZStack {
            if let bgVideoUrl = sound.bgVideoUrl, let url = URL(string: bgVideoUrl) {
                VideoBackgroundView(url: url)
            } else if let bgImageUrl = sound.bgImageUrl, let url = URL(string: bgImageUrl) {
                ImageBackgroundView(url: url)
            }
            
            LinearGradient(
                colors: sound.chatBgColors,
                startPoint: animateGradient ? .topLeading : .bottomTrailing,
                endPoint: animateGradient ? .bottomTrailing : .topLeading
            )
            .opacity(0.6)
            .animation(
                .easeInOut(duration: 8).repeatForever(autoreverses: true),
                value: animateGradient
            )
            
            FloatingParticlesView()
        }
        .ignoresSafeArea()
        .onAppear {
            animateGradient = true
        }
    }
}

struct FloatingParticlesView: View {
    @State private var particles: [Particle] = []
    
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let now = timeline.date.timeIntervalSinceReferenceDate
                for particle in particles {
                    let x = particle.startX + sin(now * particle.speedX + particle.offset) * particle.amplitude
                    let y = particle.startY - (now * particle.speedY).truncatingRemainder(dividingBy: size.height + 100)
                    let position = CGPoint(x: x, y: y)
                    context.opacity = particle.opacity
                    context.fill(
                        Path(ellipseIn: CGRect(
                            origin: position,
                            size: CGSize(width: particle.size, height: particle.size)
                        )),
                        with: .color(.white)
                    )
                }
            }
        }
        .onAppear {
            particles = (0..<25).map { _ in
                Particle(
                    startX: CGFloat.random(in: 0...UIScreen.main.bounds.width),
                    startY: CGFloat.random(in: 0...UIScreen.main.bounds.height),
                    size: CGFloat.random(in: 2...6),
                    opacity: Double.random(in: 0.1...0.4),
                    speedX: Double.random(in: 0.3...0.8),
                    speedY: Double.random(in: 10...30),
                    amplitude: CGFloat.random(in: 15...40),
                    offset: Double.random(in: 0...Double.pi * 2)
                )
            }
        }
    }
}

struct Particle {
    var startX: CGFloat
    var startY: CGFloat
    var size: CGFloat
    var opacity: Double
    var speedX: Double
    var speedY: Double
    var amplitude: CGFloat
    var offset: Double
}

struct ImageBackgroundView: View {
    let url: URL
    
    var body: some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .scaledToFill()
                    .blur(radius: 20)
            case .failure(_):
                Color.clear
            case .empty:
                Color.clear
            @unknown default:
                Color.clear
            }
        }
    }
}

struct VideoBackgroundView: View {
    let url: URL
    @State private var player: AVPlayer?
    
    var body: some View {
        VideoPlayer(player: player)
            .scaledToFill()
            .blur(radius: 10)
            .onAppear {
                player = AVPlayer(url: url)
                player?.actionAtItemEnd = .none
                player?.play()
                NotificationCenter.default.addObserver(
                    forName: .AVPlayerItemDidPlayToEndTime,
                    object: player?.currentItem,
                    queue: .main
                ) { _ in
                    player?.seek(to: .zero)
                    player?.play()
                }
            }
            .onDisappear {
                player?.pause()
                player = nil
            }
    }
}
