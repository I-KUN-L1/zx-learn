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
    -- 维度需与应用侧 zx.llm.embedding-dimension（默认 1024）**严格一致**：
    --   · 智谱 embedding-3 合法维度为 256/512/1024/2048（不含 1536）；
    --   · pgvector 的 HNSW 索引对 vector 类型上限 2000 维，故 2048 维无法建索引；
    --   两者取交集后本项目统一使用 1024。
    -- 若已存在旧数据卷（列维度不同），执行 migrate-embedding-dim.sql 做幂等迁移。
    embedding   vector(1024),
    create_time TIMESTAMPTZ DEFAULT now()
);

-- HNSW 向量索引：加速 topK 余弦相似检索（embedding <=> ?）
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding
    ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);

-- 按课程/课时过滤检索时使用的普通索引
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_course
    ON knowledge_chunk (course_id, lesson_id);