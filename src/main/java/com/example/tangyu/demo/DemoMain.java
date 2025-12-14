package com.example.tangyu.demo;

import com.example.tangyu.config.CredentialConfig;
import com.example.tangyu.speech.AsrClient;
import com.example.tangyu.speech.TokenClient;
import com.example.tangyu.speech.TtsClient;

import java.nio.file.Path;

/**
 * Minimal CLI for invoking ASR and TTS operations.
 *
 * Usage examples:
 *  ASR: java -jar target/tangyu-aliyun-speech-0.1.0.jar asr sample.pcm pcm 16000
 *  TTS: java -jar target/tangyu-aliyun-speech-0.1.0.jar tts "你好" xiaoyun wav 16000 output.wav
 */
public class DemoMain {

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(1);
        }

        CredentialConfig config = CredentialConfig.fromEnvironment();
        TokenClient tokenClient = new TokenClient(config);

        switch (args[0]) {
            case "asr" -> runAsr(args, config, tokenClient);
            case "tts" -> runTts(args, config, tokenClient);
            default -> {
                printHelp();
                System.exit(1);
            }
        }
    }

    private static void runAsr(String[] args, CredentialConfig config, TokenClient tokenClient) {
        if (args.length < 4) {
            System.err.println("Usage: asr <audioPath> <format> <sampleRate>");
            System.exit(1);
        }
        Path audio = Path.of(args[1]);
        String format = args[2];
        int sampleRate = Integer.parseInt(args[3]);
        AsrClient client = new AsrClient(config, tokenClient);
        String response = client.transcribe(audio, format, sampleRate);
        System.out.println(response);
    }

    private static void runTts(String[] args, CredentialConfig config, TokenClient tokenClient) {
        if (args.length < 6) {
            System.err.println("Usage: tts <text> <voice> <format> <sampleRate> <outputFile>");
            System.exit(1);
        }
        String text = args[1];
        String voice = args[2];
        String format = args[3];
        int sampleRate = Integer.parseInt(args[4]);
        Path output = Path.of(args[5]);
        TtsClient client = new TtsClient(config, tokenClient);
        client.synthesizeToFile(text, voice, format, sampleRate, output);
        System.out.printf("Audio written to %s%n", output.toAbsolutePath());
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  asr <audioPath> <format> <sampleRate>");
        System.out.println("  tts <text> <voice> <format> <sampleRate> <outputFile>");
        System.out.println();
        System.out.println("Credentials are read from environment variables: ");
        System.out.println("  ALIBABA_CLOUD_ACCESS_KEY_ID");
        System.out.println("  ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        System.out.println("  ALIBABA_CLOUD_APP_KEY");
    }
}
