package io.aiplugin.idea.toolwindow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.aiplugin.core.AiClient;
import io.aiplugin.core.AiResult;
import io.aiplugin.core.ResponseFormat;
import io.aiplugin.idea.AiClients;
import io.aiplugin.idea.settings.AiSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.util.Map;
import java.util.stream.Collectors;

/** 프롬프트를 입력하고 TEXT / JSON / KEY_VALUE 형식으로 결과를 받는 패널. */
public class PromptPanel extends JPanel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Project project;
    private final TrainingPanel trainingPanel;
    private final ComboBox<String> provider = new ComboBox<>(AiSettings.PROVIDERS.toArray(String[]::new));
    private final ComboBox<ResponseFormat> format = new ComboBox<>(ResponseFormat.values());
    private final JBTextField model = new JBTextField();
    private final JBTextArea system = new JBTextArea(3, 40);
    private final JBTextArea prompt = new JBTextArea(8, 40);
    private final JBTextArea result = new JBTextArea();
    private final JBCheckBox useKnowledge = new JBCheckBox("Use project knowledge (ai-knowledge)", true);
    private final JBCheckBox useRag = new JBCheckBox("Use RAG (vector DB)", false);
    private final JBLabel status = new JBLabel(" ");
    private final JButton run = new JButton("Run");

    public PromptPanel(Project project, TrainingPanel trainingPanel) {
        super(new BorderLayout());
        this.project = project;
        this.trainingPanel = trainingPanel;

        provider.setSelectedItem(AiSettings.getInstance().getState().defaultProvider);
        format.setSelectedItem(ResponseFormat.JSON);
        model.getEmptyText().setText("default model from settings");
        system.getEmptyText().setText("Optional system instructions");
        prompt.getEmptyText().setText("Type a prompt, e.g. '홍길동(30세, 서울) 정보를 name/age/city 로 추출'");
        prompt.setLineWrap(true);
        system.setLineWrap(true);
        result.setEditable(false);
        result.setLineWrap(true);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        options.add(new JBLabel("Provider:"));
        options.add(provider);
        options.add(new JBLabel("Format:"));
        options.add(format);
        options.add(useKnowledge);
        options.add(useRag);

        JPanel input = FormBuilder.createFormBuilder()
                .addComponent(options)
                .addLabeledComponent("Model:", model)
                .addLabeledComponent("System:", new JBScrollPane(system), true)
                .addLabeledComponent("Prompt:", new JBScrollPane(prompt), true)
                .getPanel();

        JButton copy = new JButton("Copy Result");
        copy.addActionListener(e -> CopyPasteManager.getInstance().setContents(new StringSelection(result.getText())));
        JButton saveExample = new JButton("Save as Training Example");
        saveExample.addActionListener(e -> saveAsExample());
        run.addActionListener(e -> runPrompt());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(run);
        buttons.add(copy);
        buttons.add(saveExample);
        buttons.add(status);

        JPanel top = new JPanel(new BorderLayout());
        top.add(input, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createTitledBorder("Result"));
        bottom.add(new JBScrollPane(result), BorderLayout.CENTER);

        JBSplitter splitter = new JBSplitter(true, 0.55f);
        splitter.setFirstComponent(top);
        splitter.setSecondComponent(bottom);
        setBorder(JBUI.Borders.empty(6));
        add(splitter, BorderLayout.CENTER);
    }

    public JComponent getPreferredFocus() {
        return prompt;
    }

    public void setPrompt(String text) {
        prompt.setText(text);
        prompt.requestFocusInWindow();
    }

    private void runPrompt() {
        String promptText = prompt.getText();
        if (promptText.isBlank()) {
            Messages.showWarningDialog(project, "Enter a prompt first.", "AI Function");
            return;
        }
        String providerName = (String) provider.getSelectedItem();
        ResponseFormat fmt = (ResponseFormat) format.getSelectedItem();
        String modelName = model.getText().isBlank() ? null : model.getText().trim();
        String systemText = system.getText().isBlank() ? null : system.getText();
        boolean knowledge = useKnowledge.isSelected();
        boolean rag = useRag.isSelected();

        run.setEnabled(false);
        status.setText("Running...");
        new Task.Backgroundable(project, "Calling " + providerName, true) {
            private String output;
            private String info;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                AiClient client = AiClients.create(project, knowledge, rag);
                AiResult r = client.prompt(promptText).provider(providerName).model(modelName)
                        .system(systemText).useKnowledge(knowledge).call(fmt);
                output = render(r, fmt);
                info = r.response().model() + " · in " + r.response().inputTokens()
                        + " / out " + r.response().outputTokens() + " tokens";
            }

            @Override
            public void onSuccess() {
                result.setText(output);
                result.setCaretPosition(0);
                status.setText(info);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                status.setText("Failed");
                result.setText(error.getMessage());
            }

            @Override
            public void onFinished() {
                run.setEnabled(true);
            }
        }.queue();
    }

    private static String render(AiResult r, ResponseFormat fmt) {
        try {
            return switch (fmt) {
                case TEXT -> r.text();
                case JSON -> MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(r.json());
                case KEY_VALUE -> {
                    Map<String, String> kv = r.keyValue();
                    yield kv.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue())
                            .collect(Collectors.joining("\n"));
                }
            };
        } catch (Exception e) {
            // 파싱에 실패하면 원문을 보여줍니다
            return "[" + e.getMessage() + "]\n\n" + r.text();
        }
    }

    private void saveAsExample() {
        if (prompt.getText().isBlank() || result.getText().isBlank()) {
            Messages.showWarningDialog(project, "Run a prompt first.", "AI Function");
            return;
        }
        String output = format.getSelectedItem() == ResponseFormat.KEY_VALUE
                ? keyValueTextToJson(result.getText()) : result.getText();
        trainingPanel.addExample(prompt.getText(), output, "");
    }

    private static String keyValueTextToJson(String text) {
        var node = MAPPER.createObjectNode();
        for (String line : text.split("\\R")) {
            int i = line.indexOf(" = ");
            if (i > 0) {
                node.put(line.substring(0, i), line.substring(i + 3));
            }
        }
        return node.toString();
    }
}
