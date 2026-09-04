package com.zhixing.aigc.service;

import com.pgvector.PGvector;
import com.zhixing.aigc.domain.ChunkHit;
import com.zhixing.aigc.domain.KnowledgeChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * pgvector 知识切片仓储：通过 JDBC 访问 PostgreSQL 的 vector 列。
 * 依赖 docker-compose 中的 postgres(pgvector) 服务与 deploy/pgvector/init.sql 建表。
 */
@Slf4j
@Repository
public class KnowledgeVectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 在目标连接上注册 pgvector 的 vector 类型，使 setObject(..., new PGvector(...)) 走二进制传输。
     */
    private void registerVectorType(PreparedStatement ps) throws SQLException {
        PGvector.addVectorType(ps.getConnection());
    }

    private static final String INSERT_SQL =
            "INSERT INTO knowledge_chunk (course_id, lesson_id, title, content, embedding, create_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

    public void insert(KnowledgeChunk chunk) {
        jdbcTemplate.update(INSERT_SQL, (PreparedStatementSetter) ps -> {
            registerVectorType(ps);
            ps.setObject(1, chunk.getCourseId());
            ps.setObject(2, chunk.getLessonId());
            ps.setString(3, chunk.getTitle());
            ps.setString(4, chunk.getContent());
            ps.setObject(5, new PGvector(chunk.getEmbedding()));
            ps.setObject(6, LocalDateTime.now());
        });
    }

    /**
     * 向量余弦检索 TopK。score = 1 - 余弦距离（向量已归一化时 == 余弦相似度）。
     */
    public List<ChunkHit> searchTopK(float[] query, int topK) {
        if (query == null || query.length == 0) {
            return List.of();
        }
        String sql = "SELECT id, title, content, (1 - (embedding <=> ?)) AS score " +
                "FROM knowledge_chunk ORDER BY embedding <=> ? ASC LIMIT ?";
        PreparedStatementSetter setter = new PreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                registerVectorType(ps);
                PGvector vec = new PGvector(query);
                ps.setObject(1, vec);
                ps.setObject(2, vec);
                ps.setInt(3, topK);
            }
        };
        return jdbcTemplate.query(sql, setter, (rs, rowNum) -> {
            ChunkHit hit = new ChunkHit();
            hit.setId(rs.getLong("id"));
            hit.setTitle(rs.getString("title"));
            hit.setContent(rs.getString("content"));
            hit.setScore(rs.getDouble("score"));
            return hit;
        });
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE id = ?", id);
    }

    public void deleteByCourse(Long courseId) {
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE course_id = ?", courseId);
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_chunk", Long.class);
        return c == null ? 0 : c;
    }
}