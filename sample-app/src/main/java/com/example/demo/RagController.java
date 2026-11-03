package com.example.demo;

import io.aiplugin.core.AiClient;
import io.aiplugin.core.rag.RagService;
import io.aiplugin.core.rag.SearchResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** ai.rag.enabled=true 일 때 동작하는 RAG 예제 API. */
@RestController
public class RagController {

    private final ObjectProvider<RagService> rag;
    private final AiClient aiClient;
    private final PolicyAi policyAi;

    public RagController(ObjectProvider<RagService> rag, AiClient aiClient, PolicyAi policyAi) {
        this.rag = rag;
        this.aiClient = aiClient;
        this.policyAi = policyAi;
    }

    private RagService rag() {
        RagService service = rag.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("RAG is disabled. Set ai.rag.enabled=true");
        }
        return service;
    }

    /** PDF / TXT / MD 업로드 → 임베딩 → 벡터 DB 저장. */
    @PostMapping("/rag/documents")
    public RagService.IngestResult upload(@RequestPart("file") MultipartFile file) throws IOException {
        return rag().ingest("upload/" + file.getOriginalFilename(), file.getInputStream());
    }

    @GetMapping("/rag/documents")
    public Map<String, Long> documents() {
        return rag().store().sources();
    }

    @GetMapping("/rag/search")
    public List<Map<String, Object>> search(@RequestParam String q, @RequestParam(defaultValue = "4") int k) {
        return rag().search(q, k).stream().map((SearchResult r) -> Map.<String, Object>of(
                "score", r.score(), "source", r.chunk().source(), "content", r.chunk().content())).toList();
    }

    /** 벡터 DB 에서 찾은 문서를 근거로 답변 (AiClient 직접 사용). */
    @PostMapping("/rag/ask")
    public Map<String, String> ask(@RequestBody String question) {
        return Map.of("answer", aiClient.prompt(question).useKnowledge(false).text());
    }

    /** @AiService 방식: 정책 문서를 근거로 key-value 답변. */
    @PostMapping("/rag/policy")
    public Map<String, String> policy(@RequestBody String question) {
        return policyAi.answer(question);
    }
}
