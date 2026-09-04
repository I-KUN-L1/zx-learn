package com.zhixing.aigc.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量检索命中的知识切片
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkHit {

    private Long id;
    private String title;
    private String content;
    /** 余弦相似度（0~1，越接近 1 越相似） */
    private double score;
}