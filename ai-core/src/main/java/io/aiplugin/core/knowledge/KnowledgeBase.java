package io.aiplugin.core.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 사전 학습 데이터 저장소.
 *
 * <p>API 로 제공되는 상용 모델은 가중치를 직접 사전학습(pre-training)할 수 없으므로, 이 클래스는 두 가지 방식으로
 * 도메인 데이터를 모델에 "학습"시킵니다.
 * <ol>
 *   <li><b>컨텍스트 주입</b>: 요청마다 질의와 유사한 예제(few-shot)와 문서를 골라 시스템 프롬프트에 넣습니다.
 *       모든 프로바이더에서 동작합니다.</li>
 *   <li><b>파인튜닝</b>: {@link FineTuneExporter} 로 예제를 JSONL 로 내보내고 {@link OpenAiFineTuneService} 로
 *       OpenAI 파인튜닝 작업을 생성합니다.</li>
 * </ol>
 *
 * <p>디렉터리 규칙: {@code *.jsonl} / {@code *.json} = 학습 예제, {@code *.md} / {@code *.txt} = 문서,
 * 파일명이 {@code instructions} 로 시작하는 문서는 항상 포함됩니다.
 */
public final class KnowledgeBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<TrainingExample> examples;
    private final List<KnowledgeDocument> documents;
    private final int maxExamples;
    private final int maxDocuments;
    private final int maxDocumentChars;

    private KnowledgeBase(Builder b) {
        this.examples = List.copyOf(b.examples);
        this.documents = List.copyOf(b.documents);
        this.maxExamples = b.maxExamples;
        this.maxDocuments = b.maxDocuments;
        this.maxDocumentChars = b.maxDocumentChars;
    }

    public static KnowledgeBase empty() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<TrainingExample> examples() {
        return examples;
    }

    public List<KnowledgeDocument> documents() {
        return documents;
    }

    public boolean isEmpty() {
        return examples.isEmpty() && documents.isEmpty();
    }

    /**
     * 질의와 관련된 문서/예제를 골라 시스템 프롬프트용 텍스트를 만듭니다.
     *
     * @param query 사용자 프롬프트
     * @param tag   null 이 아니면 해당 태그가 붙은 항목만 사용
     */
    public String buildContext(String query, String tag) {
        if (isEmpty()) {
            return "";
        }
        Set<String> queryGrams = grams(query);
        StringBuilder sb = new StringBuilder();

        List<KnowledgeDocument> docs = new ArrayList<>();
        documents.stream().filter(d -> d.alwaysInclude() && matchesTag(d.tags(), tag)).forEach(docs::add);
        documents.stream()
                .filter(d -> !d.alwaysInclude() && matchesTag(d.tags(), tag))
                .map(d -> new Scored<>(d, score(queryGrams, d.title() + " " + d.content())))
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingDouble((Scored<KnowledgeDocument> s) -> s.score).reversed())
                .limit(maxDocuments)
                .forEach(s -> docs.add(s.item));
        if (!docs.isEmpty()) {
            sb.append("Use the following reference knowledge when answering.\n");
            for (KnowledgeDocument d : docs) {
                String content = d.content().length() > maxDocumentChars
                        ? d.content().substring(0, maxDocumentChars) + "\n..." : d.content();
                sb.append("<document title=\"").append(d.title()).append("\">\n")
                        .append(content.strip()).append("\n</document>\n");
            }
        }

        List<TrainingExample> picked = examples.stream()
                .filter(e -> matchesTag(e.tags(), tag))
                .map(e -> new Scored<>(e, score(queryGrams, e.input())))
                .sorted(Comparator.comparingDouble((Scored<TrainingExample> s) -> s.score).reversed())
                .limit(maxExamples)
                .map(s -> s.item)
                .toList();
        if (!picked.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("Follow the style and output structure of these examples.\n");
            for (TrainingExample e : picked) {
                sb.append("<example>\n<input>\n").append(e.input().strip()).append("\n</input>\n<output>\n")
                        .append(e.outputText().strip()).append("\n</output>\n</example>\n");
            }
        }
        return sb.toString().strip();
    }

    private static boolean matchesTag(List<String> tags, String tag) {
        return tag == null || tag.isBlank() || tags.contains(tag);
    }

    /** 문자 bigram 겹침 기반 유사도. 형태소 분석 없이 한국어/영어 모두에 동작합니다. */
    private static double score(Set<String> queryGrams, String text) {
        Set<String> g = grams(text);
        if (g.isEmpty() || queryGrams.isEmpty()) {
            return 0;
        }
        int overlap = 0;
        for (String s : queryGrams) {
            if (g.contains(s)) {
                overlap++;
            }
        }
        return overlap / Math.sqrt(g.size());
    }

    private static Set<String> grams(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        String norm = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
        for (String word : norm.split(" ")) {
            if (word.length() == 1) {
                out.add(word);
            }
            for (int i = 0; i + 2 <= word.length(); i++) {
                out.add(word.substring(i, i + 2));
            }
        }
        return out;
    }

    private record Scored<T>(T item, double score) {
    }

    public static final class Builder {
        private final List<TrainingExample> examples = new ArrayList<>();
        private final List<KnowledgeDocument> documents = new ArrayList<>();
        private int maxExamples = 5;
        private int maxDocuments = 3;
        private int maxDocumentChars = 8000;

        public Builder addExample(TrainingExample example) {
            examples.add(example);
            return this;
        }

        public Builder addExample(String input, String output, String... tags) {
            return addExample(new TrainingExample(input, MAPPER.getNodeFactory().textNode(output), List.of(tags)));
        }

        public Builder addDocument(KnowledgeDocument document) {
            documents.add(document);
            return this;
        }

        public Builder addDocument(String title, String content) {
            return addDocument(new KnowledgeDocument(title, content, List.of(),
                    title.toLowerCase(Locale.ROOT).startsWith("instructions")));
        }

        public Builder maxExamples(int n) {
            this.maxExamples = n;
            return this;
        }

        public Builder maxDocuments(int n) {
            this.maxDocuments = n;
            return this;
        }

        public Builder maxDocumentChars(int n) {
            this.maxDocumentChars = n;
            return this;
        }

        /** 파일 이름 확장자에 따라 예제(.json/.jsonl) 또는 문서(.md/.txt)로 읽어들입니다. */
        public Builder load(String fileName, InputStream in) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            try (in) {
                if (lower.endsWith(".jsonl") || lower.endsWith(".json")) {
                    examples.addAll(KnowledgeFiles.readExamples(in, lower.endsWith(".jsonl")));
                } else if (lower.endsWith(".md") || lower.endsWith(".txt")) {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    String title = fileName.replaceFirst("\\.[^.]+$", "");
                    addDocument(title, content);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load knowledge file " + fileName, e);
            }
            return this;
        }

        public Builder loadDirectory(Path dir) {
            if (!Files.isDirectory(dir)) {
                return this;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                    load(p.getFileName().toString(), Files.newInputStream(p));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load knowledge directory " + dir, e);
            }
            return this;
        }

        public KnowledgeBase build() {
            return new KnowledgeBase(this);
        }
    }

    /** 학습 예제 파일 읽기/쓰기 도우미 (IntelliJ 플러그인에서도 사용). */
    public static final class KnowledgeFiles {

        private KnowledgeFiles() {
        }

        public static List<TrainingExample> readExamples(InputStream in, boolean jsonLines) throws IOException {
            List<TrainingExample> out = new ArrayList<>();
            if (jsonLines) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        out.add(toExample(MAPPER.readTree(line)));
                    }
                }
            } else {
                JsonNode root = MAPPER.readTree(in);
                if (root.isArray()) {
                    for (JsonNode n : root) {
                        out.add(toExample(n));
                    }
                } else if (root.isObject()) {
                    out.add(toExample(root));
                }
            }
            return out;
        }

        public static String toJsonLine(TrainingExample example) {
            try {
                return MAPPER.writeValueAsString(example);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static TrainingExample toExample(JsonNode n) {
            List<String> tags = new ArrayList<>();
            n.path("tags").forEach(t -> tags.add(t.asText()));
            return new TrainingExample(n.path("input").asText(), n.get("output"), tags);
        }
    }
}
