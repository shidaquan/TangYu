# Android Kotlin 调用示例

Kotlin 可以完全兼容 Java 库，直接调用即可。以下是 Kotlin 版本的完整示例。

## 最简单的使用方式

### 1. 在Activity中初始化

```kotlin
import com.example.tangyu.config.DashScopeConfig
import com.example.tangyu.speech.AsrClient
import com.example.tangyu.speech.AsrSessionManager
import com.example.tangyu.speech.AsrSession

class MainActivity : AppCompatActivity() {
    private lateinit var asrClient: AsrClient
    private lateinit var sessionManager: AsrSessionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 直接使用API Key创建配置（对应命令行：DASHSCOPE_API_KEY="sk-xxx"）
        val config = DashScopeConfig(
            "sk-dcc82f29f97348deacf9969d27d209bf",  // 你的API Key
            null,  // WebSocket地址（null使用默认值）
            null,  // 模型名称（null使用默认值）
            null   // 语言提示（null使用默认值）
        )
        
        // 创建ASR客户端
        asrClient = AsrClient(config)
        sessionManager = AsrSessionManager(asrClient, "pcm", 16000)
    }
}
```

### 2. 开始流式识别（对应命令行：asr-stream pcm 16000）

```kotlin
// 创建会话
val sessionId = "session-${System.currentTimeMillis()}"
val session = sessionManager.getOrCreateSession(sessionId)

// 设置回调（Kotlin的lambda语法更简洁）
session.onPartialResult { text ->
    // 部分识别结果
    runOnUiThread {
        textViewPartial.text = text
    }
}

session.onFinalResult { text ->
    // 最终识别结果
    runOnUiThread {
        textViewFinal.text = text
    }
}

session.onError {
    runOnUiThread {
        Toast.makeText(this, "识别出错", Toast.LENGTH_SHORT).show()
    }
}

// 开始识别（对应命令行参数：pcm 16000）
session.start("normal")  // 或 "kws" 用于关键词唤醒模式
```

### 3. 发送音频数据

```kotlin
// 从AudioRecord获取音频数据
val audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,
    16000,  // 采样率
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
)

audioRecord.startRecording()

val buffer = ByteArray(3200)  // 100ms的音频数据
while (isRecording) {
    val read = audioRecord.read(buffer, 0, buffer.size)
    if (read > 0) {
        // 计算RMS（可选）
        val rms = calculateRMS(buffer, read)
        
        // 发送音频数据到ASR会话
        session.addAudio(buffer.copyOf(read), rms, "normal")
    }
}
```

## 完整示例代码（Kotlin）

```kotlin
package com.example.myapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tangyu.config.DashScopeConfig
import com.example.tangyu.speech.AsrClient
import com.example.tangyu.speech.AsrSession
import com.example.tangyu.speech.AsrSessionManager

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    private lateinit var textViewPartial: TextView
    private lateinit var textViewFinal: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    
    private lateinit var asrClient: AsrClient
    private lateinit var sessionManager: AsrSessionManager
    private var currentSession: AsrSession? = null
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        textViewPartial = findViewById(R.id.textViewPartial)
        textViewFinal = findViewById(R.id.textViewFinal)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        
        // 初始化ASR客户端（对应命令行：DASHSCOPE_API_KEY="sk-xxx"）
        val config = DashScopeConfig(
            "sk-dcc82f29f97348deacf9969d27d209bf",  // 你的API Key
            null, null, null  // 使用默认值
        )
        asrClient = AsrClient(config)
        sessionManager = AsrSessionManager(asrClient, "pcm", 16000)
        
        // 请求录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
        
        btnStart.setOnClickListener { startRecognition() }
        btnStop.setOnClickListener { stopRecognition() }
    }
    
    private fun startRecognition() {
        if (isRecording) return
        
        // 创建会话
        val sessionId = "session-${System.currentTimeMillis()}"
        currentSession = sessionManager.getOrCreateSession(sessionId)
        
        // 设置回调（Kotlin lambda语法）
        currentSession?.onPartialResult { text ->
            runOnUiThread {
                if (text.isNotEmpty()) {
                    textViewPartial.text = "部分结果: $text"
                }
            }
        }
        
        currentSession?.onFinalResult { text ->
            runOnUiThread {
                if (text.isNotEmpty()) {
                    textViewFinal.text = "最终结果: $text"
                }
            }
        }
        
        currentSession?.onError {
            runOnUiThread {
                Toast.makeText(this, "识别出错", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 开始识别（对应命令行：asr-stream pcm 16000）
        currentSession?.start("normal")
        
        // 开始录音
        startRecording()
        
        btnStart.isEnabled = false
        btnStop.isEnabled = true
    }
    
    private fun stopRecognition() {
        isRecording = false
        
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        
        currentSession?.apply {
            end()
            sessionManager.removeSession(sessionId)
        }
        currentSession = null
        
        btnStart.isEnabled = true
        btnStop.isEnabled = false
    }
    
    private fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        audioRecord?.startRecording()
        isRecording = true
        
        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0 && currentSession != null) {
                    // 计算RMS
                    val rms = calculateRMS(buffer, read)
                    
                    // 发送音频数据
                    currentSession?.addAudio(
                        buffer.copyOf(read),
                        rms,
                        "normal"
                    )
                }
            }
        }
        
        recordingThread?.start()
    }
    
    private fun calculateRMS(buffer: ByteArray, length: Int): Double {
        var sum = 0L
        for (i in 0 until length step 2) {
            val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort()
            sum += sample * sample
        }
        return Math.sqrt(sum / (length / 2.0))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRecognition()
        sessionManager.clearAll()
    }
}
```

## Service示例（Kotlin）

```kotlin
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.tangyu.config.DashScopeConfig
import com.example.tangyu.speech.*

class AsrService : Service() {
    private lateinit var asrClient: AsrClient
    private lateinit var sessionManager: AsrSessionManager
    private var currentSession: AsrSession? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // 创建配置
        val config = DashScopeConfig(
            "sk-dcc82f29f97348deacf9969d27d209bf",
            null, null, null
        )
        
        asrClient = AsrClient(config)
        sessionManager = AsrSessionManager(asrClient, "pcm", 16000)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "start_recognition" -> startRecognition()
            "stop_recognition" -> stopRecognition()
            "add_audio" -> {
                val audioData = intent.getByteArrayExtra("audio_data")
                val rms = intent.getDoubleExtra("rms", 0.0)
                val mode = intent.getStringExtra("mode") ?: "normal"
                audioData?.let {
                    currentSession?.addAudio(it, rms, mode)
                }
            }
        }
        return START_STICKY
    }
    
    private fun startRecognition() {
        val sessionId = "session-${System.currentTimeMillis()}"
        currentSession = sessionManager.getOrCreateSession(sessionId)
        
        currentSession?.onPartialResult { text ->
            sendBroadcast(Intent("ASR_PARTIAL_RESULT").apply {
                putExtra("text", text)
            })
        }
        
        currentSession?.onFinalResult { text ->
            sendBroadcast(Intent("ASR_FINAL_RESULT").apply {
                putExtra("text", text)
            })
        }
        
        currentSession?.onError {
            sendBroadcast(Intent("ASR_ERROR"))
        }
        
        currentSession?.start("normal")
    }
    
    private fun stopRecognition() {
        currentSession?.apply {
            end()
            sessionManager.removeSession(sessionId)
        }
        currentSession = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sessionManager.clearAll()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
```

## 配置工具类（Kotlin）

```kotlin
import com.example.tangyu.config.DashScopeConfig
import com.example.tangyu.config.CredentialConfig
import com.example.tangyu.speech.AsrClient
import com.example.tangyu.speech.TtsClient
import com.example.tangyu.speech.TokenClient

object SpeechConfigHelper {
    // 你的API密钥（建议放在安全的地方，不要硬编码）
    private const val DASHSCOPE_API_KEY = "sk-dcc82f29f97348deacf9969d27d209bf"
    private const val ACCESS_KEY_ID = "your-access-key-id"
    private const val ACCESS_KEY_SECRET = "your-access-key-secret"
    private const val APP_KEY = "your-app-key"
    
    /**
     * 创建ASR配置
     */
    fun createDashScopeConfig(): DashScopeConfig {
        return DashScopeConfig(
            DASHSCOPE_API_KEY,
            null,  // 使用默认值
            null,  // 使用默认值
            null   // 使用默认值
        )
    }
    
    /**
     * 创建TTS配置
     */
    fun createCredentialConfig(): CredentialConfig {
        return CredentialConfig(
            ACCESS_KEY_ID,
            ACCESS_KEY_SECRET,
            APP_KEY
        )
    }
    
    /**
     * 创建ASR客户端
     */
    fun createAsrClient(): AsrClient {
        return AsrClient(createDashScopeConfig())
    }
    
    /**
     * 创建TTS客户端
     */
    fun createTtsClient(): TtsClient {
        val config = createCredentialConfig()
        val tokenClient = TokenClient(config)
        return TtsClient(config, tokenClient)
    }
}
```

## 使用配置工具类

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var asrClient: AsrClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 使用工具类创建客户端
        asrClient = SpeechConfigHelper.createAsrClient()
    }
}
```

## Kotlin vs Java 的优势

1. **更简洁的语法**：
   ```kotlin
   // Kotlin
   session.onPartialResult { text -> ... }
   
   // Java
   session.onPartialResult(text -> { ... });
   ```

2. **空安全**：
   ```kotlin
   currentSession?.start("normal")  // 自动处理null
   ```

3. **扩展函数**（可选）：
   ```kotlin
   fun AsrSession.startWithCallbacks(
       onPartial: (String) -> Unit,
       onFinal: (String) -> Unit
   ) {
       onPartialResult(onPartial)
       onFinalResult(onFinal)
       start("normal")
   }
   ```

## 注意事项

1. **依赖配置**：与Java版本完全相同，在 `build.gradle` 中添加：
   ```gradle
   dependencies {
       implementation 'com.example:tangyu-aliyun-speech:0.1.0'
       // ... 其他依赖
   }
   ```

2. **互操作性**：Kotlin可以直接调用Java库，无需任何转换

3. **类型推断**：Kotlin的类型推断让代码更简洁

4. **协程支持**（可选）：如果需要异步处理，可以使用Kotlin协程：
   ```kotlin
   lifecycleScope.launch {
       withContext(Dispatchers.IO) {
           val result = asrClient.transcribe(audioFile, "pcm", 16000)
           withContext(Dispatchers.Main) {
               textView.text = result
           }
       }
   }
   ```

## 总结

- ✅ **完全兼容**：Kotlin可以直接调用Java库
- ✅ **语法更简洁**：lambda表达式更优雅
- ✅ **空安全**：Kotlin的空安全特性更安全
- ✅ **无需修改库**：直接使用即可

只需将Java示例代码转换为Kotlin语法即可，功能完全相同！

