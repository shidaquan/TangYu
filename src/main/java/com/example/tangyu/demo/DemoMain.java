package com.example.tangyu.demo;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.example.tangyu.config.CredentialConfig;
import com.example.tangyu.config.DashScopeConfig;
import com.example.tangyu.config.TtsConfig;
import com.example.tangyu.robot.RobotClient;
import com.example.tangyu.robot.RobotConfig;
import com.example.tangyu.server.PcmHttpServer;
import com.example.tangyu.speech.AsrClient;
import com.example.tangyu.speech.AsrResultHandler;
import com.example.tangyu.speech.TokenClient;
import com.example.tangyu.speech.TtsClient;
import com.example.tangyu.speech.TtsRealtimeClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sound.sampled.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;

/**
 * 精简的命令行入口，提供 ASR/TTS 的调用示例。
 *
 * 使用示例：
 *  ASR（同步）：java -jar target/tangyu-aliyun-speech-0.1.0.jar asr sample.pcm pcm 16000
 *  ASR（流式）：java -jar target/tangyu-aliyun-speech-0.1.0.jar asr-stream pcm 16000
 *  TTS：java -jar target/tangyu-aliyun-speech-0.1.0.jar tts "你好" xiaoyun wav 16000 output.wav
 *  （ASR 依赖 DASHSCOPE_API_KEY；TTS 依赖 AccessKey + AppKey）
 */
public class DemoMain {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static RobotClient robotClient;
    private static final int[] FALLBACK_SAMPLE_RATES = new int[] {44100, 16000};

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(1);
        }

        switch (args[0]) {
            case "asr" -> runAsr(args);
            case "asr-stream" -> runAsrStream(args);
            case "tts" -> runTts(args);
            case "tts-realtime" -> runTtsRealtime(args);
            case "serve" -> runServer(args);
            case "serve-http" -> runHttpServer(args);
            default -> {
                printHelp();
                System.exit(1);
            }
        }
    }

    private static void runAsr(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: asr <audioPath> <format> <sampleRate> [token]");
            System.exit(1);
        }
        DashScopeConfig dashScopeConfig = DashScopeConfig.fromEnvironment();
        Path audio = Path.of(args[1]);
        String format = args[2];
        int sampleRate = Integer.parseInt(args[3]);
        String token = args.length > 4 ? args[4] : getTokenWithDefault();
        AsrClient client = new AsrClient(dashScopeConfig);
        String response = transcribeWithFallback(client, audio, format, sampleRate);
        System.out.println("完整识别结果:");
        System.out.println(response);

        String text = extractText(response);
        String robotReply = callRobot(text, token);
        if (robotReply != null) {
            System.out.println("大模型回复:");
            System.out.println(robotReply);
            streamTtsReply(robotReply);
        } else {
            System.err.println("大模型调用失败或未返回内容");
        }
    }

    private static void runAsrStream(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: asr-stream <format> <sampleRate> [token]");
            System.err.println("Example: asr-stream pcm 16000 my-token");
            System.exit(1);
        }
        String format = args[1];
        int sampleRate = Integer.parseInt(args[2]);
        String token = args.length > 3 ? args[3] : getTokenWithDefault();

        DashScopeConfig dashScopeConfig = DashScopeConfig.fromEnvironment();
        AsrClient client = new AsrClient(dashScopeConfig);
        AsrResultHandler handler = new AsrResultHandler();
        handler.setOnFinalResult(text -> {
            System.out.println("最终识别文本: " + text);
            String reply = callRobot(text, token);
            if (reply != null) {
                System.out.println("大模型回复:");
                System.out.println(reply);
            }
        });

        Recognition recognition = null;
        try {
            recognition = client.startStreaming(format, sampleRate, handler);

            // 从麦克风获取音频并实时发送
            AudioFormat audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
            TargetDataLine microphone = AudioSystem.getTargetDataLine(audioFormat);
            microphone.open(audioFormat);
            microphone.start();

            System.out.println("开始录音，按 Ctrl+C 停止...");
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = microphone.read(buffer, 0, buffer.length)) != -1) {
                if (bytesRead > 0) {
                    client.sendAudioFrame(recognition, buffer, 0, bytesRead);
                }
                if (handler.isCompleted()) {
                    break;
                }
            }

            microphone.stop();
            microphone.close();
        } catch (LineUnavailableException e) {
            System.err.println("无法访问麦克风: " + e.getMessage());
            System.err.println("提示: 请确保麦克风已连接并授予应用权限");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("流式识别错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (recognition != null) {
                client.stopStreaming(recognition);
            }
        }

        System.out.println("\n最终识别文本: " + handler.getFullText());
        String robotReply = callRobot(handler.getFullText(), token);
        if (robotReply != null) {
            System.out.println("大模型回复:");
            System.out.println(robotReply);
            streamTtsReply(robotReply);
        } else {
            System.err.println("大模型调用失败或未返回内容");
        }
    }

    private static void runTts(String[] args) {
        if (args.length < 6) {
            System.err.println("Usage: tts <text> <voice> <format> <sampleRate> <outputFile>");
            System.exit(1);
        }
        CredentialConfig config = CredentialConfig.fromEnvironment();
        TokenClient tokenClient = new TokenClient(config);
        String text = args[1];
        String voice = args[2];
        String format = args[3];
        int sampleRate = Integer.parseInt(args[4]);
        Path output = Path.of(args[5]);
        TtsClient client = new TtsClient(config, tokenClient);
        client.synthesizeToFile(text, voice, format, sampleRate, output);
        System.out.printf("Audio written to %s%n", output.toAbsolutePath());
    }

    private static void runTtsRealtime(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: tts-realtime <text> <outputFile> [voice]");
            System.err.println("Example: tts-realtime \"你好，欢迎使用实时语音合成\" output.pcm");
            System.exit(1);
        }
        TtsConfig ttsConfig = TtsConfig.fromEnvironment();
        String text = args[1];
        Path output = Path.of(args[2]);
        String voice = args.length > 3 ? args[3] : null;
        
        TtsRealtimeClient client = new TtsRealtimeClient(ttsConfig);
        client.synthesizeToFile(text, voice, output);
        System.out.printf("Audio written to %s%n", output.toAbsolutePath());
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  asr <audioPath> <format> <sampleRate> [token]");
        System.out.println("    同步识别音频文件");
        System.out.println("    Example: asr sample.pcm pcm 16000 my-token");
        System.out.println();
        System.out.println("  asr-stream <format> <sampleRate> [token]");
        System.out.println("    流式识别（从麦克风实时识别）");
        System.out.println("    Example: asr-stream pcm 16000 my-token");
        System.out.println();
        System.out.println("  tts <text> <voice> <format> <sampleRate> <outputFile>");
        System.out.println("    文本转语音（旧版NLS接口）");
        System.out.println("    Example: tts \"你好\" xiaoyun wav 16000 output.wav");
        System.out.println();
        System.out.println("  tts-realtime <text> <outputFile> [voice]");
        System.out.println("    实时文本转语音（DashScope Qwen TTS Realtime）");
        System.out.println("    Example: tts-realtime \"你好\" output.pcm");
        System.out.println();
        System.out.println("  serve [port]");
        System.out.println("    启动 HTTP 接口服务 (默认端口 8080)");
        System.out.println("    Example: serve 8080");
        System.out.println();
        System.out.println("  serve-http [port] [dir]");
        System.out.println("    启动极简 HTTP 文件服务，默认端口8080，目录为系统临时目录");
        System.out.println("    Example: serve-http 8080 /tmp");
        System.out.println();
        System.out.println("ASR uses DashScope API Key (Fun-ASR):");
        System.out.println("  DASHSCOPE_API_KEY (required)");
        System.out.println("  DASHSCOPE_BASE_URL (optional, default wss://dashscope.aliyuncs.com/api-ws/v1/inference)");
        System.out.println("  DASHSCOPE_ASR_MODEL (optional, default fun-asr-realtime)");
        System.out.println("  DASHSCOPE_ASR_LANGUAGE_HINTS (optional, comma separated, default zh,en)");
        System.out.println();
        System.out.println("TTS Realtime uses DashScope API Key:");
        System.out.println("  DASHSCOPE_API_KEY (required)");
        System.out.println("  DASHSCOPE_TTS_BASE_URL (optional, default wss://dashscope.aliyuncs.com/api-ws/v1/inference)");
        System.out.println("  DASHSCOPE_TTS_MODEL (optional, default qwen3-tts-flash-realtime-2025-11-27)");
        System.out.println("  DASHSCOPE_TTS_VOICE (optional, default zh-CN-XiaoyiNeural)");
        System.out.println("  DASHSCOPE_TTS_SAMPLE_RATE (optional, default 24000)");
        System.out.println("  DASHSCOPE_TTS_FORMAT (optional, default pcm)");
        System.out.println();
        System.out.println("TTS (旧版) uses AccessKey/AppKey + temporary token:");
        System.out.println("  ALIBABA_CLOUD_ACCESS_KEY_ID (required)");
        System.out.println("  ALIBABA_CLOUD_ACCESS_KEY_SECRET (required)");
        System.out.println("  ALIBABA_CLOUD_APP_KEY (required)");
        System.out.println();
        System.out.println("Robot (可选，未提供则跳过大模型调用):");
        System.out.println("  ROBOT_WS_URL");
        System.out.println("  ROBOT_PERSONA_ID");
        System.out.println("  ROBOT_SCENE");
        System.out.println("  ROBOT_INPUT_TYPE");
        System.out.println("  ROBOT_TOKEN (可作为 token 默认值)");
    }

    private static void runServer(String[] args) {
        int port = args.length > 1
                ? Integer.parseInt(args[1])
                : Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
        SpeechApiServer server = new SpeechApiServer(port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.printf("Speech API server is running on port %d%n", port);
        System.out.println("Health: GET  /health");
        System.out.println("ASR:    POST /api/asr?format=pcm&sampleRate=16000 (body: audio bytes)");
        System.out.println("TTS:    POST /api/tts (body: {\"text\":\"你好\",\"voice\":\"xiaoyun\",\"format\":\"wav\",\"sampleRate\":16000})");
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String extractText(String response) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(response);
            if (node.hasNonNull("text")) {
                return node.get("text").asText();
            }
        } catch (Exception ignore) {
        }
        return response;
    }

    /**
     * 同步识别，若主采样率无结果则尝试备用采样率（44100、16000）。
     */
    private static String transcribeWithFallback(AsrClient client, Path audio, String format, int primaryRate) {
        String response = client.transcribe(audio, format, primaryRate);
        String text = extractText(response);
        if (text != null && !text.isBlank()) {
            return response;
        }
        for (int rate : FALLBACK_SAMPLE_RATES) {
            if (rate == primaryRate) {
                continue;
            }
            try {
                String resp = client.transcribe(audio, format, rate);
                String txt = extractText(resp);
                if (txt != null && !txt.isBlank()) {
                    System.out.printf("使用备用采样率 %d 取得结果%n", rate);
                    return resp;
                }
            } catch (Exception e) {
                System.err.printf("备用采样率 %d 识别失败: %s%n", rate, e.getMessage());
            }
        }
        return response;
    }

    private static String getTokenFromEnv() {
        String token = System.getenv("ROBOT_TOKEN");
        if (token == null || token.isBlank()) {
            token = System.getProperty("robot.token");
        }
        return token;
    }

    /**
    * 获取 token，若为空则使用默认值。
    */
    private static String getTokenWithDefault() {
        String token = getTokenFromEnv();
        if (token == null || token.isBlank()) {
            System.err.println("未提供 token，使用默认值 demo-token");
            return "demo-token";
        }
        return token;
    }

    private static synchronized RobotClient robotClient() {
        if (robotClient == null) {
            robotClient = new RobotClient(RobotConfig.fromSystemEnv());
        }
        return robotClient;
    }

    private static String callRobot(String text, String token) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (token == null || token.isBlank()) {
            System.out.println("未提供 token，跳过大模型调用");
            return null;
        }
        return robotClient().sendAndReceive(text, token);
    }

    /**
     * 将大模型回复通过实时 TTS 合成为临时 pcm 文件。
     */
    private static void streamTtsReply(String reply) {
        try {
            TtsRealtimeClient client = new TtsRealtimeClient(TtsConfig.fromEnvironment());
            Files.createDirectories(PCM_OUTPUT_DIR);
            String fileName = "reply-" + System.currentTimeMillis() + ".pcm";
            Path out = PCM_OUTPUT_DIR.resolve(fileName);
            client.synthesizeToFile(reply, out);
            System.out.printf("大模型回复已转语音（pcm）：%s%n", out.toAbsolutePath());
            System.out.println("前端可从该目录读取文件，或通过自定义文件服务对外提供访问");
        } catch (Exception e) {
            System.err.println("TTS 转换失败: " + e.getMessage());
        }
    }

    private static void runHttpServer(String[] args) {
        int port = args.length > 1
                ? Integer.parseInt(args[1])
                : 8080;
        Path baseDir = args.length > 2 ? Path.of(args[2]) : Path.of(System.getProperty("java.io.tmpdir"));
        PcmHttpServer server = new PcmHttpServer(port, baseDir);
        server.start();
        System.out.printf("HTTP 文件服务已启动: GET http://localhost:%d/pcm?name=xxx  (目录=%s)%n", port, baseDir.toAbsolutePath());
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
