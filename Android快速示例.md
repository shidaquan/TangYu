# Android快速调用示例

## 最简单的使用方式

### 1. 在Application或Activity中初始化

```java
import com.example.tangyu.config.DashScopeConfig;
import com.example.tangyu.speech.AsrClient;
import com.example.tangyu.speech.AsrSessionManager;
import com.example.tangyu.speech.AsrSession;

public class MainActivity extends AppCompatActivity {
    private AsrClient asrClient;
    private AsrSessionManager sessionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 直接使用API Key创建配置（对应命令行：DASHSCOPE_API_KEY="sk-xxx"）
        DashScopeConfig config = new DashScopeConfig(
            "sk-dcc82f29f97348deacf9969d27d209bf",  // 你的API Key
            null,  // WebSocket地址（null使用默认值）
            null,  // 模型名称（null使用默认值）
            null   // 语言提示（null使用默认值）
        );
        
        // 创建ASR客户端
        asrClient = new AsrClient(config);
        sessionManager = new AsrSessionManager(asrClient, "pcm", 16000);
    }
}
```

### 2. 开始流式识别（对应命令行：asr-stream pcm 16000）

```java
// 创建会话
String sessionId = "session-" + System.currentTimeMillis();
AsrSession session = sessionManager.getOrCreateSession(sessionId);

// 设置回调
session.onPartialResult(text -> {
    // 部分识别结果
    runOnUiThread(() -> {
        textViewPartial.setText(text);
    });
});

session.onFinalResult(text -> {
    // 最终识别结果
    runOnUiThread(() -> {
        textViewFinal.setText(text);
    });
});

// 开始识别（对应命令行参数：pcm 16000）
session.start("normal");  // 或 "kws" 用于关键词唤醒模式
```

### 3. 发送音频数据

```java
// 从AudioRecord获取音频数据
AudioRecord audioRecord = new AudioRecord(
    MediaRecorder.AudioSource.MIC,
    16000,  // 采样率
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
);

audioRecord.startRecording();

byte[] buffer = new byte[3200];  // 100ms的音频数据
while (isRecording) {
    int read = audioRecord.read(buffer, 0, buffer.length);
    if (read > 0) {
        // 计算RMS（可选）
        double rms = calculateRMS(buffer, read);
        
        // 发送音频数据到ASR会话
        session.addAudio(buffer, rms, "normal");
    }
}
```

### 4. 结束识别

```java
// 结束当前句子识别
session.end();

// 或者停止会话
session.stop();

// 清理会话
sessionManager.removeSession(sessionId);
```

## 完整示例代码

```java
package com.example.myapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.tangyu.config.DashScopeConfig;
import com.example.tangyu.speech.AsrClient;
import com.example.tangyu.speech.AsrSession;
import com.example.tangyu.speech.AsrSessionManager;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    
    private TextView textViewPartial;
    private TextView textViewFinal;
    private Button btnStart;
    private Button btnStop;
    
    private AsrClient asrClient;
    private AsrSessionManager sessionManager;
    private AsrSession currentSession;
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        textViewPartial = findViewById(R.id.textViewPartial);
        textViewFinal = findViewById(R.id.textViewFinal);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        
        // 初始化ASR客户端（对应命令行：DASHSCOPE_API_KEY="sk-xxx"）
        DashScopeConfig config = new DashScopeConfig(
            "sk-dcc82f29f97348deacf9969d27d209bf",  // 你的API Key
            null, null, null  // 使用默认值
        );
        asrClient = new AsrClient(config);
        sessionManager = new AsrSessionManager(asrClient, "pcm", 16000);
        
        // 请求录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
        }
        
        btnStart.setOnClickListener(v -> startRecognition());
        btnStop.setOnClickListener(v -> stopRecognition());
    }
    
    private void startRecognition() {
        if (isRecording) {
            return;
        }
        
        // 创建会话
        String sessionId = "session-" + System.currentTimeMillis();
        currentSession = sessionManager.getOrCreateSession(sessionId);
        
        // 设置回调
        currentSession.onPartialResult(text -> {
            runOnUiThread(() -> {
                if (!text.isEmpty()) {
                    textViewPartial.setText("部分结果: " + text);
                }
            });
        });
        
        currentSession.onFinalResult(text -> {
            runOnUiThread(() -> {
                if (!text.isEmpty()) {
                    textViewFinal.setText("最终结果: " + text);
                }
            });
        });
        
        currentSession.onError(() -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "识别出错", Toast.LENGTH_SHORT).show();
            });
        });
        
        // 开始识别（对应命令行：asr-stream pcm 16000）
        currentSession.start("normal");
        
        // 开始录音
        startRecording();
        
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
    }
    
    private void stopRecognition() {
        isRecording = false;
        
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        
        if (currentSession != null) {
            currentSession.end();
            sessionManager.removeSession(currentSession.getSessionId());
            currentSession = null;
        }
        
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }
    
    private void startRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        
        audioRecord = new AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        );
        
        audioRecord.startRecording();
        isRecording = true;
        
        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0 && currentSession != null) {
                    // 计算RMS
                    double rms = calculateRMS(buffer, read);
                    
                    // 发送音频数据
                    currentSession.addAudio(
                        Arrays.copyOf(buffer, read),
                        rms,
                        "normal"
                    );
                }
            }
        });
        
        recordingThread.start();
    }
    
    private double calculateRMS(byte[] buffer, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sum += sample * sample;
        }
        return Math.sqrt(sum / (length / 2.0));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecognition();
        if (sessionManager != null) {
            sessionManager.clearAll();
        }
    }
}
```

## 对应关系

| 命令行 | Android代码 |
|--------|------------|
| `DASHSCOPE_API_KEY="sk-xxx"` | `new DashScopeConfig("sk-xxx", null, null, null)` |
| `asr-stream pcm 16000` | `session.start("normal")` + `session.addAudio(audioData, rms, "normal")` |
| 音频数据 | `AudioRecord` 读取的PCM数据 |
| 识别结果 | `onPartialResult` 和 `onFinalResult` 回调 |

## 注意事项

1. **API Key安全**：不要将API Key硬编码在代码中，建议：
   - 使用BuildConfig（gradle配置）
   - 从服务器获取
   - 使用加密存储

2. **权限**：需要录音权限和网络权限

3. **线程**：网络操作和音频处理在后台线程，UI更新在主线程

4. **资源清理**：在Activity/Fragment销毁时清理会话和AudioRecord

