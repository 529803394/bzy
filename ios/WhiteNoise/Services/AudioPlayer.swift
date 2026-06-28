import Foundation
import AVFoundation
import Combine

class AudioPlayer: NSObject, ObservableObject {
    static let shared = AudioPlayer()
    
    @Published var isPlaying: Bool = false
    @Published var currentSoundId: String? = nil
    @Published var volume: Float = 0.7
    @Published var isLooping: Bool = true
    
    private var audioPlayer: AVAudioPlayer?
    private var audioSession: AVAudioSession = AVAudioSession.sharedInstance()
    
    private let builtInSoundFiles: [String: String] = [
        "rain": "rain.mp3",
        "waves": "ocean.mp3",
        "forest": "forest.mp3",
        "wind": "wind.mp3",
        "fire": "campfire.mp3"
    ]
    
    private override init() {
        super.init()
        setupAudioSession()
        setupNotificationObservers()
    }
    
    private func setupAudioSession() {
        do {
            try audioSession.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try audioSession.setActive(true)
        } catch {
            print("Audio session setup failed: \(error.localizedDescription)")
        }
    }
    
    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioInterruption),
            name: AVAudioSession.interruptionNotification,
            object: audioSession
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleRouteChange),
            name: AVAudioSession.routeChangeNotification,
            object: audioSession
        )
    }
    
    @objc private func handleAudioInterruption(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let interruptionType = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let interruptionTypeValue = AVAudioSession.InterruptionType(rawValue: interruptionType) else {
            return
        }
        
        switch interruptionTypeValue {
        case .began:
            if isPlaying {
                pause()
            }
        case .ended:
            if let options = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt {
                if AVAudioSession.InterruptionOptions(rawValue: options).contains(.shouldResume) {
                    resume()
                }
            }
        default:
            break
        }
    }
    
    @objc private func handleRouteChange(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }
        
        switch reason {
        case .oldDeviceUnavailable:
            if isPlaying {
                pause()
            }
        default:
            break
        }
    }
    
    func play(sound: Sound) {
        stop()
        
        var fileName: String?
        
        if sound.isBuiltIn {
            fileName = builtInSoundFiles[sound.id]
        } else if let localPath = sound.localPath {
            fileName = localPath
        }
        
        guard let soundFileName = fileName else {
            print("Sound file not found for sound: \(sound.id)")
            return
        }
        
        let fileURL: URL?
        
        if sound.isBuiltIn {
            let name = (soundFileName as NSString).deletingPathExtension
            let ext = (soundFileName as NSString).pathExtension
            fileURL = Bundle.main.url(forResource: name, withExtension: ext)
        } else if let localPath = sound.localPath {
            let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
            let fullPath = (documentsPath as NSString).appendingPathComponent(localPath)
            fileURL = URL(fileURLWithPath: fullPath)
        } else {
            fileURL = nil
        }
        
        guard let url = fileURL else {
            print("Sound file URL not found: \(soundFileName)")
            return
        }
        
        do {
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.delegate = self
            audioPlayer?.numberOfLoops = isLooping ? -1 : 0
            audioPlayer?.volume = volume
            audioPlayer?.prepareToPlay()
            audioPlayer?.play()
            
            isPlaying = true
            currentSoundId = sound.id
        } catch {
            print("Failed to play sound: \(error.localizedDescription)")
            isPlaying = false
            currentSoundId = nil
        }
    }
    
    func pause() {
        guard let player = audioPlayer, player.isPlaying else { return }
        player.pause()
        isPlaying = false
    }
    
    func resume() {
        guard let player = audioPlayer, !player.isPlaying else { return }
        player.play()
        isPlaying = true
    }
    
    func stop() {
        audioPlayer?.stop()
        audioPlayer = nil
        isPlaying = false
        currentSoundId = nil
    }
    
    func setVolume(_ volume: Float) {
        let clampedVolume = max(0.0, min(1.0, volume))
        self.volume = clampedVolume
        audioPlayer?.volume = clampedVolume
    }
    
    func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            resume()
        }
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}

extension AudioPlayer: AVAudioPlayerDelegate {
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        if !isLooping {
            isPlaying = false
            currentSoundId = nil
        }
    }
    
    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        if let error = error {
            print("Audio player decode error: \(error.localizedDescription)")
        }
        isPlaying = false
        currentSoundId = nil
    }
}
