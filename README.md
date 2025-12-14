# TangYu 阿里云语音工具

一个用于调用阿里云智能语音交互（NLS）提供的 ASR（语音转写）和 TTS（文本转语音）的简单 Java 项目。项目基于 Maven 构建，提供获取临时 Token、调用 ASR 与 TTS 接口的轻量封装以及示例命令行入口。

## 快速开始

1. 安装 JDK 17+ 与 Maven。
2. 配置以下环境变量（或 JVM 系统属性）：
   - `ALIBABA_CLOUD_ACCESS_KEY_ID`
   - `ALIBABA_CLOUD_ACCESS_KEY_SECRET`
   - `ALIBABA_CLOUD_APP_KEY`（语音控制台的 AppKey）
3. 构建可执行 jar：

   ```bash
   mvn package
   ```

## 命令行示例

构建后通过 `java -jar target/tangyu-aliyun-speech-0.1.0.jar` 调用：

- 语音转写（短语音）：

  ```bash
  java -jar target/tangyu-aliyun-speech-0.1.0.jar asr sample.pcm pcm 16000
  ```

- 文本转语音：

  ```bash
  java -jar target/tangyu-aliyun-speech-0.1.0.jar tts "你好，欢迎使用阿里云语音服务" xiaoyun wav 16000 output.wav
  ```

## 主要模块

- `CredentialConfig`：从环境/系统属性读取并校验 AccessKey 与 AppKey。
- `TokenClient`：使用阿里云 SDK 获取并缓存临时 Token（默认 30 分钟，预留 60 秒更新缓冲）。
- `AsrClient`：通过 `https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/asr` 发送音频流，返回原始 JSON 结果。
- `TtsClient`：通过 `https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts` 生成音频并保存到文件，接口出错时返回 JSON 会自动抛出异常。
- `DemoMain`：简单 CLI，分别支持 `asr` 与 `tts` 子命令。

## 注意事项

- 代码未包含重试与长连接优化，适用于快速演示/工具场景，可按需扩展。
- ASR 需确保音频格式与采样率与传参一致（例如 PCM 16k）。
- TTS 音色（`voice`）请参考阿里云语音控制台支持的具体值。
