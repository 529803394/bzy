import SwiftUI

struct ChatView: View {
    let sound: Sound
    
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var soundStore: SoundStore
    
    @StateObject private var messageStore = MessageStore.shared
    @State private var inputText = ""
    @State private var showMoreMenu = false
    @State private var isLoading = false
    @State private var scrollProxy: ScrollViewProxy?
    
    @State private var keyboardHeight: CGFloat = 0
    
    var body: some View {
        ZStack {
            ChatBackgroundView(sound: sound)
            
            VStack(spacing: 0) {
                topBar
                messageList
                inputBar
            }
        }
        .sheet(isPresented: $showMoreMenu) {
            MoreMenuSheet(
                sound: sound,
                onGenerateImage: { generateImage() },
                onGenerateVideo: { generateVideo() },
                onShowTasks: { showTasks() },
                onShare: { share() }
            )
        }
        .onAppear {
            setupKeyboardObservers()
        }
        .onDisappear {
            removeKeyboardObservers()
        }
    }
    
    private var topBar: some View {
        HStack {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.title2)
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
            }
            
            Spacer()
            
            Text(sound.name)
                .font(.headline)
                .foregroundStyle(.white)
            
            Spacer()
            
            Button {
                showMoreMenu = true
            } label: {
                Image(systemName: "ellipsis")
                    .font(.title2)
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, 4)
        .background(.ultraThinMaterial.opacity(0.3))
    }
    
    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(messages) { message in
                        MessageBubbleView(message: message)
                            .id(message.id)
                    }
                    
                    if isLoading {
                        HStack {
                            TypingIndicator()
                            Spacer()
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    }
                }
                .padding(.top, 16)
                .padding(.bottom, 8)
            }
            .onAppear {
                scrollProxy = proxy
                scrollToBottom(proxy: proxy)
            }
            .onChange(of: messages.count) { _ in
                scrollToBottom(proxy: proxy)
            }
        }
    }
    
    private var inputBar: some View {
        HStack(spacing: 12) {
            TextField("说点什么...", text: $inputText, axis: .vertical)
                .textFieldStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(
                    Capsule()
                        .fill(.white.opacity(0.9))
                )
                .lineLimit(1...5)
            
            Button {
                sendMessage()
            } label: {
                Image(systemName: "paperplane.fill")
                    .font(.title3)
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        Circle()
                            .fill(inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color.gray.opacity(0.5) : Color.accentColor)
                    )
            }
            .disabled(inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isLoading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial.opacity(0.5))
        .offset(y: -keyboardHeight)
        .animation(.easeOut(duration: 0.25), value: keyboardHeight)
    }
    
    private var messages: [ChatMessage] {
        messageStore.getMessages(for: sound.id)
    }
    
    private func sendMessage() {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        
        inputText = ""
        
        let userMessage = ChatMessage(text: text, fromUser: true)
        messageStore.addMessage(userMessage, for: sound.id)
        soundStore.setLastMessage(id: sound.id, message: text)
        
        isLoading = true
        
        Task {
            do {
                let reply = try await AIService.shared.chat(
                    soundName: sound.name,
                    userText: text,
                    history: messages
                )
                
                let aiMessage = ChatMessage(text: reply, fromUser: false)
                messageStore.addMessage(aiMessage, for: sound.id)
                soundStore.setLastMessage(id: sound.id, message: reply)
            } catch {
                let errorMessage = ChatMessage(text: "抱歉，我遇到了一些问题，请稍后再试。", fromUser: false)
                messageStore.addMessage(errorMessage, for: sound.id)
            }
            
            isLoading = false
        }
    }
    
    private func scrollToBottom(proxy: ScrollViewProxy) {
        guard let lastMessage = messages.last else { return }
        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.2)) {
                proxy.scrollTo(lastMessage.id, anchor: .bottom)
            }
        }
    }
    
    private func setupKeyboardObservers() {
        NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardWillShowNotification,
            object: nil,
            queue: .main
        ) { notification in
            if let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
                keyboardHeight = keyboardFrame.height
            }
        }
        
        NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardWillHideNotification,
            object: nil,
            queue: .main
        ) { _ in
            keyboardHeight = 0
        }
    }
    
    private func removeKeyboardObservers() {
        NotificationCenter.default.removeObserver(self)
    }
    
    private func generateImage() {
        print("智能配图")
    }
    
    private func generateVideo() {
        print("生成视频")
    }
    
    private func showTasks() {
        print("后台任务")
    }
    
    private func share() {
        print("分享")
    }
}

struct TypingIndicator: View {
    @State private var scale: CGFloat = 0
    
    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(.white.opacity(0.7))
                    .frame(width: 8, height: 8)
                    .scaleEffect(scale)
                    .animation(
                        .easeInOut(duration: 0.6)
                        .repeatForever()
                        .delay(Double(index) * 0.2),
                        value: scale
                    )
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.black.opacity(0.25))
        )
        .onAppear {
            scale = 1
        }
    }
}
