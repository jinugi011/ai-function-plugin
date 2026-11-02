package io.aiplugin.idea.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class AiToolWindowFactory implements ToolWindowFactory, DumbAware {

    public static final String ID = "AI Function";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory factory = ContentFactory.getInstance();
        TrainingPanel training = new TrainingPanel(project);
        PromptPanel prompt = new PromptPanel(project, training);

        Content promptContent = factory.createContent(prompt, "Prompt", false);
        promptContent.setPreferredFocusableComponent(prompt.getPreferredFocus());
        toolWindow.getContentManager().addContent(promptContent);
        toolWindow.getContentManager().addContent(factory.createContent(training, "Training Data", false));
        toolWindow.getContentManager().addContent(factory.createContent(new LocalModelsPanel(project), "Local Models", false));
    }
}
