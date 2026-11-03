package io.aiplugin.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** {@code ai.rag.*} — 문서 임베딩 + 벡터 DB 검색(RAG) 설정. */
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

    private boolean enabled = false;
    /** 조각 최대 글자 수. */
    private int chunkSize = 800;
    private int chunkOverlap = 100;
    /** 프롬프트에 넣을 검색 결과 수. */
    private int topK = 4;
    /** 이 유사도(0~1) 미만 결과는 버림. */
    private double minScore = 0.3;
    /** 시작 시 documents 위치의 문서를 색인 (바뀌지 않은 문서는 건너뜀). */
    private boolean ingestOnStartup = true;
    /** 색인할 문서 위치 (Spring 리소스 패턴). 예: classpath*:rag-docs/**, file:./docs/** */
    private List<String> documents = new ArrayList<>(List.of("classpath*:rag-docs/**"));

    private final Embedding embedding = new Embedding();
    private final Store store = new Store();

    public static class Embedding {
        /** openai | ollama | openai-compatible (Voyage AI, llama.cpp 등). */
        private String provider = "openai";
        /** 비우면 openai: text-embedding-3-small, ollama: bge-m3. */
        private String model;
        /** openai-compatible 엔드포인트 (예: https://api.voyageai.com/v1) 또는 Ollama 주소. */
        private String baseUrl;
        /** 비우면 openai 는 ai.openai.api-key 를 사용. */
        private String apiKey;
        private int batchSize = 64;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class Store {
        /** file | qdrant | pgvector. */
        private String type = "file";
        /** file: 저장 파일 (.gz 이면 압축). "classpath:..." 이면 읽기 전용 (IntelliJ 에서 미리 만든 저장소). */
        private String filePath = "./ai-rag/vector-store.json.gz";
        private String qdrantUrl = "http://localhost:6333";
        private String qdrantCollection = "ai_documents";
        private String qdrantApiKey;
        /** pgvector: 애플리케이션의 DataSource 를 사용합니다. */
        private String pgvectorTable = "ai_documents";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getQdrantUrl() {
            return qdrantUrl;
        }

        public void setQdrantUrl(String qdrantUrl) {
            this.qdrantUrl = qdrantUrl;
        }

        public String getQdrantCollection() {
            return qdrantCollection;
        }

        public void setQdrantCollection(String qdrantCollection) {
            this.qdrantCollection = qdrantCollection;
        }

        public String getQdrantApiKey() {
            return qdrantApiKey;
        }

        public void setQdrantApiKey(String qdrantApiKey) {
            this.qdrantApiKey = qdrantApiKey;
        }

        public String getPgvectorTable() {
            return pgvectorTable;
        }

        public void setPgvectorTable(String pgvectorTable) {
            this.pgvectorTable = pgvectorTable;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public boolean isIngestOnStartup() {
        return ingestOnStartup;
    }

    public void setIngestOnStartup(boolean ingestOnStartup) {
        this.ingestOnStartup = ingestOnStartup;
    }

    public List<String> getDocuments() {
        return documents;
    }

    public void setDocuments(List<String> documents) {
        this.documents = documents;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public Store getStore() {
        return store;
    }
}
