package io.aiplugin.idea.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.aiplugin.core.rag.RagService;
import io.aiplugin.core.rag.SearchResult;
import io.aiplugin.idea.RagProjectService;
import io.aiplugin.idea.settings.AiSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RAG 벡터 DB 구축: PDF/TXT/MD 파일을 임베딩해 파일 저장소 또는 Qdrant 에 저장하고, 검색을 시험해 봅니다.
 */
public class RagPanel extends JPanel {

    private final Project project;
    private final ComboBox<String> embeddingProvider = new ComboBox<>(new String[]{"openai", "ollama", "openai-compatible"});
    private final JBTextField embeddingModel = new JBTextField();
    private final JBTextField embeddingBaseUrl = new JBTextField();
    private final ComboBox<String> storeType = new ComboBox<>(new String[]{"file", "qdrant"});
    private final JBTextField filePath = new JBTextField();
    private final JBTextField qdrantUrl = new JBTextField();
    private final JBTextField qdrantCollection = new JBTextField();
    private final JBTextField chunkSize = new JBTextField();
    private final JBTextField topK = new JBTextField();
    private final DefaultTableModel sourcesModel = new DefaultTableModel(new Object[]{"Document", "Chunks"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JBTable sourcesTable = new JBTable(sourcesModel);
    private final JBTextField query = new JBTextField();
    private final JBTextArea output = new JBTextArea();
    private final JBLabel status = new JBLabel(" ");

    public RagPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        setBorder(JBUI.Borders.empty(6));
        output.setEditable(false);
        output.setLineWrap(true);

        AiSettings.State s = AiSettings.getInstance().getState();
        embeddingProvider.setSelectedItem(s.ragEmbeddingProvider);
        embeddingModel.setText(s.ragEmbeddingModel);
        embeddingModel.getEmptyText().setText("default: text-embedding-3-small / bge-m3");
        embeddingBaseUrl.setText(s.ragEmbeddingBaseUrl);
        embeddingBaseUrl.getEmptyText().setText("optional, e.g. https://api.voyageai.com/v1");
        storeType.setSelectedItem(s.ragStoreType);
        filePath.setText(s.ragFilePath);
        qdrantUrl.setText(s.ragQdrantUrl);
        qdrantCollection.setText(s.ragQdrantCollection);
        chunkSize.setText(String.valueOf(s.ragChunkSize));
        topK.setText(String.valueOf(s.ragTopK));

        JPanel config = FormBuilder.createFormBuilder()
                .addLabeledComponent("Embedding:", row(embeddingProvider, new JBLabel("Model:"), embeddingModel))
                .addLabeledComponent("Embedding URL:", embeddingBaseUrl)
                .addLabeledComponent("Vector DB:", row(storeType, new JBLabel("Chunk size:"), chunkSize,
                        new JBLabel("Top K:"), topK))
                .addLabeledComponent("File store (project-relative):", filePath)
                .addLabeledComponent("Qdrant:", row(qdrantUrl, new JBLabel("Collection:"), qdrantCollection))
                .addComponent(row(
                        button("Add Files / Folders…", this::addDocuments),
                        button("Remove Selected", this::removeSelected),
                        button("Refresh", this::refresh),
                        status))
                .getPanel();

        JPanel search = new JPanel(new BorderLayout(4, 0));
        query.getEmptyText().setText("Test a search query, e.g. 환불 규정");
        query.addActionListener(e -> search());
        search.add(query, BorderLayout.CENTER);
        search.add(button("Search", this::search), BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.add(search, BorderLayout.NORTH);
        bottom.add(new JBScrollPane(output), BorderLayout.CENTER);

        JBSplitter splitter = new JBSplitter(true, 0.45f);
        splitter.setFirstComponent(new JBScrollPane(sourcesTable));
        splitter.setSecondComponent(bottom);

        add(config, BorderLayout.NORTH);
        add(splitter, BorderLayout.CENTER);
        refresh();
    }

    private static JPanel row(JComponent... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (JComponent c : components) {
            p.add(c);
        }
        return p;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void saveSettings() {
        AiSettings.State s = AiSettings.getInstance().getState();
        s.ragEmbeddingProvider = (String) embeddingProvider.getSelectedItem();
        s.ragEmbeddingModel = embeddingModel.getText().trim();
        s.ragEmbeddingBaseUrl = embeddingBaseUrl.getText().trim();
        s.ragStoreType = (String) storeType.getSelectedItem();
        s.ragFilePath = filePath.getText().trim();
        s.ragQdrantUrl = qdrantUrl.getText().trim();
        s.ragQdrantCollection = qdrantCollection.getText().trim();
        s.ragChunkSize = parse(chunkSize, s.ragChunkSize);
        s.ragTopK = parse(topK, s.ragTopK);
    }

    private static int parse(JBTextField field, int fallback) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            field.setText(String.valueOf(fallback));
            return fallback;
        }
    }

    private void background(String title, Consumer<ProgressIndicator> work) {
        saveSettings();
        new Task.Backgroundable(project, title, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                work.accept(indicator);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                status.setText("Failed");
                Messages.showErrorDialog(project, error.getMessage(), "RAG");
            }
        }.queue();
    }

    private void addDocuments() {
        VirtualFile[] files = FileChooser.chooseFiles(
                FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
                        .withTitle("Select PDF / TXT / MD Files or Folders"), project, null);
        if (files.length == 0) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        for (VirtualFile f : files) {
            paths.add(f.toNioPath());
        }
        background("Building vector DB", indicator -> {
            RagProjectService service = RagProjectService.getInstance(project);
            RagService rag = service.rag();
            int docs = 0, chunks = 0, skipped = 0;
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < paths.size(); i++) {
                indicator.checkCanceled();
                indicator.setFraction((double) i / paths.size());
                Path path = paths.get(i);
                RagService.IngestResult r = ingest(rag, service, path, indicator);
                docs += r.documents();
                chunks += r.chunks();
                skipped += r.skipped();
                errors.addAll(r.errors());
            }
            rag.store().flush();
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(service.storeFile());
            String summary = "Indexed " + docs + " documents (" + chunks + " chunks), " + skipped + " unchanged"
                    + (errors.isEmpty() ? "" : "\nErrors:\n" + String.join("\n", errors));
            ApplicationManager.getApplication().invokeLater(() -> output.setText(summary));
            refresh();
        });
    }

    /** 폴더는 하위 파일을 각각 프로젝트 기준 이름으로 색인합니다. */
    private static RagService.IngestResult ingest(RagService rag, RagProjectService service, Path path,
                                                  ProgressIndicator indicator) {
        List<Path> files = new ArrayList<>();
        if (java.nio.file.Files.isDirectory(path)) {
            try (var walk = java.nio.file.Files.walk(path)) {
                walk.filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> io.aiplugin.core.rag.document.DocumentLoader.isSupported(p.getFileName().toString()))
                        .sorted().forEach(files::add);
            } catch (java.io.IOException e) {
                return new RagService.IngestResult(0, 0, 0, List.of(path + ": " + e.getMessage()));
            }
        } else {
            files.add(path);
        }
        int docs = 0, chunks = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        for (Path file : files) {
            indicator.checkCanceled();
            indicator.setText("Embedding " + file.getFileName());
            try {
                var loaded = io.aiplugin.core.rag.document.DocumentLoader.load(file);
                var doc = new io.aiplugin.core.rag.document.LoadedDocument(service.sourceName(file),
                        loaded.pages(), loaded.hash());
                RagService.IngestResult r = rag.ingest(doc);
                docs += r.documents();
                chunks += r.chunks();
                skipped += r.skipped();
            } catch (RuntimeException e) {
                errors.add(file.getFileName() + ": " + e.getMessage());
            }
        }
        return new RagService.IngestResult(docs, chunks, skipped, errors);
    }

    private void removeSelected() {
        int[] rows = sourcesTable.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        List<String> sources = new ArrayList<>();
        for (int r : rows) {
            sources.add((String) sourcesModel.getValueAt(sourcesTable.convertRowIndexToModel(r), 0));
        }
        background("Removing documents", indicator -> {
            RagService rag = RagProjectService.getInstance(project).rag();
            sources.forEach(rag::remove);
            refresh();
        });
    }

    /** 설정이 아직 없을 수 있으므로(API 키 등) 오류는 대화상자 대신 상태 표시줄에 보여줍니다. */
    private void refresh() {
        background("Loading vector DB", indicator -> {
            RagService rag;
            try {
                rag = RagProjectService.getInstance(project).rag();
            } catch (RuntimeException e) {
                ApplicationManager.getApplication().invokeLater(() -> status.setText(e.getMessage()));
                return;
            }
            Map<String, Long> sources = rag.store().sources();
            long total = rag.store().count();
            String text = total + " chunks · " + rag.embedding().id();
            ApplicationManager.getApplication().invokeLater(() -> {
                sourcesModel.setRowCount(0);
                sources.forEach((src, n) -> sourcesModel.addRow(new Object[]{src, n}));
                status.setText(text);
            });
        });
    }

    private void search() {
        String q = query.getText().trim();
        if (q.isEmpty()) {
            return;
        }
        background("Searching vector DB", indicator -> {
            RagService rag = RagProjectService.getInstance(project).rag();
            List<SearchResult> hits = rag.search(q, AiSettings.getInstance().getState().ragTopK);
            StringBuilder sb = new StringBuilder(hits.isEmpty() ? "No results (try lowering similarity or adding documents)\n" : "");
            for (SearchResult hit : hits) {
                sb.append(String.format("[%.3f] %s", hit.score(), hit.chunk().source()));
                if (hit.chunk().metadata().containsKey("page")) {
                    sb.append(" p.").append(hit.chunk().metadata().get("page"));
                }
                sb.append('\n').append(hit.chunk().content()).append("\n\n");
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                output.setText(sb.toString());
                output.setCaretPosition(0);
            });
        });
    }
}
