package io.aiplugin.idea.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.aiplugin.core.local.DownloadProgress;
import io.aiplugin.core.local.ModelDownloader;
import io.aiplugin.core.local.OllamaClient;
import io.aiplugin.idea.LocalModelService;
import io.aiplugin.idea.settings.AiSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Ollama 모델 다운로드/관리 + llama.cpp GGUF 다운로드/서버 실행. */
public class LocalModelsPanel extends JPanel {

    private static final String[] OLLAMA_SUGGESTIONS = {"llama3.2", "qwen2.5:7b", "exaone3.5", "gemma3", "mistral"};

    private final Project project;
    private final JBTextField ollamaUrl = new JBTextField();
    private final ComboBox<String> ollamaModel = new ComboBox<>(OLLAMA_SUGGESTIONS);
    private final JBTextField ggufModel = new JBTextField();
    private final JBTextField modelDir = new JBTextField();
    private final JBTextField serverBinary = new JBTextField();
    private final JBTextField port = new JBTextField();
    private final JBLabel llamaStatus = new JBLabel("stopped");
    private final JBTextArea log = new JBTextArea();

    public LocalModelsPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        setBorder(JBUI.Borders.empty(6));
        ollamaModel.setEditable(true);
        log.setEditable(false);

        AiSettings.State s = AiSettings.getInstance().getState();
        ollamaUrl.setText(s.ollamaBaseUrl);
        ollamaModel.setSelectedItem(s.ollamaModel);
        ggufModel.setText(s.llamaModel);
        modelDir.setText(s.llamaModelDir);
        serverBinary.setText(s.llamaServerBinary);
        port.setText(String.valueOf(s.llamaPort));

        JPanel form = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("<html><b>Ollama</b> — install from ollama.com and keep it running</html>"))
                .addLabeledComponent("Server URL:", ollamaUrl)
                .addLabeledComponent("Model:", ollamaModel)
                .addComponent(buttons(
                        button("Status", this::ollamaStatus),
                        button("Download (pull)", this::ollamaPull),
                        button("Installed Models", this::ollamaList),
                        button("Delete", this::ollamaDelete)))
                .addSeparator()
                .addComponent(new JBLabel("<html><b>llama.cpp</b> — GGUF model + llama-server "
                        + "(macOS: <code>brew install llama.cpp</code>)</html>"))
                .addLabeledComponent("Model (hf:owner/repo/file.gguf, URL or path):", ggufModel)
                .addLabeledComponent("Model directory:", modelDir)
                .addLabeledComponent("llama-server binary:", serverBinary)
                .addLabeledComponent("Port:", port)
                .addComponent(buttons(
                        button("Download Model", this::llamaDownload),
                        button("Start Server", this::llamaStart),
                        button("Stop Server", this::llamaStop),
                        new JBLabel("Server:"), llamaStatus))
                .addComponent(new JBLabel("Use provider 'ollama' or 'llamacpp' in the Prompt tab."))
                .getPanel();

        add(form, BorderLayout.NORTH);
        add(new JBScrollPane(log), BorderLayout.CENTER);
        LocalModelService.getInstance().setProgressListener(throttled());
    }

    private static JPanel buttons(javax.swing.JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (javax.swing.JComponent c : components) {
            panel.add(c);
        }
        return panel;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void saveSettings() {
        AiSettings.State s = AiSettings.getInstance().getState();
        s.ollamaBaseUrl = ollamaUrl.getText().trim();
        s.ollamaModel = String.valueOf(ollamaModel.getEditor().getItem()).trim();
        s.llamaModel = ggufModel.getText().trim();
        s.llamaModelDir = modelDir.getText().trim();
        s.llamaServerBinary = serverBinary.getText().trim();
        try {
            s.llamaPort = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException ignored) {
            port.setText(String.valueOf(s.llamaPort));
        }
    }

    private void append(String message) {
        String line = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + message + "\n";
        ApplicationManager.getApplication().invokeLater(() -> {
            log.append(line);
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    /** 다운로드 진행 로그가 너무 많아지지 않도록 상태가 바뀌거나 5% 진행될 때만 기록합니다. */
    private Consumer<DownloadProgress> throttled() {
        return new Consumer<>() {
            private String lastStatus = "";
            private double lastFraction = -1;

            @Override
            public void accept(DownloadProgress p) {
                if (!p.status().equals(lastStatus) || p.fraction() - lastFraction >= 0.05) {
                    lastStatus = p.status();
                    lastFraction = p.fraction();
                    append(p.toString());
                }
            }
        };
    }

    private void background(String title, java.util.function.Consumer<ProgressIndicator> work) {
        saveSettings();
        new Task.Backgroundable(project, title, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                work.accept(indicator);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                append("ERROR: " + error.getMessage());
            }

            @Override
            public void onFinished() {
                updateLlamaStatus();
            }
        }.queue();
    }

    private Consumer<DownloadProgress> progressTo(ProgressIndicator indicator) {
        Consumer<DownloadProgress> logger = throttled();
        return p -> {
            indicator.checkCanceled();
            indicator.setText(p.toString());
            if (p.fraction() >= 0) {
                indicator.setIndeterminate(false);
                indicator.setFraction(p.fraction());
            }
            logger.accept(p);
        };
    }

    private void ollamaStatus() {
        background("Checking Ollama", i -> {
            OllamaClient client = new OllamaClient(ollamaUrl.getText().trim());
            append(client.isRunning() ? "Ollama is running at " + client.baseUrl()
                    : "Ollama is NOT reachable at " + client.baseUrl() + " — install/start it from https://ollama.com");
        });
    }

    private void ollamaPull() {
        String model = String.valueOf(ollamaModel.getEditor().getItem()).trim();
        background("Downloading " + model, i -> {
            append("Pulling " + model + " ...");
            new OllamaClient(ollamaUrl.getText().trim()).pull(model, progressTo(i));
            append("Done: " + model);
        });
    }

    private void ollamaList() {
        background("Listing Ollama models", i ->
                append("Installed: " + new OllamaClient(ollamaUrl.getText().trim()).listModels()));
    }

    private void ollamaDelete() {
        String model = String.valueOf(ollamaModel.getEditor().getItem()).trim();
        background("Deleting " + model, i -> {
            new OllamaClient(ollamaUrl.getText().trim()).delete(model);
            append("Deleted " + model);
        });
    }

    private void llamaDownload() {
        background("Downloading GGUF model", i -> {
            String spec = ggufModel.getText().trim();
            append("Downloading " + ModelDownloader.resolveUrl(spec));
            Path path = new ModelDownloader(AiSettings.getApiKey("huggingface"))
                    .download(spec, Path.of(modelDir.getText().trim()), progressTo(i));
            append("Saved: " + path);
        });
    }

    private void llamaStart() {
        background("Starting llama-server", i -> {
            i.setText("Downloading model if needed and starting llama-server...");
            append("Starting llama-server on port " + port.getText().trim() + " ...");
            LocalModelService.getInstance().llamaCpp().prepare();
            append("llama-server is ready");
        });
    }

    private void llamaStop() {
        LocalModelService.getInstance().stop();
        append("llama-server stopped");
        updateLlamaStatus();
    }

    private void updateLlamaStatus() {
        boolean running = LocalModelService.getInstance().isServerRunning();
        ApplicationManager.getApplication().invokeLater(() -> llamaStatus.setText(running ? "running" : "stopped"));
    }
}
