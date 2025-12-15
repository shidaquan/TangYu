package com.example.tangyu.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 极简 HTTP 文件服务，只支持 GET /pcm?name=xxx 从指定目录读取文件。
 * 用于前端拉取已生成的 PCM/WAV 等音频文件。
 */
public class PcmHttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(PcmHttpServer.class);

    private final int port;
    private final Path baseDir;
    private HttpServer server;

    public PcmHttpServer(int port, Path baseDir) {
        this.port = port;
        this.baseDir = baseDir;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/pcm", new FileHandler());
            server.setExecutor(null);
            server.start();
            LOG.info("PCM HTTP server started on port {}, baseDir={}", port, baseDir.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("无法启动 HTTP 服务: " + e.getMessage(), e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = Query.parse(query);
            String name = params.get("name");
            if (name == null || name.isBlank()) {
                writeJson(exchange, 400, "{\"error\":\"缺少 name 参数\"}");
                return;
            }
            // 防止目录穿越
            Path target = baseDir.resolve(name).normalize();
            if (!target.startsWith(baseDir)) {
                writeJson(exchange, 400, "{\"error\":\"非法路径\"}");
                return;
            }
            if (!Files.isReadable(target)) {
                writeJson(exchange, 404, "{\"error\":\"文件不存在或不可读\"}");
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/octet-stream");
            headers.set("Content-Disposition", "attachment; filename=\"" + target.getFileName() + "\"");
            byte[] data = Files.readAllBytes(target);
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static class Query {
        static Map<String, String> parse(String query) {
            if (query == null || query.isBlank()) return Map.of();
            return java.util.Arrays.stream(query.split("&"))
                    .map(kv -> kv.split("=", 2))
                    .filter(arr -> arr.length == 2)
                    .collect(java.util.stream.Collectors.toMap(
                            arr -> urlDecode(arr[0]),
                            arr -> urlDecode(arr[1]),
                            (a, b) -> b));
        }

        private static String urlDecode(String val) {
            return java.net.URLDecoder.decode(val, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
