# 白噪音 iOS 版

专为白噪音爱好者设计的沉浸式助眠与专注伴侣。

## 功能特性

### 核心功能
- 🌧️ **5种经典白噪音**：雨声、海浪、森林、风声、篝火
- 💬 **AI 对话陪伴**：和你喜欢的声音"聊天"，温柔回应
- 🎨 **智能配图**：AI 生成专属背景图
- 🎬 **背景视频**：图片转动态视频，让背景"动起来"
- 📋 **后台任务**：任务列表管理，支持应用/刷新/删除

### 播放功能
- 🔊 音量调节
- ⏱️ 定时关闭
- 🔄 循环播放
- 🎧 后台播放支持

### 界面体验
- 🌓 深色/浅色模式自动切换
- 🎨 每种声音独特主题色
- ✨ 精致渐变背景动画
- 📱 支持 iPhone 和 iPad

## 项目结构

```
ios/
├── WhiteNoise.xcodeproj/     # Xcode 项目文件
└── WhiteNoise/
    ├── WhiteNoiseApp.swift    # App 入口
    ├── WhiteNoise-Bridging-Header.h  # 桥接头文件
    ├── Info.plist             # 应用配置
    ├── Assets.xcassets/       # 资源文件
    ├── Stores/                # 数据存储层
    │   ├── SoundStore.swift   # 声音数据管理
    │   ├── TaskStore.swift    # 后台任务管理
    │   └── MessageStore.swift # 聊天消息管理
    ├── Services/              # 业务服务层
    │   ├── AIService.swift    # AI API 服务（DeepSeek + 智谱）
    │   └── AudioPlayer.swift  # 音频播放器
    └── Views/                 # 界面视图层
        ├── HomeView.swift     # 首页
        ├── ChatView.swift     # 聊天页面
        ├── ChatBackgroundView.swift  # 聊天背景动画
        ├── MessageBubbleView.swift   # 消息气泡
        ├── MoreMenuSheet.swift       # 更多菜单
        ├── SoundCardView.swift       # 声音卡片
        ├── RecommendCardView.swift   # 推荐卡片
        ├── TaskListView.swift        # 后台任务列表
        ├── ImageGeneratorView.swift  # 智能配图
        ├── VideoGeneratorView.swift  # 视频生成
        ├── LibraryView.swift         # 乐库页面
        ├── ProfileView.swift         # 个人中心
        ├── SettingsView.swift        # 设置页面
        ├── TimerView.swift           # 定时关闭
        └── ThemeColors.swift         # 主题颜色定义
```

## 快速开始

### 环境要求
- Xcode 15.0+
- iOS 16.0+
- Swift 5.0+

### 运行步骤

1. 用 Xcode 打开 `WhiteNoise.xcodeproj`
2. 选择你的开发团队（Signing & Capabilities）
3. 选择模拟器或真机
4. 按 `Cmd + R` 运行

### 添加音频文件

项目需要以下音频文件才能正常播放（放入 `WhiteNoise/` 目录并添加到 Xcode 项目）：

- `rain.mp3` - 雨声
- `ocean.mp3` - 海浪
- `forest.mp3` - 森林
- `wind.mp3` - 风声
- `campfire.mp3` - 篝火

## 技术栈

- **SwiftUI** - 界面框架
- **AVFoundation** - 音频播放
- **URLSession** - 网络请求
- **UserDefaults** - 数据持久化
- **DeepSeek API** - AI 聊天和推荐
- **智谱 AI API** - 图像和视频生成

## 数据持久化

所有数据自动保存到 UserDefaults：
- 声音列表及自定义配置
- 聊天消息历史（每个声音独立存储）
- 后台任务列表
- 用户设置（音量、主题、定时等）

## 版本

当前版本：2.23.29
