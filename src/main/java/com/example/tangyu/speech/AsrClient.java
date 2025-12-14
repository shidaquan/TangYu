package com.example.tangyu.speech;

import com.example.tangyu.config.CredentialConfig;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Thin wrapper for Aliyun short speech recognition (ASR) API.
 */
public class AsrClient {
    private static final Logger LOG = LoggerFactory.getLogger(AsrClient.class);
    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
    private static final String ASR_ENDPOINT = "https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/asr";

    private final CredentialConfig credentialConfig;
    private final TokenClient tokenClient;
    private final OkHttpClient httpClient;

    public AsrClient(CredentialConfig credentialConfig, TokenClient tokenClient) {
        this.credentialConfig = Objects.requireNonNull(credentialConfig);
        this.tokenClient = Objects.requireNonNull(tokenClient);
        this.httpClient = new OkHttpClient();
    }

    public String transcribe(Path audioFile, String format, int sampleRate) {
        validate(format, sampleRate);
        try {
            byte[] audioBytes = Files.readAllBytes(audioFile);
            HttpUrl url = HttpUrl.parse(ASR_ENDPOINT).newBuilder()
                    .addQueryParameter("appkey", credentialConfig.getAppKey())
                    .addQueryParameter("format", format)
                    .addQueryParameter("sample_rate", Integer.toString(sampleRate))
                    .build();

            RequestBody body = RequestBody.create(audioBytes, OCTET_STREAM);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-NLS-Token", tokenClient.getToken())
                    .post(body)
                    .build();

            LOG.info("Sending ASR request to {} with {} bytes", url, audioBytes.length);
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("ASR request failed: " + response.code() + " " + response.message());
                }
                String responseBody = Objects.requireNonNull(response.body()).string();
                LOG.debug("ASR raw response: {}", responseBody);
                return responseBody;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to send ASR request", e);
        }
    }

    private void validate(String format, int sampleRate) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Audio format must be provided (e.g., pcm, wav)");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
    }
}
