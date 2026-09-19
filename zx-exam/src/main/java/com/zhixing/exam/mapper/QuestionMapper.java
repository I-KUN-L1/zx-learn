package com.zhixing.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.exam.domain.po.Question;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题库 Mapper
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
