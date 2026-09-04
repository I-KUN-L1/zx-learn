-- ============================================================
-- 知行智学（zx-learn）RAG 向量库初始化（docker-entrypoint-initdb.d）
-- 容器：pgvector/pgvector:pg16，数据库 zx_aigc（由 POSTGRES_DB 指定）
-- ============================================================

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 知识切片表：每行一个 embeddings 向量 + 原文
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT,
    lesson_id   BIGINT,
    title       VARCHAR(255),
    content     TEXT      NOT NULL,
    -- 维度需与应用侧 zx.llm.embedding-dimension（默认 1536）保持一致
    embedding   vector(1536),
    create_time TIMESTAMPTZ DEFAULT now()
);

-- HNSW 向量索引：加速 topK 余弦相似检索（embedding <=> ?）
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding
    ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);

-- 按课程/课时过滤检索时使用的普通索引
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_course
    ON knowledge_chunk (course_id, lesson_id);