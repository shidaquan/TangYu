# TangYu 阿里云语音工具

一个用于调用阿里云智能语音交互（NLS）提供的 ASR（语音转写）和 TTS（文本转语音）的 Java SDK。项目基于 Maven 构建，提供获取临时 Token、调用 ASR 与 TTS 接口的轻量封装，支持同步识别、流式识别、KWS模式等功能。

## 📚 完整文档

**详细使用文档请查看：[完整使用文档.md](./完整使用文档.md)**

文档包含：
- ✅ Java应用集成（Maven/Gradle）
- ✅ Android服务调用（完整示例）
- ✅ Spring Boot集成
- ✅ ASR功能详解（会话管理、KWS模式、文本去重）
- ✅ 配置说明和常见问题

## 快速开始

1. 安装 JDK 17+ 与 Maven
2. 配置环境变量：
   ```bash
   export DASHSCOPE_API_KEY="your-api-key"
   ```
3. 构建项目：
   ```bash
   mvn clean package
   ```

## 命令行示例

```bash
# 同步识别音频文件
java -jar target/tangyu-aliyun-speech-0.1.0-executable.jar asr sample.pcm pcm 16000

# 流式识别（从麦克风）
java -jar target/tangyu-aliyun-speech-0.1.0-executable.jar asr-stream pcm 16000

# 文本转语音
java -jar target/tangyu-aliyun-speech-0.1.0-executable.jar tts "你好" xiaoyun wav 16000 output.wav
```

## 主要功能

- ✅ **ASR语音识别**
  - 同步识别（音频文件）
  - 流式识别（实时音频流）
  - KWS模式（关键词唤醒，快速响应）
  - 文本去重处理
  - 会话管理（多会话支持）

- ✅ **TTS文本转语音**
  - 支持多种音色
  - 多种音频格式输出

- ✅ **多平台支持**
  - Java应用
  - Android应用
  - Spring Boot项目

## 主要模块

- `AsrClient` - ASR客户端（同步/流式识别）
- `AsrSession` - ASR会话管理（支持KWS模式和正常模式）
- `AsrSessionManager` - 多会话管理器
- `AsrResultHandler` - 识别结果处理器（支持部分/最终结果）
- `TextDeduplicator` - 文本去重处理器
- `TtsClient` - TTS客户端
- `TokenClient` - Token管理（自动缓存和刷新）
- `DashScopeConfig` - DashScope配置管理
- `CredentialConfig` - 阿里云凭证配置

## 快速集成

### Maven
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>tangyu-aliyun-speech</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle
```gradle
implementation 'com.example:tangyu-aliyun-speech:0.1.0'
```

### Android
详细说明请查看 [完整使用文档.md](./完整使用文档.md) 的 Android服务调用章节。

## 注意事项

- 需要 JDK 17 或更高版本
- 需要网络连接访问阿里云服务
- Android 需要网络权限和录音权限（如使用麦克风）
- 详细配置说明请查看完整文档

## 参考文档

- [完整使用文档.md](./完整使用文档.md) - 完整的使用说明和示例
- 阿里云官方文档：https://help.aliyun.com/zh/model-studio/fun-asr-realtime-java-sdk
