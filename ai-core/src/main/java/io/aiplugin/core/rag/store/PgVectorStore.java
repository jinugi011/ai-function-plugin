package io.aiplugin.core.rag.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aiplugin.core.AiException;
import io.aiplugin.core.rag.Chunk;
import io.aiplugin.core.rag.SearchResult;
import io.aiplugin.core.rag.embedding.Vectors;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL + pgvector 저장소. 테이블과 HNSW 인덱스는 첫 저장 시 자동 생성합니다.
 * PostgreSQL JDBC 드라이버(org.postgresql:postgresql)가 클래스패스에 있어야 합니다.
 * 로컬 실행: {@code docker run -p 5432:5432 -e POSTGRES_PASSWORD=postgres pgvector/pgvector:pg17}
 */
public class PgVectorStore implements VectorStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;
    private final String table;
    private volatile boolean tableReady;

    public PgVectorStore(DataSource dataSource, String table) {
        if (!table.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }
        this.dataSource = dataSource;
        this.table = table;
    }

    @Override
    public void upsert(List<Chunk> chunks, List<float[]> vectors) {
        if (chunks.isEmpty()) {
            return;
        }
        ensureTable(vectors.get(0).length);
        String sql = "INSERT INTO " + table + " (id, source, chunk_index, content, metadata, embedding) "
                + "VALUES (?, ?, ?, ?, ?::jsonb, ?::vector) ON CONFLICT (id) DO UPDATE SET source = EXCLUDED.source, "
                + "chunk_index = EXCLUDED.chunk_index, content = EXCLUDED.content, metadata = EXCLUDED.metadata, "
                + "embedding = EXCLUDED.embedding";
        try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < chunks.size(); i++) {
                Chunk c = chunks.get(i);
                ps.setString(1, c.id());
                ps.setString(2, c.source());
                ps.setInt(3, c.index());
                ps.setString(4, c.content());
                ps.setString(5, MAPPER.writeValueAsString(c.metadata()));
                ps.setString(6, Vectors.toLiteral(vectors.get(i)));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AiException("pgvector upsert failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SearchResult> search(float[] query, int topK, double minScore) {
        if (!tableExists()) {
            return List.of();
        }
        String sql = "SELECT id, source, chunk_index, content, metadata, 1 - (embedding <=> ?::vector) AS score FROM "
                + table + " ORDER BY embedding <=> ?::vector LIMIT ?";
        List<SearchResult> out = new ArrayList<>();
        try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            String literal = Vectors.toLiteral(query);
            ps.setString(1, literal);
            ps.setString(2, literal);
            ps.setInt(3, topK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double score = rs.getDouble("score");
                    if (score < minScore) {
                        continue;
                    }
                    Map<String, String> meta = MAPPER.readValue(rs.getString("metadata"), new TypeReference<>() {
                    });
                    out.add(new SearchResult(new Chunk(rs.getString("id"), rs.getString("source"),
                            rs.getInt("chunk_index"), rs.getString("content"), meta), score));
                }
            }
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AiException("pgvector search failed: " + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public void deleteSource(String source) {
        if (tableExists()) {
            update("DELETE FROM " + table + " WHERE source = ?", source);
        }
    }

    @Override
    public boolean containsSource(String source, String hash) {
        return tableExists() && queryLong("SELECT count(*) FROM " + table
                + " WHERE source = ? AND metadata->>'hash' = ?", source, hash) > 0;
    }

    @Override
    public long count() {
        return tableExists() ? queryLong("SELECT count(*) FROM " + table) : 0;
    }

    @Override
    public Map<String, Long> sources() {
        Map<String, Long> out = new LinkedHashMap<>();
        if (!tableExists()) {
            return out;
        }
        try (Connection con = dataSource.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT source, count(*) FROM " + table + " GROUP BY source ORDER BY source")) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getLong(2));
            }
        } catch (SQLException e) {
            throw new AiException("pgvector query failed: " + e.getMessage(), e);
        }
        return out;
    }

    private boolean tableExists() {
        if (tableReady) {
            return true;
        }
        return queryLong("SELECT count(*) FROM information_schema.tables WHERE table_name = ?", table.toLowerCase()) > 0;
    }

    private synchronized void ensureTable(int dimension) {
        if (tableReady) {
            return;
        }
        try (Connection con = dataSource.getConnection(); Statement st = con.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS vector");
            st.execute("CREATE TABLE IF NOT EXISTS " + table + " (id text PRIMARY KEY, source text NOT NULL, "
                    + "chunk_index int NOT NULL, content text NOT NULL, metadata jsonb NOT NULL DEFAULT '{}', "
                    + "embedding vector(" + dimension + ") NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS " + table + "_source_idx ON " + table + " (source)");
            st.execute("CREATE INDEX IF NOT EXISTS " + table + "_embedding_idx ON " + table
                    + " USING hnsw (embedding vector_cosine_ops)");
        } catch (SQLException e) {
            throw new AiException("pgvector table setup failed (is the pgvector extension installed?): "
                    + e.getMessage(), e);
        }
        tableReady = true;
    }

    private void update(String sql, String... params) {
        try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AiException("pgvector update failed: " + e.getMessage(), e);
        }
    }

    private long queryLong(String sql, String... params) {
        try (Connection con = dataSource.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new AiException("pgvector query failed: " + e.getMessage(), e);
        }
    }
}
