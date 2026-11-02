package io.aiplugin.idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import io.aiplugin.core.local.DownloadProgress;
import io.aiplugin.core.local.LlamaCppProvider;
import io.aiplugin.idea.settings.AiSettings;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * IDE 전체에서 하나의 llama-server 프로세스를 공유합니다. 설정이 바뀌면 다시 만들고, IDE 종료 시 프로세스를 끕니다.
 */
@Service(Service.Level.APP)
public final class LocalModelService implements Disposable {

    private LlamaCppProvider provider;
    private List<Object> providerConfig;
    private volatile Consumer<DownloadProgress> progressListener;

    public static LocalModelService getInstance() {
        return ApplicationManager.getApplication().getService(LocalModelService.class);
    }

    /** 다운로드 진행 상황을 받을 리스너 (Local Models 탭이 사용). */
    public void setProgressListener(Consumer<DownloadProgress> listener) {
        this.progressListener = listener;
    }

    public synchronized LlamaCppProvider llamaCpp() {
        AiSettings.State s = AiSettings.getInstance().getState();
        List<Object> config = List.of(s.llamaModel, s.llamaModelDir, s.llamaServerBinary, s.llamaPort, s.llamaContextSize);
        if (provider == null || !Objects.equals(config, providerConfig)) {
            if (provider != null) {
                provider.close();
            }
            provider = LlamaCppProvider.builder()
                    .model(s.llamaModel)
                    .modelDir(Path.of(s.llamaModelDir))
                    .serverBinary(s.llamaServerBinary)
                    .port(s.llamaPort)
                    .contextSize(s.llamaContextSize)
                    .huggingFaceToken(AiSettings.getApiKey("huggingface"))
                    .progressListener(p -> {
                        Consumer<DownloadProgress> l = progressListener;
                        if (l != null) {
                            l.accept(p);
                        }
                    })
                    .build();
            providerConfig = config;
        }
        return provider;
    }

    /** 설정을 다시 읽지 않고 현재 서버 상태만 확인합니다 (EDT 에서 호출 가능). */
    public synchronized boolean isServerRunning() {
        return provider != null && provider.isServerRunning();
    }

    public synchronized void stop() {
        if (provider != null) {
            provider.close();
        }
    }

    @Override
    public void dispose() {
        stop();
    }
}
