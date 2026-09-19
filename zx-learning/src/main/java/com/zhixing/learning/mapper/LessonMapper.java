package com.zhixing.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.learning.domain.po.Lesson;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 我的课表 Mapper
 */
public interface LessonMapper extends BaseMapper<Lesson> {

    /**
     * 复活被逻辑删除的课表项（开课幂等自愈）。
     * <p>
     * 为什么需要它：{@code lesson} 上的唯一索引 {@code uk_user_course(user_id, course_id)} 是
     * <b>物理</b>唯一约束，而"删除课表项"走的是 MyBatis-Plus 的逻辑删除（{@code deleted=1}）。
     * 于是学员删过某门课后再重新购买/开课时，{@code insert} 必然撞唯一索引抛
     * {@code DuplicateKeyException}；如果只做"幂等跳过"，那行 {@code deleted=1} 的记录永远
     * 不会复活 —— 表现为「订单已支付、再点购买提示已拥有，但"我的课表"里始终没有这门课」。
     * <p>
     * 注意：这里必须用原生 {@code @Update} SQL，因为 MyBatis-Plus 的逻辑删除会让自动生成的
     * update 语句强制追加 {@code deleted = 0}，从而永远匹配不到 {@code deleted = 1} 的行。
     *
     * @return 受影响行数（0 表示该课表项不存在）
     */
    @Update("UPDATE lesson SET deleted = 0, "
            + "course_name = COALESCE(#{courseName}, course_name), "
            + "update_time = NOW() "
            + "WHERE user_id = #{userId} AND course_id = #{courseId}")
    int revive(@Param("userId") Long userId,
               @Param("courseId") Long courseId,
               @Param("courseName") String courseName);
}