package io.aiplugin.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.aiplugin.core.local.DownloadProgress;
import io.aiplugin.core.local.LlamaCppProvider;
import io.aiplugin.core.local.ModelDownloader;
import io.aiplugin.core.local.OllamaClient;
import io.aiplugin.core.local.OllamaProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalModelTest {

    private HttpServer server;
    private String base;
    private final AtomicBoolean pulled = new AtomicBoolean();
    private final List<String> chatBodies = new ArrayList<>();
    private final byte[] modelBytes = new byte[300_000];

    @BeforeEach
    void startFakeServer() throws IOException {
        for (int i = 0; i < modelBytes.length; i++) {
            modelBytes[i] = (byte) i;
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", ex -> send(ex, 200, "{\"version\":\"test\"}"));
        server.createContext("/api/tags", ex -> send(ex, 200, pulled.get()
                ? "{\"models\":[{\"name\":\"llama3.2:latest\"}]}" : "{\"models\":[]}"));
        server.createContext("/api/pull", ex -> {
            pulled.set(true);
            send(ex, 200, "{\"status\":\"pulling manifest\"}\n"
                    + "{\"status\":\"downloading\",\"completed\":50,\"total\":100}\n{\"status\":\"success\"}\n");
        });
        server.createContext("/api/chat", ex -> {
            chatBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(ex, 200, "{\"model\":\"llama3.2\",\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"{\\\"name\\\":\\\"홍길동\\\",\\\"age\\\":\\\"30\\\"}\"},"
                    + "\"prompt_eval_count\":12,\"eval_count\":8}");
        });
        // Range 를 지원하는 모델 파일 서버
        server.createContext("/models/tiny.gguf", ex -> {
            String range = ex.getRequestHeaders().getFirst("Range");
            int from = range == null ? 0 : Integer.parseInt(range.replaceAll("bytes=(\\d+)-", "$1"));
            ex.getResponseHeaders().add("Content-Length", String.valueOf(modelBytes.length - from));
            ex.sendResponseHeaders(range == null ? 200 : 206, modelBytes.length - from);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(modelBytes, from, modelBytes.length - from);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void ollamaAutoPullsMissingModelThenChats() {
        List<DownloadProgress> progress = new ArrayList<>();
        OllamaProvider ollama = new OllamaProvider(new OllamaClient(base), "llama3.2", 512, true,
                Duration.ofSeconds(10), progress::add);
        AiClient client = new AiClient(List.of(ollama), "ollama", null, null);

        Map<String, String> kv = client.prompt("이름과 나이: 홍길동 30세").keyValue();

        assertEquals(Map.of("name", "홍길동", "age", "30"), kv);
        assertTrue(pulled.get());
        assertEquals(0.5, progress.get(1).fraction());
        assertTrue(chatBodies.get(0).contains("\"format\":\"json\""));
        assertTrue(new OllamaClient(base).isRunning());
    }

    @Test
    void downloaderResumesPartialFile(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("tiny.gguf.part"), java.util.Arrays.copyOf(modelBytes, 1000));
        Path file = new ModelDownloader().download(base + "/models/tiny.gguf", dir, null);
        assertArrayEquals(modelBytes, Files.readAllBytes(file));
        assertEquals("https://huggingface.co/a/b/resolve/main/sub/m.gguf", ModelDownloader.resolveUrl("hf:a/b/sub/m.gguf"));
    }

    /** 가짜 llama-server(python 스크립트)로 다운로드 → 서버 실행 → OpenAI 호환 호출 전체 흐름을 검증. */
    @Test
    void llamaCppDownloadsStartsServerAndChats(@TempDir Path dir) throws Exception {
        assumeTrue(new ProcessBuilder("python3", "--version").start().waitFor() == 0, "python3 required");
        Path fake = dir.resolve("fake-llama-server");
        Files.writeString(fake, """
                #!/usr/bin/env python3
                import sys, json
                from http.server import BaseHTTPRequestHandler, HTTPServer
                args = sys.argv[1:]
                port = int(args[args.index('--port') + 1])
                model = args[args.index('-m') + 1]
                class H(BaseHTTPRequestHandler):
                    def log_message(self, *a): pass
                    def reply(self, body):
                        data = json.dumps(body).encode()
                        self.send_response(200); self.send_header('Content-Length', str(len(data))); self.end_headers()
                        self.wfile.write(data)
                    def do_GET(self): self.reply({'status': 'ok'})
                    def do_POST(self):
                        req = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                        assert 'max_tokens' in req
                        self.reply({'model': model, 'choices': [{'message': {'content': 'pong:' + req['messages'][-1]['content']}}],
                                    'usage': {'prompt_tokens': 3, 'completion_tokens': 2}})
                HTTPServer(('127.0.0.1', port), H).serve_forever()
                """);
        assertTrue(fake.toFile().setExecutable(true));
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        try (LlamaCppProvider llama = LlamaCppProvider.builder()
                .model(base + "/models/tiny.gguf")
                .modelDir(dir.resolve("models"))
                .serverBinary(fake.toString())
                .port(port)
                .startupTimeout(Duration.ofSeconds(20))
                .build()) {
            AiClient client = new AiClient(List.of(llama), null, null, null);
            AiResult result = client.prompt("ping").call(ResponseFormat.TEXT);

            assertEquals("pong:ping", result.text());
            assertTrue(result.response().model().endsWith("tiny.gguf"));
            assertTrue(llama.isServerRunning());
            assertTrue(Files.exists(dir.resolve("models/tiny.gguf")));
            llama.close();
            assertTrue(!llama.isServerRunning());
        }
    }
}
