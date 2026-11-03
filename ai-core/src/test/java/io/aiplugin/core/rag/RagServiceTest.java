package io.aiplugin.core.rag;

import io.aiplugin.core.AiClient;
import io.aiplugin.core.AiRequest;
import io.aiplugin.core.AiResponse;
import io.aiplugin.core.AiException;
import io.aiplugin.core.provider.AiProvider;
import io.aiplugin.core.rag.document.DocumentLoader;
import io.aiplugin.core.rag.document.TextSplitter;
import io.aiplugin.core.rag.store.FileVectorStore;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagServiceTest {

    static void writePdf(Path file, String... pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(file.toFile());
        }
    }

    @Test
    void splitsWithOverlapAtSentenceBoundaries() {
        String text = "첫 번째 문장입니다. ".repeat(40) + "\n\n" + "두 번째 문단의 문장입니다. ".repeat(40);
        List<String> chunks = new TextSplitter(200, 40).split(text);
        assertTrue(chunks.size() > 3);
        chunks.forEach(c -> assertTrue(c.length() <= 200, c));
        assertTrue(chunks.get(0).endsWith("."));
    }

    @Test
    void loadsPdfPerPage(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("manual.pdf");
        writePdf(pdf, "Refund policy: refunds within 14 days", "Shipping takes 3 business days");
        var doc = DocumentLoader.load(pdf);
        assertEquals(2, doc.pages().size());
        assertTrue(doc.pages().get(1).contains("Shipping"));
    }

    @Test
    void ingestsSearchesPersistsAndInjectsIntoPrompt(@TempDir Path dir) throws IOException {
        Path docs = Files.createDirectories(dir.resolve("docs"));
        writePdf(docs.resolve("policy.pdf"), "Refund policy: refunds are accepted within 14 days of delivery",
                "Shipping policy: orders ship in 3 business days");
        Files.writeString(docs.resolve("faq.txt"), "회원 등급은 브론즈, 실버, 골드가 있습니다. 골드 회원은 무료 배송입니다.");
        Files.writeString(docs.resolve("ignored.bin"), "binary");

        Path storeFile = dir.resolve("store/vector-store.json.gz");
        HashingEmbedding embedding = new HashingEmbedding();
        RagService rag = new RagService(embedding, new FileVectorStore(storeFile, embedding.id()),
                new RagService.Options(300, 50, 2, 0.1));

        RagService.IngestResult first = rag.ingest(docs);
        assertEquals(2, first.documents());
        assertEquals(3, first.chunks());
        assertTrue(Files.exists(storeFile));

        // 바뀌지 않은 파일은 다시 임베딩하지 않음
        int calls = embedding.calls;
        assertEquals(2, rag.ingest(docs).skipped());
        assertEquals(calls, embedding.calls);

        // 바뀐 파일은 기존 조각을 교체
        Files.writeString(docs.resolve("faq.txt"), "골드 회원 혜택: 무료 배송과 5% 적립.");
        RagService.IngestResult changed = rag.ingest(docs);
        assertEquals(1, changed.documents());
        assertEquals(3, rag.store().count());

        SearchResult top = rag.search("refund within days", 1).get(0);
        assertTrue(top.chunk().source().endsWith("policy.pdf"));
        assertEquals("1", top.chunk().metadata().get("page"));

        // 파일에서 다시 열어도 같은 결과, 다른 임베딩 모델이면 거부
        FileVectorStore reopened = new FileVectorStore(storeFile, embedding.id());
        assertEquals(3, reopened.count());
        assertEquals(2, reopened.sources().size());
        assertThrows(AiException.class, () -> new FileVectorStore(storeFile, "openai/other"));

        // AiClient 에 연결하면 검색 결과가 시스템 프롬프트에 들어감
        List<AiRequest> requests = new ArrayList<>();
        AiProvider fake = new AiProvider() {
            public String name() {
                return "fake";
            }

            public AiResponse complete(AiRequest r) {
                requests.add(r);
                return new AiResponse("ok", "fake", "m", 0, 0);
            }
        };
        AiClient client = new AiClient(List.of(fake), null, null, null, rag);
        client.prompt("골드 회원 혜택이 뭐야?").text();
        assertTrue(requests.get(0).systemPrompt().contains("무료 배송과 5% 적립"));
        assertTrue(requests.get(0).systemPrompt().contains("faq.txt"));

        client.prompt("골드 회원 혜택이 뭐야?").useRag(false).text();
        assertEquals(null, requests.get(1).systemPrompt());

        rag.remove(docs.resolve("faq.txt").toString());
        assertFalse(rag.store().sources().containsKey(docs.resolve("faq.txt").toString()));
    }
}
