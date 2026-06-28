import SwiftUI

struct RecommendCardView: View {
    let recResult: RecResult
    let soundIndex: Int
    var onListen: (() -> Void)? = nil
    
    @Environment(\.colorScheme) private var colorScheme
    
    var body: some View {
        Button(action: {
            onListen?()
        }) {
            cardContent
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var cardContent: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 20)
                .fill(
                    LinearGradient(
                        colors: ThemeColors.gradient(for: soundIndex, colorScheme: colorScheme),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: "sparkles")
                        .font(.title2)
                        .foregroundColor(.white.opacity(0.9))
                    
                    Text("智能推荐")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.white.opacity(0.9))
                    
                    Spacer()
                }
                
                Spacer()
                
                VStack(alignment: .leading, spacing: 6) {
                    Text(recResult.creativeName.isEmpty ? recResult.soundName : recResult.creativeName)
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text(recResult.shortReason)
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.85))
                        .lineLimit(2)
                }
                
                Spacer()
                
                HStack {
                    Spacer()
                    
                    Button(action: {
                        onListen?()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 14, weight: .semibold))
                            
                            Text("立即听")
                                .font(.subheadline)
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.primary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(.white.opacity(0.9))
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.15), radius: 4, x: 0, y: 2)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(20)
        }
        .frame(height: 180)
        .shadow(color: Color.black.opacity(0.2), radius: 12, x: 0, y: 6)
    }
}
