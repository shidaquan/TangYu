# TTS 优化说明

参照阿里云通义千问实时语音合成（Qwen TTS Realtime）文档，优化了TTS代码。

## 新增功能

### 1. **TtsConfig 配置类**

支持从环境变量或系统属性读取TTS配置：

- `DASHSCOPE_API_KEY` - DashScope API密钥（必需）
- `DASHSCOPE_TTS_BASE_URL` - WebSocket地址（可选，默认：`wss://dashscope.aliyuncs.com/api-ws/v1/inference`）
- `DASHSCOPE_TTS_MODEL` - 模型名称（可选，默认：`qwen3-tts-flash-realtime-2025-11-27`）
- `DASHSCOPE_TTS_VOICE` - 音色（可选，默认：`zh-CN-XiaoyiNeural`）
- `DASHSCOPE_TTS_SAMPLE_RATE` - 采样率（可选，默认：`24000`）
- `DASHSCOPE_TTS_FORMAT` - 音频格式（可选，默认：`pcm`）

### 2. **TtsRealtimeClient 实时TTS客户端**

基于DashScope Qwen TTS Realtime WebSocket API实现，支持：

- **同步合成**：将文本合成为音频文件
- **流式合成**：实时接收音频数据流
- **低延迟**：使用WebSocket实现实时交互
- **多种音色**：支持多种语音模型和音色

### 3. **保留原有TtsClient**

原有的基于NLS接口的`TtsClient`保持不变，确保向后兼容。

## 使用方式

### 方式1：同步合成到文件

```java
import com.example.tangyu.config.TtsConfig;
import com.example.tangyu.speech.TtsRealtimeClient;
import java.nio.file.Paths;

// 从环境变量读取配置
TtsConfig config = TtsConfig.fromEnvironment();

// 创建客户端
TtsRealtimeClient client = new TtsRealtimeClient(config);

// 合成文本到文件
Path outputFile = Paths.get("output.pcm");
client.synthesizeToFile("你好，欢迎使用实时语音合成", outputFile);

// 使用自定义音色
client.synthesizeToFile("你好", "zh-CN-XiaoyiNeural", outputFile);
```

### 方式2：流式合成

```java
import com.example.tangyu.config.TtsConfig;
import com.example.tangyu.speech.TtsRealtimeClient;
import okhttp3.WebSocket;

// 创建客户端
TtsRealtimeClient client = new TtsRealtimeClient(TtsConfig.fromEnvironment());

// 启动流式合成
WebSocket webSocket = client.startStreaming(
    audioData -> {
        // 处理音频数据块
        playAudio(audioData);
    },
    "zh-CN-XiaoyiNeural",  // 音色（可选）
    () -> System.err.println("错误"),  // 错误回调（可选）
    () -> System.out.println("完成")   // 完成回调（可选）
);

// 发送文本（需要通过WebSocket发送）
// 注意：实际使用中需要实现WebSocket消息发送逻辑
```

### 方式3：命令行使用

```bash
# 使用实时TTS合成
DASHSCOPE_API_KEY="your-api-key" java -jar tangyu-aliyun-speech-0.1.0-executable.jar \
    tts-realtime "你好，欢迎使用实时语音合成" output.pcm

# 指定音色
DASHSCOPE_API_KEY="your-api-key" java -jar tangyu-aliyun-speech-0.1.0-executable.jar \
    tts-realtime "你好" output.pcm zh-CN-XiaoyiNeural
```

## 与原有TTS的对比

| 特性 | TtsClient (旧版) | TtsRealtimeClient (新版) |
|------|-----------------|------------------------|
| API接口 | NLS HTTP接口 | DashScope WebSocket接口 |
| 延迟 | 较高 | 低（实时流式） |
| 配置 | AccessKey + AppKey | DashScope API Key |
| 音色支持 | 有限 | 更多选择 |
| 流式支持 | 否 | 是 |
| 模型 | NLS模型 | Qwen TTS Realtime模型 |

## 配置示例

### 环境变量配置

```bash
# 必需
export DASHSCOPE_API_KEY="sk-your-api-key"

# 可选配置
export DASHSCOPE_TTS_BASE_URL="wss://dashscope.aliyuncs.com/api-ws/v1/inference"
export DASHSCOPE_TTS_MODEL="qwen3-tts-flash-realtime-2025-11-27"
export DASHSCOPE_TTS_VOICE="zh-CN-XiaoyiNeural"
export DASHSCOPE_TTS_SAMPLE_RATE="24000"
export DASHSCOPE_TTS_FORMAT="pcm"
```

### 代码配置

```java
// 直接创建配置对象
TtsConfig config = new TtsConfig(
    "sk-your-api-key",           // API Key
    null,                         // WebSocket地址（使用默认值）
    null,                         // 模型（使用默认值）
    "zh-CN-XiaoyiNeural",        // 音色
    24000,                        // 采样率
    "pcm"                         // 格式
);

TtsRealtimeClient client = new TtsRealtimeClient(config);
```

## Android使用示例

```kotlin
import com.example.tangyu.config.TtsConfig
import com.example.tangyu.speech.TtsRealtimeClient
import java.nio.file.Paths

// 创建配置（Android中不能使用环境变量）
val config = TtsConfig(
    "sk-your-api-key",
    null, null, null, 24000, "pcm"
)

// 创建客户端
val client = TtsRealtimeClient(config)

// 合成文本
val outputFile = Paths.get(context.filesDir, "output.pcm")
client.synthesizeToFile("你好，世界", outputFile)
```

## 注意事项

1. **API Key**：需要使用DashScope API Key，不是AccessKey
2. **音频格式**：默认输出PCM格式，采样率24000Hz
3. **WebSocket连接**：实时TTS使用WebSocket，需要保持连接
4. **错误处理**：建议实现错误回调处理网络异常
5. **资源清理**：使用完毕后记得关闭WebSocket连接

## 参考文档

- 阿里云文档：https://help.aliyun.com/zh/model-studio/qwen-tts-realtime-java-sdk
- DashScope API文档：https://help.aliyun.com/zh/model-studio/

## 文件说明

- `TtsConfig.java` - TTS配置类
- `TtsRealtimeClient.java` - 实时TTS客户端（新版）
- `TtsClient.java` - TTS客户端（旧版，保持兼容）

