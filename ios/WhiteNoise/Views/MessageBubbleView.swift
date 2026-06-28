import SwiftUI

struct MessageBubbleView: View {
    let message: ChatMessage
    
    var body: some View {
        HStack {
            if message.fromUser {
                Spacer()
            }
            
            Text(message.text)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(bubbleColor)
                )
                .foregroundStyle(textColor)
                .frame(maxWidth: 280, alignment: message.fromUser ? .trailing : .leading)
            
            if !message.fromUser {
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }
    
    private var bubbleColor: Color {
        if message.fromUser {
            return Color.white.opacity(0.9)
        } else {
            return Color.black.opacity(0.25)
        }
    }
    
    private var textColor: Color {
        if message.fromUser {
            return .primary
        } else {
            return .white
        }
    }
}
