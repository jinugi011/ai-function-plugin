package io.aiplugin.idea.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Settings | Tools | AI Function Plugin */
public class AiSettingsConfigurable implements Configurable {

    /** PasswordSafe 에 저장하는 키: 클라우드 API 키 + Hugging Face 토큰. */
    private static final java.util.List<String> KEY_NAMES = java.util.List.of("claude", "openai", "abacus", "huggingface", "embedding");

    private final Map<String, JBPasswordField> keyFields = new HashMap<>();
    private final Map<String, String> loadedKeys = new HashMap<>();
    private final ComboBox<String> defaultProvider = new ComboBox<>(AiSettings.PROVIDERS.toArray(String[]::new));
    private final JBTextField claudeModel = new JBTextField();
    private final ComboBox<String> claudeEffort = new ComboBox<>(new String[]{"low", "medium", "high", "xhigh", "max"});
    private final JBTextField openaiModel = new JBTextField();
    private final JBTextField abacusModel = new JBTextField();
    private final JBTextField knowledgeDir = new JBTextField();
    private final JBTextField fineTuneBaseModel = new JBTextField();

    @Override
    public @Nls String getDisplayName() {
        return "AI Function Plugin";
    }

    @Override
    public JComponent createComponent() {
        for (String p : KEY_NAMES) {
            keyFields.put(p, new JBPasswordField());
        }
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Default provider:", defaultProvider)
                .addSeparator()
                .addLabeledComponent("Claude API key:", keyFields.get("claude"))
                .addLabeledComponent("Claude model:", claudeModel)
                .addLabeledComponent("Claude effort:", claudeEffort)
                .addSeparator()
                .addLabeledComponent("OpenAI API key:", keyFields.get("openai"))
                .addLabeledComponent("OpenAI model:", openaiModel)
                .addSeparator()
                .addLabeledComponent("Abacus.AI API key:", keyFields.get("abacus"))
                .addLabeledComponent("Abacus.AI model:", abacusModel)
                .addSeparator()
                .addLabeledComponent("Hugging Face token (gated GGUF models, optional):", keyFields.get("huggingface"))
                .addLabeledComponent("Embedding API key (openai-compatible, e.g. Voyage AI):", keyFields.get("embedding"))
                .addComponent(new JBLabel("Ollama / llama.cpp local models and RAG: AI Function tool window tabs."))
                .addSeparator()
                .addLabeledComponent("Knowledge directory (project-relative):", knowledgeDir)
                .addLabeledComponent("Fine-tune base model (OpenAI):", fineTuneBaseModel)
                .addComponent(new JBLabel("API keys are stored in the IDE password safe, not in project files."))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        AiSettings.State s = AiSettings.getInstance().getState();
        for (String p : KEY_NAMES) {
            if (!Objects.equals(new String(keyFields.get(p).getPassword()), loadedKeys.getOrDefault(p, ""))) {
                return true;
            }
        }
        return !Objects.equals(defaultProvider.getSelectedItem(), s.defaultProvider)
                || !claudeModel.getText().equals(s.claudeModel)
                || !Objects.equals(claudeEffort.getSelectedItem(), s.claudeEffort)
                || !openaiModel.getText().equals(s.openaiModel)
                || !abacusModel.getText().equals(s.abacusModel)
                || !knowledgeDir.getText().equals(s.knowledgeDir)
                || !fineTuneBaseModel.getText().equals(s.fineTuneBaseModel);
    }

    @Override
    public void apply() {
        AiSettings.State s = AiSettings.getInstance().getState();
        s.defaultProvider = (String) defaultProvider.getSelectedItem();
        s.claudeModel = claudeModel.getText().trim();
        s.claudeEffort = (String) claudeEffort.getSelectedItem();
        s.openaiModel = openaiModel.getText().trim();
        s.abacusModel = abacusModel.getText().trim();
        s.knowledgeDir = knowledgeDir.getText().trim();
        s.fineTuneBaseModel = fineTuneBaseModel.getText().trim();
        for (String p : KEY_NAMES) {
            String key = new String(keyFields.get(p).getPassword()).trim();
            if (!key.equals(loadedKeys.getOrDefault(p, ""))) {
                AiSettings.setApiKey(p, key);
                loadedKeys.put(p, key);
            }
        }
    }

    @Override
    public void reset() {
        AiSettings.State s = AiSettings.getInstance().getState();
        defaultProvider.setSelectedItem(s.defaultProvider);
        claudeModel.setText(s.claudeModel);
        claudeEffort.setSelectedItem(s.claudeEffort);
        openaiModel.setText(s.openaiModel);
        abacusModel.setText(s.abacusModel);
        knowledgeDir.setText(s.knowledgeDir);
        fineTuneBaseModel.setText(s.fineTuneBaseModel);
        for (String p : KEY_NAMES) {
            String key = AiSettings.getApiKey(p);
            loadedKeys.put(p, key == null ? "" : key);
            keyFields.get(p).setText(key == null ? "" : key);
        }
    }
}
