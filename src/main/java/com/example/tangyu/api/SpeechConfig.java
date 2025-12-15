package com.example.tangyu.api;

import com.example.tangyu.config.CredentialConfig;
import com.example.tangyu.config.DashScopeConfig;
import com.example.tangyu.robot.RobotClient;
import com.example.tangyu.robot.RobotConfig;
import com.example.tangyu.speech.AsrClient;
import com.example.tangyu.speech.TokenClient;
import com.example.tangyu.speech.TtsClient;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpeechConfig {

    @Bean
    public DashScopeConfig dashScopeConfig(Environment env) {
        return DashScopeConfig.fromEnvironment(env);
    }

    @Bean
    public CredentialConfig credentialConfig(Environment env) {
        return CredentialConfig.fromEnvironment(env);
    }

    @Bean
    public TokenClient tokenClient(CredentialConfig credentialConfig) {
        return new TokenClient(credentialConfig);
    }

    @Bean
    public AsrClient asrClient(DashScopeConfig dashScopeConfig) {
        return new AsrClient(dashScopeConfig);
    }

    @Bean
    public TtsClient ttsClient(CredentialConfig credentialConfig, TokenClient tokenClient) {
        return new TtsClient(credentialConfig, tokenClient);
    }

    @Bean
    public RobotConfig robotConfig(Environment env) {
        return RobotConfig.fromEnvironment(env);
    }

    @Bean
    public RobotClient robotClient(RobotConfig robotConfig) {
        return new RobotClient(robotConfig);
    }
}
