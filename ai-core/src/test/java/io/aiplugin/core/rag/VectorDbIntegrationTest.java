package io.aiplugin.core.rag;

import io.aiplugin.core.rag.store.PgVectorStore;
import io.aiplugin.core.rag.store.QdrantVectorStore;
import io.aiplugin.core.rag.store.VectorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 벡터 DB 통합 테스트. 실행:
 * <pre>
 * docker run -d -p 6333:6333 qdrant/qdrant
 * docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=postgres pgvector/pgvector:pg17
 * QDRANT_URL=http://localhost:6333 PGVECTOR_URL=jdbc:postgresql://localhost:5432/postgres ./gradlew :ai-core:test
 * </pre>
 */
class VectorDbIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "QDRANT_URL", matches = ".+")
    void qdrant() {
        exercise(new QdrantVectorStore(System.getenv("QDRANT_URL"), "test_" + UUID.randomUUID(), null));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PGVECTOR_URL", matches = ".+")
    void pgvector() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(System.getenv("PGVECTOR_URL"));
        ds.setUser("postgres");
        ds.setPassword("postgres");
        exercise(new PgVectorStore(ds, "rag_test_" + UUID.randomUUID().toString().substring(0, 8)));
    }

    private void exercise(VectorStore store) {
        HashingEmbedding embedding = new HashingEmbedding();
        RagService rag = new RagService(embedding, store, new RagService.Options(200, 20, 2, 0.1));
        assertEquals(0, store.count());
        assertTrue(rag.search("anything", 3).isEmpty());

        rag.ingestText("refund.txt", "환불은 배송 완료 후 14일 이내에 가능합니다.", java.util.Map.of("category", "policy"));
        rag.ingestText("shipping.txt", "배송은 영업일 기준 3일 걸립니다.", java.util.Map.of());
        assertEquals(2, store.count());
        assertEquals(1, rag.ingestText("refund.txt", "환불은 배송 완료 후 14일 이내에 가능합니다.", java.util.Map.of()).skipped());

        List<SearchResult> hits = rag.search("환불 가능 기간", 1);
        assertEquals("refund.txt", hits.get(0).chunk().source());
        assertEquals("policy", hits.get(0).chunk().metadata().get("category"));
        assertTrue(hits.get(0).score() > 0.1);

        rag.remove("refund.txt");
        assertEquals(1, store.count());
        assertFalse(store.containsSource("refund.txt", "x"));
    }
}
