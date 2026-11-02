package io.aiplugin.idea.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import io.aiplugin.idea.settings.AiSettings;
import org.jetbrains.annotations.NotNull;

/** New | AI Service Interface — @AiService 인터페이스 템플릿을 생성합니다. */
public class NewAiServiceAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        e.getPresentation().setEnabledAndVisible(e.getProject() != null
                && (element instanceof PsiDirectory || e.getData(LangDataKeys.IDE_VIEW) != null));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        var view = e.getData(LangDataKeys.IDE_VIEW);
        PsiDirectory dir = view != null ? view.getOrChooseDirectory() : null;
        if (project == null || dir == null) {
            return;
        }
        String name = Messages.showInputDialog(project, "Interface name:", "New AI Service", null, "MyAiService", null);
        if (name == null || !name.trim().matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return;
        }
        String className = name.trim();
        if (dir.findFile(className + ".java") != null) {
            Messages.showErrorDialog(project, className + ".java already exists.", "New AI Service");
            return;
        }
        VirtualFile root = ProjectFileIndex.getInstance(project).getSourceRootForFile(dir.getVirtualFile());
        String relative = root == null ? null : VfsUtilCore.getRelativePath(dir.getVirtualFile(), root, '.');
        String packageName = relative == null ? "" : relative;
        String provider = AiSettings.getInstance().getState().defaultProvider;

        String source = (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n") + """
                import io.aiplugin.spring.annotation.AiPrompt;
                import io.aiplugin.spring.annotation.AiService;

                import java.util.Map;

                @AiService(provider = "%s")
                public interface %s {

                    /** 반환 타입이 record/클래스이면 JSON 응답이 해당 타입으로 매핑됩니다. */
                    record Result(String summary, String category) {
                    }

                    @AiPrompt("다음 내용을 요약하고 분류해줘:\\n{text}")
                    Result analyze(String text);

                    /** 반환 타입이 Map 이면 key-value 로 받습니다. */
                    @AiPrompt("다음 내용에서 핵심 속성을 key-value 로 추출해줘:\\n{text}")
                    Map<String, String> extract(String text);

                    /** 템플릿이 없으면 String 파라미터가 그대로 프롬프트가 됩니다. */
                    @AiPrompt
                    String ask(String prompt);
                }
                """.formatted(provider, className);

        WriteCommandAction.runWriteCommandAction(project, "Create AI Service", null, () -> {
            PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(className + ".java",
                    FileTypeManager.getInstance().getFileTypeByExtension("java"), source);
            PsiElement added = dir.add(file);
            if (added instanceof PsiFile created) {
                created.navigate(true);
            }
        });
    }
}
