package io.aiplugin.idea.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import io.aiplugin.idea.toolwindow.AiToolWindowFactory;
import io.aiplugin.idea.toolwindow.PromptPanel;
import org.jetbrains.annotations.NotNull;

/** 에디터에서 선택한 텍스트를 AI Function 툴윈도우의 프롬프트로 보냅니다. */
public class SendSelectionToPromptAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(editor != null && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        String selected = editor.getSelectionModel().getSelectedText();
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(AiToolWindowFactory.ID);
        if (window == null || selected == null) {
            return;
        }
        window.activate(() -> {
            for (Content content : window.getContentManager().getContents()) {
                if (content.getComponent() instanceof PromptPanel panel) {
                    window.getContentManager().setSelectedContent(content);
                    panel.setPrompt(selected);
                }
            }
        });
    }
}
