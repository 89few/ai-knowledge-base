package org.aiknowledgebase.service;

import org.aiknowledgebase.dto.ChunkSearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorStoreService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public VectorStoreService(JdbcTemplate jdbcTemplate,
                              EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    /**
     * 初始化 PGVector 字段。
     * 当前使用 nomic-embed-text，所以字段类型是 vector(768)。
     */

    public void initVectorColumn() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

        String sql = "ALTER TABLE kb_text_chunk " +
                "ADD COLUMN IF NOT EXISTS embedding vector(" + EmbeddingService.VECTOR_DIM + ")";

        jdbcTemplate.execute(sql);
    }

    /**
     * 为某个知识库下的所有文本切片生成 embedding，并写入 PGVector。
     */
    public int buildEmbeddings(Long knowledgeBaseId) {
        initVectorColumn();

        String querySql = """
                SELECT id, content
                FROM kb_text_chunk
                WHERE knowledge_base_id = ?
                ORDER BY id
                """;

        List<ChunkRow> chunks = jdbcTemplate.query(
                querySql,
                (rs, rowNum) -> new ChunkRow(
                        rs.getLong("id"),
                        rs.getString("content")
                ),
                knowledgeBaseId
        );

        int count = 0;

        for (ChunkRow chunk : chunks) {
            double[] embedding = embeddingService.embed(chunk.content());
            String vectorLiteral = embeddingService.toVectorLiteral(embedding);

            String updateSql = """
                    UPDATE kb_text_chunk
                    SET embedding = CAST(? AS vector)
                    WHERE id = ?
                    """;

            jdbcTemplate.update(updateSql, vectorLiteral, chunk.id());
            count++;
        }

        return count;
    }
    public List<ChunkSearchResult> searchUploadedFileChunksWithOverview(Long uploadedFileId, String query, int topK) {
        initUploadedChunkVectorColumn();

        double[] queryEmbedding = embeddingService.embed(query);
        String queryVector = embeddingService.toVectorLiteral(queryEmbedding);

        int semanticTopK = Math.max(topK, 8);

        String sql = """
            WITH overview AS (
                SELECT id,
                       uploaded_file_id AS document_id,
                       chunk_index,
                       content,
                       embedding <=> CAST(? AS vector) AS distance
                FROM kb_uploaded_file_chunk
                WHERE uploaded_file_id = ?
                  AND embedding IS NOT NULL
                ORDER BY chunk_index
                LIMIT 6
            ),
            semantic AS (
                SELECT id,
                       uploaded_file_id AS document_id,
                       chunk_index,
                       content,
                       embedding <=> CAST(? AS vector) AS distance
                FROM kb_uploaded_file_chunk
                WHERE uploaded_file_id = ?
                  AND embedding IS NOT NULL
                ORDER BY distance
                LIMIT ?
            )
            SELECT DISTINCT ON (id)
                   id,
                   document_id,
                   chunk_index,
                   content,
                   distance
            FROM (
                SELECT * FROM overview
                UNION ALL
                SELECT * FROM semantic
            ) t
            ORDER BY id, distance
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ChunkSearchResult(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("distance")
                ),
                queryVector,
                uploadedFileId,
                queryVector,
                uploadedFileId,
                semanticTopK
        );
    }
    /**
     * 根据用户问题，在某个知识库中检索最相关的文本切片。
     */
    public List<ChunkSearchResult> searchSimilarChunks(Long knowledgeBaseId, String query, int topK) {
        initVectorColumn();
        initUploadedChunkVectorColumn();

        double[] queryEmbedding = embeddingService.embed(query);
        String queryVector = embeddingService.toVectorLiteral(queryEmbedding);

        String sql = """
            SELECT id, document_id, chunk_index, content, distance
            FROM (
                SELECT id,
                       document_id,
                       chunk_index,
                       content,
                       embedding <=> CAST(? AS vector) AS distance
                FROM kb_text_chunk
                WHERE knowledge_base_id = ?
                  AND embedding IS NOT NULL

                UNION ALL

                SELECT id,
                       uploaded_file_id AS document_id,
                       chunk_index,
                       content,
                       embedding <=> CAST(? AS vector) AS distance
                FROM kb_uploaded_file_chunk
                WHERE knowledge_base_id = ?
                  AND embedding IS NOT NULL
            ) t
            ORDER BY distance
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ChunkSearchResult(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("distance")
                ),
                queryVector,
                knowledgeBaseId,
                queryVector,
                knowledgeBaseId,
                topK
        );
    }
    /**
     * 内部临时对象，用来接收数据库查询出来的切片 ID 和内容。
     */
    private record ChunkRow(Long id, String content) {
    }
    private void initUploadedChunkVectorColumn() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

        String sql = "ALTER TABLE kb_uploaded_file_chunk " +
                "ADD COLUMN IF NOT EXISTS embedding vector(" + EmbeddingService.VECTOR_DIM + ")";

        jdbcTemplate.execute(sql);
    }
    public List<ChunkSearchResult> searchSimilarUploadedFileChunks(Long uploadedFileId, String query, int topK) {
        initUploadedChunkVectorColumn();

        double[] queryEmbedding = embeddingService.embed(query);
        String queryVector = embeddingService.toVectorLiteral(queryEmbedding);

        String sql = """
            SELECT id,
                   uploaded_file_id AS document_id,
                   chunk_index,
                   content,
                   embedding <=> CAST(? AS vector) AS distance
            FROM kb_uploaded_file_chunk
            WHERE uploaded_file_id = ?
              AND embedding IS NOT NULL
            ORDER BY distance
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ChunkSearchResult(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("distance")
                ),
                queryVector,
                uploadedFileId,
                topK
        );
    }
}