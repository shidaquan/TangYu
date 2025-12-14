package com.example.tangyu.speech;

import com.example.tangyu.config.CredentialConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thin wrapper for Aliyun text-to-speech (TTS) API.
 */
public class TtsClient {
    private static final Logger LOG = LoggerFactory.getLogger(TtsClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String TTS_ENDPOINT = "https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts";

    private final CredentialConfig credentialConfig;
    private final TokenClient tokenClient;
    private final OkHttpClient httpClient;

    public TtsClient(CredentialConfig credentialConfig, TokenClient tokenClient) {
        this.credentialConfig = Objects.requireNonNull(credentialConfig);
        this.tokenClient = Objects.requireNonNull(tokenClient);
        this.httpClient = new OkHttpClient();
    }

    public Path synthesizeToFile(String text, String voice, String format, int sampleRate, Path outputFile) {
        validate(text, voice, format, sampleRate);
        Map<String, Object> payload = new HashMap<>();
        payload.put("appkey", credentialConfig.getAppKey());
        payload.put("text", text);
        payload.put("format", format);
        payload.put("sample_rate", sampleRate);
        payload.put("voice", voice);

        String requestBody = JsonUtils.writeJson(payload);

        Request request = new Request.Builder()
                .url(TTS_ENDPOINT)
                .addHeader("X-NLS-Token", tokenClient.getToken())
                .post(RequestBody.create(requestBody, JSON))
                .build();

        LOG.info("Sending TTS request to {} for {} characters", TTS_ENDPOINT, text.length());
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("TTS request failed: " + response.code() + " " + response.message());
            }

            String contentType = Objects.requireNonNull(response.body()).contentType().toString();
            if (contentType.contains("application/json")) {
                String errorBody = response.body().string();
                throw new IllegalStateException("TTS returned error JSON: " + errorBody);
            }

            try (InputStream inputStream = response.body().byteStream()) {
                Files.copy(inputStream, outputFile);
                LOG.info("Audio saved to {}", outputFile.toAbsolutePath());
            }
            return outputFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to send TTS request", e);
        }
    }

    private void validate(String text, String voice, String format, int sampleRate) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to synthesize must be provided");
        }
        if (voice == null || voice.isBlank()) {
            throw new IllegalArgumentException("Voice name is required");
        }
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Audio format must be provided (e.g., wav, mp3)");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
    }
}
