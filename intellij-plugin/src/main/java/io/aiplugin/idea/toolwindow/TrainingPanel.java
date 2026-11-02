package io.aiplugin.idea.toolwindow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import io.aiplugin.core.knowledge.FineTuneExporter;
import io.aiplugin.core.knowledge.KnowledgeBase;
import io.aiplugin.core.knowledge.OpenAiFineTuneService;
import io.aiplugin.core.knowledge.TrainingExample;
import io.aiplugin.idea.AiClients;
import io.aiplugin.idea.settings.AiSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 사전 학습 데이터(ai-knowledge/examples.jsonl) 편집기.
 * 저장된 예제는 Spring 앱 실행 시 few-shot 컨텍스트로 자동 사용되며, OpenAI 파인튜닝으로 보낼 수도 있습니다.
 */
public class TrainingPanel extends JPanel {

    static final String EXAMPLES_FILE = "examples.jsonl";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Project project;
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Input (prompt)", "Output (expected)", "Tags"}, 0);
    private final JBTable table = new JBTable(model);
    private final JBLabel info = new JBLabel(" ");

    public TrainingPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        setBorder(JBUI.Borders.empty(6));

        JButton add = new JButton("Add");
        add.addActionListener(e -> model.addRow(new Object[]{"", "", ""}));
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            int[] rows = table.getSelectedRows();
            for (int i = rows.length - 1; i >= 0; i--) {
                model.removeRow(table.convertRowIndexToModel(rows[i]));
            }
        });
        JButton reload = new JButton("Reload");
        reload.addActionListener(e -> load());
        JButton save = new JButton("Save");
        save.addActionListener(e -> save());
        JButton export = new JButton("Export OpenAI JSONL");
        export.addActionListener(e -> exportJsonl());
        JButton fineTune = new JButton("Start OpenAI Fine-tune");
        fineTune.addActionListener(e -> startFineTune());
        JButton jobStatus = new JButton("Fine-tune Status");
        jobStatus.addActionListener(e -> checkJob());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JButton b : List.of(add, remove, reload, save, export, fineTune, jobStatus)) {
            buttons.add(b);
        }
        add(buttons, BorderLayout.NORTH);
        add(new JBScrollPane(table), BorderLayout.CENTER);
        add(info, BorderLayout.SOUTH);
        load();
    }

    public void addExample(String input, String output, String tags) {
        model.addRow(new Object[]{input, output, tags});
        save();
    }

    private Path examplesPath() {
        return AiClients.knowledgeDir(project).resolve(EXAMPLES_FILE);
    }

    private void load() {
        model.setRowCount(0);
        Path path = examplesPath();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                for (TrainingExample e : KnowledgeBase.KnowledgeFiles.readExamples(in, true)) {
                    model.addRow(new Object[]{e.input(), e.outputText(), String.join(",", e.tags())});
                }
            } catch (IOException ex) {
                Messages.showErrorDialog(project, "Failed to read " + path + ": " + ex.getMessage(), "AI Function");
            }
        }
        KnowledgeBase kb = AiClients.loadKnowledge(project);
        info.setText(model.getRowCount() + " examples in " + EXAMPLES_FILE + " · "
                + kb.examples().size() + " examples / " + kb.documents().size()
                + " documents in " + AiSettings.getInstance().getState().knowledgeDir);
    }

    private List<TrainingExample> examples() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        List<TrainingExample> out = new ArrayList<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            String input = String.valueOf(model.getValueAt(r, 0)).strip();
            String output = String.valueOf(model.getValueAt(r, 1)).strip();
            if (input.isEmpty() || output.isEmpty()) {
                continue;
            }
            List<String> tags = Arrays.stream(String.valueOf(model.getValueAt(r, 2)).split(","))
                    .map(String::strip).filter(s -> !s.isEmpty()).toList();
            out.add(new TrainingExample(input, toJson(output), tags));
        }
        return out;
    }

    /** 출력이 JSON 이면 JSON 그대로, 아니면 문자열로 저장합니다. */
    private static JsonNode toJson(String output) {
        if (output.startsWith("{") || output.startsWith("[")) {
            try {
                return MAPPER.readTree(output);
            } catch (IOException ignored) {
                // 일반 텍스트로 저장
            }
        }
        return MAPPER.getNodeFactory().textNode(output);
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (TrainingExample e : examples()) {
            sb.append(KnowledgeBase.KnowledgeFiles.toJsonLine(e)).append('\n');
        }
        write(examplesPath(), sb.toString());
        load();
    }

    private void exportJsonl() {
        Path out = AiClients.knowledgeDir(project).resolveSibling("openai-finetune.jsonl");
        write(out, FineTuneExporter.toOpenAiJsonl(examples(), null));
        notify("Exported " + out, NotificationType.INFORMATION);
    }

    private void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        } catch (IOException ex) {
            Messages.showErrorDialog(project, "Failed to write " + path + ": " + ex.getMessage(), "AI Function");
        }
    }

    private void startFineTune() {
        List<TrainingExample> examples = examples();
        String baseModel = Messages.showInputDialog(project, "Base model to fine-tune:", "OpenAI Fine-tune",
                null, AiSettings.getInstance().getState().fineTuneBaseModel, null);
        if (baseModel == null || baseModel.isBlank()) {
            return;
        }
        new Task.Backgroundable(project, "Starting OpenAI fine-tune", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                String key = AiSettings.getApiKey("openai");
                if (key == null) {
                    throw new IllegalStateException("OpenAI API key is not configured");
                }
                JsonNode job = new OpenAiFineTuneService(key).startFineTune(examples, null, baseModel.trim(), null);
                AiFunctionNotifier.notify(project, "Fine-tune job " + job.path("id").asText() + " created (status: "
                        + job.path("status").asText() + "). When it succeeds, set the resulting ft:... model "
                        + "as the OpenAI model.", NotificationType.INFORMATION);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                TrainingPanel.this.notify(error.getMessage(), NotificationType.ERROR);
            }
        }.queue();
    }

    private void checkJob() {
        String jobId = Messages.showInputDialog(project, "Fine-tune job id (ftjob-...):", "OpenAI Fine-tune", null);
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        new Task.Backgroundable(project, "Checking fine-tune job", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                JsonNode job = new OpenAiFineTuneService(AiSettings.getApiKey("openai")).getJob(jobId.trim());
                String model = job.path("fine_tuned_model").asText("");
                AiFunctionNotifier.notify(project, "Status: " + job.path("status").asText()
                        + (model.isEmpty() ? "" : " · model: " + model), NotificationType.INFORMATION);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                TrainingPanel.this.notify(error.getMessage(), NotificationType.ERROR);
            }
        }.queue();
    }

    private void notify(String message, NotificationType type) {
        AiFunctionNotifier.notify(project, message, type);
    }

    static final class AiFunctionNotifier {
        static void notify(Project project, String message, NotificationType type) {
            NotificationGroupManager.getInstance().getNotificationGroup("AI Function Plugin")
                    .createNotification(message, type).notify(project);
        }
    }
}
