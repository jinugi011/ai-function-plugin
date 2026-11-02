package io.aiplugin.core.local;

import io.aiplugin.core.AiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * llama.cpp 의 {@code llama-server} 프로세스를 띄우고 관리합니다 (OpenAI 호환 API 제공).
 * 설치: macOS {@code brew install llama.cpp}, 그 외 https://github.com/ggml-org/llama.cpp/releases
 */
public class LlamaCppServer implements AutoCloseable {

    private final String binary;
    private final Path modelPath;
    private final String host;
    private final int port;
    private final int contextSize;
    private final Integer gpuLayers;
    private final List<String> extraArgs;
    private final Duration startupTimeout;
    private final Path logFile;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private Process process;

    public LlamaCppServer(String binary, Path modelPath, String host, int port, int contextSize, Integer gpuLayers,
                          List<String> extraArgs, Duration startupTimeout) {
        this.binary = binary == null || binary.isBlank() ? "llama-server" : binary;
        this.modelPath = modelPath;
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
        this.port = port;
        this.contextSize = contextSize;
        this.gpuLayers = gpuLayers;
        this.extraArgs = extraArgs == null ? List.of() : List.copyOf(extraArgs);
        this.startupTimeout = startupTimeout;
        this.logFile = modelPath.resolveSibling("llama-server-" + port + ".log");
    }

    public String baseUrl() {
        return "http://" + host + ":" + port + "/v1";
    }

    public Path logFile() {
        return logFile;
    }

    List<String> command() {
        List<String> cmd = new ArrayList<>(List.of(binary, "-m", modelPath.toString(),
                "--host", host, "--port", String.valueOf(port), "-c", String.valueOf(contextSize)));
        if (gpuLayers != null) {
            cmd.add("-ngl");
            cmd.add(String.valueOf(gpuLayers));
        }
        cmd.addAll(extraArgs);
        return cmd;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    /** 서버를 시작하고 모델 로딩이 끝날 때까지(/health == 200) 기다립니다. */
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        if (isHealthy()) {
            throw new AiException("Port " + port + " is already in use by another server. "
                    + "Change the llama.cpp port or set base-url to use the existing server.");
        }
        try {
            process = new ProcessBuilder(command())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
        } catch (IOException e) {
            throw new AiException("Cannot start '" + binary + "'. Install llama.cpp (macOS: brew install llama.cpp) "
                    + "or set the full path of llama-server: " + e.getMessage(), e);
        }
        long deadline = System.nanoTime() + startupTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new AiException("llama-server exited with code " + process.exitValue() + ":\n" + logTail());
            }
            if (isHealthy()) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                close();
                throw new AiException("llama-server startup interrupted", e);
            }
        }
        close();
        throw new AiException("llama-server did not become ready within " + startupTimeout + ":\n" + logTail());
    }

    private boolean isHealthy() {
        try {
            HttpResponse<Void> res = http.send(HttpRequest.newBuilder(
                            URI.create("http://" + host + ":" + port + "/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String logTail() {
        try {
            List<String> lines = Files.readAllLines(logFile);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 20), lines.size()));
        } catch (IOException e) {
            return "(no log: " + logFile + ")";
        }
    }

    @Override
    public synchronized void close() {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        process = null;
    }
}
