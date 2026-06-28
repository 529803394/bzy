import SwiftUI

struct HomeView: View {
    @EnvironmentObject var soundStore: SoundStore
    @EnvironmentObject var audioPlayer: AudioPlayer
    @Environment(\.colorScheme) private var colorScheme
    
    @State private var selectedTab = 0
    @State private var isRefreshing = false
    @State private var recommendation: RecResult?
    
    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                homeContent
                    .navigationTitle("白噪音")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button(action: {
                                print("Settings tapped")
                            }) {
                                Image(systemName: "gearshape.fill")
                                    .foregroundColor(.primary)
                            }
                        }
                    }
            }
            .tabItem {
                Image(systemName: "house.fill")
                Text("首页")
            }
            .tag(0)
            
            NavigationStack {
                LibraryView()
                    .navigationTitle("乐库")
                    .navigationBarTitleDisplayMode(.inline)
            }
            .tabItem {
                Image(systemName: "square.grid.2x2.fill")
                Text("乐库")
            }
            .tag(1)
            
            NavigationStack {
                ProfileView()
                    .navigationTitle("我的")
                    .navigationBarTitleDisplayMode(.inline)
            }
            .tabItem {
                Image(systemName: "person.fill")
                Text("我的")
            }
            .tag(2)
        }
    }
    
    private var homeContent: some View {
        ScrollView {
            LazyVStack(spacing: 20, pinnedViews: []) {
                recommendationSection
                    .padding(.top, 8)
                
                soundListSection
                    .padding(.bottom, 20)
            }
            .padding(.horizontal, 16)
        }
        .refreshable {
            await refreshRecommendation()
        }
        .background(Color(.systemGroupedBackground))
    }
    
    private var recommendationSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("为你推荐")
                    .font(.headline)
                    .fontWeight(.semibold)
                
                Spacer()
            }
            
            if let rec = recommendation {
                RecommendCardView(
                    recResult: rec,
                    soundIndex: soundIndex(for: rec.soundName)
                ) {
                    playRecommendedSound(rec)
                }
            } else {
                placeholderRecommendationCard
            }
        }
    }
    
    private var placeholderRecommendationCard: some View {
        RecommendCardView(
            recResult: RecResult(
                soundName: "雨声",
                shortReason: "舒缓的雨声帮助你放松心情，专注当下",
                detailedReason: "",
                creativeName: "静谧雨巷",
                creativeDesc: "",
                recipe: "",
                recipeReason: ""
            ),
            soundIndex: 0
        )
        .redacted(reason: .placeholder)
    }
    
    private var soundListSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("我的声音")
                    .font(.headline)
                    .fontWeight(.semibold)
                
                Spacer()
            }
            
            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12)
                ],
                spacing: 12
            ) {
                ForEach(soundStore.getHomeList()) { sound in
                    NavigationLink(destination: ChatView(sound: sound)) {
                        SoundCardView(sound: sound)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
    }
    
    private func soundIndex(for soundName: String) -> Int {
        let matchedName = SoundStore.matchBuiltinName(soundName)
        for (i, name) in SoundStore.defaultNames.enumerated() {
            if name == matchedName {
                return i
            }
        }
        return 0
    }
    
    private func playRecommendedSound(_ rec: RecResult) {
        let matchedName = SoundStore.matchBuiltinName(rec.soundName)
        if let sound = soundStore.getHomeList().first(where: { $0.name == matchedName }) {
            audioPlayer.play(sound: sound)
        }
    }
    
    private func refreshRecommendation() async {
        isRefreshing = true
        
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        
        let recommendations = [
            RecResult(
                soundName: "雨声",
                shortReason: "舒缓的雨声帮助你放松心情，专注当下",
                detailedReason: "",
                creativeName: "静谧雨巷",
                creativeDesc: "",
                recipe: "",
                recipeReason: ""
            ),
            RecResult(
                soundName: "海浪",
                shortReason: "海浪声带你回到海边，享受宁静时光",
                detailedReason: "",
                creativeName: "海边晨曦",
                creativeDesc: "",
                recipe: "",
                recipeReason: ""
            ),
            RecResult(
                soundName: "森林",
                shortReason: "森林鸟鸣让你感受大自然的生机与活力",
                detailedReason: "",
                creativeName: "林间小径",
                creativeDesc: "",
                recipe: "",
                recipeReason: ""
            ),
            RecResult(
                soundName: "风声",
                shortReason: "轻柔风声抚平内心焦躁，回归平静",
                detailedReason: "",
                creativeName: "山谷微风",
                creativeDesc: "",
                recipe: "",
                recipeReason: ""
            ),
            RecResult(
                soundName: "篝火",
                shortReason: "温暖篝火声带来安全感，伴你入眠",
                detailedReason: "",
                creativeName: "夜营篝火",
                creativeDesc: "",
                recipe: "",
                recipeReason: ""
            )
        ]
        
        recommendation = recommendations.randomElement()
        isRefreshing = false
    }
}




