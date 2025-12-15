package com.example.tangyu.robot;

import org.springframework.core.env.Environment;

import java.util.Objects;

public class RobotConfig {
    private static final String DEFAULT_WS_URL = "ws://localhost:8080/api/v1/robot/memory/ws";
    private static final String DEFAULT_PERSONA_ID = "394f8467-b114-4ef3-8647-077d2eab5a9d";
    private static final String DEFAULT_SCENE = "chat";
    private static final String DEFAULT_INPUT_TYPE = "listening";

    private final String wsUrl;
    private final String personaId;
    private final String scene;
    private final String inputType;

    public RobotConfig(String wsUrl, String personaId, String scene, String inputType) {
        this.wsUrl = Objects.requireNonNullElse(wsUrl, DEFAULT_WS_URL);
        this.personaId = Objects.requireNonNullElse(personaId, DEFAULT_PERSONA_ID);
        this.scene = Objects.requireNonNullElse(scene, DEFAULT_SCENE);
        this.inputType = Objects.requireNonNullElse(inputType, DEFAULT_INPUT_TYPE);
    }

    public static RobotConfig fromEnvironment(Environment env) {
        String wsUrl = env.getProperty("robot.wsUrl", DEFAULT_WS_URL);
        String personaId = env.getProperty("robot.personaId", DEFAULT_PERSONA_ID);
        String scene = env.getProperty("robot.scene", DEFAULT_SCENE);
        String inputType = env.getProperty("robot.inputType", DEFAULT_INPUT_TYPE);
        return new RobotConfig(wsUrl, personaId, scene, inputType);
    }

    /**
     * 从系统属性/环境变量构建配置，供 CLI 使用。
     */
    public static RobotConfig fromSystemEnv() {
        String wsUrl = firstNonBlank(
                System.getProperty("robot.wsUrl"),
                System.getenv("ROBOT_WS_URL"),
                DEFAULT_WS_URL);
        String personaId = firstNonBlank(
                System.getProperty("robot.personaId"),
                System.getenv("ROBOT_PERSONA_ID"),
                DEFAULT_PERSONA_ID);
        String scene = firstNonBlank(
                System.getProperty("robot.scene"),
                System.getenv("ROBOT_SCENE"),
                DEFAULT_SCENE);
        String inputType = firstNonBlank(
                System.getProperty("robot.inputType"),
                System.getenv("ROBOT_INPUT_TYPE"),
                DEFAULT_INPUT_TYPE);
        return new RobotConfig(wsUrl, personaId, scene, inputType);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public String getPersonaId() {
        return personaId;
    }

    public String getScene() {
        return scene;
    }

    public String getInputType() {
        return inputType;
    }
}
