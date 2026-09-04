package com.zhixing.aigc.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识切片：课程讲义切片出的一个文本片段 + 其向量
 */
@Data
public class KnowledgeChunk {

    private Long id;
    private Long courseId;
    private Long lessonId;
    private String title;
    private String content;
    /** 向量（维度与 zx.llm.embedding-dimension 一致），不参与序列化 */
    private transient float[] embedding;
    private LocalDateTime createTime;
}