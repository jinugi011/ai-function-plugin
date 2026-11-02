package io.aiplugin.core.local;

import io.aiplugin.core.AiException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * GGUF 모델 파일 다운로더. 중단된 다운로드는 이어받고(.part + Range), 완료되면 원자적으로 이름을 바꿉니다.
 *
 * <p>모델 지정 형식:
 * <ul>
 *   <li>{@code hf:<owner>/<repo>/<file>.gguf} — Hugging Face (main 브랜치)</li>
 *   <li>{@code https://...} — 직접 URL</li>
 *   <li>로컬 파일 경로 — 다운로드 없이 사용</li>
 * </ul>
 */
public class ModelDownloader {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final String huggingFaceToken;

    /** @param huggingFaceToken 접근 제한(gated) 모델용 HF 토큰, 없으면 null */
    public ModelDownloader(String huggingFaceToken) {
        this.huggingFaceToken = huggingFaceToken;
    }

    public ModelDownloader() {
        this(null);
    }

    public static String resolveUrl(String spec) {
        if (spec.startsWith("hf:")) {
            String[] parts = spec.substring(3).split("/", 3);
            if (parts.length < 3) {
                throw new IllegalArgumentException("Expected hf:<owner>/<repo>/<file>.gguf but got " + spec);
            }
            return "https://huggingface.co/" + parts[0] + "/" + parts[1] + "/resolve/main/" + parts[2];
        }
        return spec;
    }

    public static String fileName(String spec) {
        String url = resolveUrl(spec);
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** 이미 내려받았거나 로컬 경로이면 그 경로를, 아니면 null. */
    public static Path findExisting(String spec, Path modelDir) {
        Path local = Path.of(spec.startsWith("hf:") || spec.startsWith("http") ? fileName(spec) : spec);
        if (local.isAbsolute() && Files.isRegularFile(local)) {
            return local;
        }
        Path inDir = modelDir.resolve(fileName(spec));
        return Files.isRegularFile(inDir) ? inDir : null;
    }

    /** 모델을 modelDir 에 내려받고 경로를 반환합니다. 이미 있으면 바로 반환합니다. */
    public Path download(String spec, Path modelDir, Consumer<DownloadProgress> progress) {
        Path existing = findExisting(spec, modelDir);
        if (existing != null) {
            return existing;
        }
        if (!spec.startsWith("hf:") && !spec.startsWith("http")) {
            throw new AiException("Model file not found: " + spec);
        }
        String url = resolveUrl(spec);
        Path target = modelDir.resolve(fileName(spec));
        Path part = modelDir.resolve(target.getFileName() + ".part");
        try {
            Files.createDirectories(modelDir);
            long offset = Files.exists(part) ? Files.size(part) : 0;

            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).GET();
            if (offset > 0) {
                req.header("Range", "bytes=" + offset + "-");
            }
            if (huggingFaceToken != null && !huggingFaceToken.isBlank() && url.contains("huggingface.co")) {
                req.header("Authorization", "Bearer " + huggingFaceToken);
            }
            HttpResponse<InputStream> res = http.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = res.statusCode();
            if (status == 416) {
                // 이미 전부 받은 .part
                res.body().close();
            } else {
                if (status != 200 && status != 206) {
                    res.body().close();
                    throw new AiException("Model download failed (" + status + "): " + url
                            + (status == 401 || status == 403 ? " — gated model? set a Hugging Face token" : ""));
                }
                boolean append = status == 206;
                long done = append ? offset : 0;
                long length = res.headers().firstValueAsLong("Content-Length").orElse(-1);
                long total = length > 0 ? done + length : 0;
                try (InputStream in = res.body();
                     OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE,
                             append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buf = new byte[1 << 16];
                    long lastReport = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (progress != null && done - lastReport >= (8 << 20)) {
                            progress.accept(new DownloadProgress("downloading " + target.getFileName(), done, total));
                            lastReport = done;
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException();
                        }
                    }
                }
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (progress != null) {
                long size = Files.size(target);
                progress.accept(new DownloadProgress("success", size, size));
            }
            return target;
        } catch (IOException e) {
            throw new AiException("Model download failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Model download interrupted (partial file kept for resume)", e);
        }
    }
}
