package com.zhixing.exam.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import com.zhixing.common.handler.JsonStringListTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 题目（题库）。
 * <p>
 * 由原内存 Map 实现升级为 MySQL 持久化：教师端发布 → 学员端接收，
 * {@code courseId} 是师生联动的锚点，{@code status=1} 表示已发布（学员可见）。
 * <p>
 * {@code options} 以 JSON 字符串落库，通过 {@link JsonStringListTypeHandler} 自动互转；
 * 因此必须开启 {@code autoResultMap}，否则查询结果不会走自定义 TypeHandler。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "question", autoResultMap = true)
public class Question extends BasePO {

    /** 题干 */
    private String name;

    /** 类型：1-单选 2-多选 3-判断 */
    private Integer type;

    /** 难度 1-5 */
    private Integer difficulty;

    /** 分值 */
    private Integer score;

    /** 题干补充说明 */
    private String content;

    /** 选项列表（判断题为 [正确, 错误]），DB 中为 JSON 字符串 */
    @TableField(typeHandler = JsonStringListTypeHandler.class)
    private List<String> options;

    /** 正确答案（选项序号连写，如 A / AB） */
    private String answer;

    /** 答案解析 */
    private String analysis;

    /** 归属教师 id（发布人，师生联动归属） */
    private Long teacherId;

    /** 关联课程 id（师生联动锚点） */
    private Long courseId;

    /** 状态：0-草稿（学员不可见） 1-已发布（学员可见） */
    private Integer status;

    /** 关联课程名称快照（非表字段，列表展示用，由服务层填充） */
    @TableField(exist = false)
    private String courseName;
}
