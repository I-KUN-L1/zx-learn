-- ============================================================
-- RAG 向量库维度迁移（幂等）：knowledge_chunk.embedding 1536 → 1024
--
-- 背景（上线审查发现的缺陷）：
--   1) 应用侧 Embedding 接口路径曾被写死为 /v1/embeddings，与智谱 base-url
--      （https://open.bigmodel.cn/api/paas/v4）拼成 /v4/v1/embeddings → HTTP 404，
--      Embedding 恒定失败并被**静默降级**为伪向量，RAG 检索无任何语义；
--   2) 配置的模型名 text-embedding-3-small 不是智谱的模型（错误码 1211 模型不存在）；
--   3) 智谱 embedding-3 的合法维度为 256/512/1024/2048，**不含 1536**，
--      而 pgvector 的 HNSW 索引对 vector 类型上限 2000 维，2048 无法索引。
--   ⇒ 统一取 1024 维：既符合厂商可选值，又满足 HNSW 索引上限。
--
-- 用法（仅对「已存在的 PG 数据卷」需要执行；全新容器由 init.sql 直接建 1024）：
--   docker exec -i zx-learn-pg psql -U postgres -d zx_aigc < deploy/pgvector/migrate-embedding-dim.sql
--
-- 幂等性：维度已一致时整个 DO 块不产生任何变更。
-- 代价：维度变化后旧向量与新向量不可比，因此会清空 knowledge_chunk，
--       需要用教师端「知识库上传」重新灌入讲义（当前库通常为空，无实际损失）。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

DO $$
DECLARE
    cur_type text;
BEGIN
    SELECT format_type(a.atttypid, a.atttypmod)
      INTO cur_type
      FROM pg_attribute a
     WHERE a.attrelid = 'knowledge_chunk'::regclass
       AND a.attname = 'embedding'
       AND NOT a.attisdropped;

    IF cur_type IS NULL THEN
        RAISE NOTICE 'knowledge_chunk 不存在或没有 embedding 列，跳过';
        RETURN;
    END IF;

    IF cur_type = 'vector(1024)' THEN
        RAISE NOTICE 'knowledge_chunk.embedding 已是 vector(1024)，无需迁移';
        RETURN;
    END IF;

    RAISE NOTICE 'knowledge_chunk.embedding 当前为 %，迁移到 vector(1024)', cur_type;

    -- HNSW 索引必须在改类型前删除，改完再重建
    DROP INDEX IF EXISTS idx_knowledge_chunk_embedding;
    ALTER TABLE knowledge_chunk ALTER COLUMN embedding TYPE vector(1024);

    -- 维度变更后旧向量不可比（长度不同，无法参与余弦检索），清空并提示重灌
    DELETE FROM knowledge_chunk;

    CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding
        ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);

    RAISE NOTICE '迁移完成：旧向量已清空，请通过教师端「知识库上传」重新灌入讲义';
END $$;
