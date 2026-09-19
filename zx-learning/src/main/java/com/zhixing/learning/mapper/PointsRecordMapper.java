package com.zhixing.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.learning.domain.po.PointsRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 积分明细 Mapper。
 * <p>
 * 排行榜类是跨行聚合查询，MyBatis-Plus 的 Wrapper 难以表达
 * {@code GROUP BY ... HAVING ...} 后再计数，因此以显式 SQL 表达，语义更清晰。
 */
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    /**
     * 积分排行榜（按用户聚合，降序）。
     * 显式 {@code deleted = 0}，不走 MP 逻辑删除自动拼接（自定义 SQL 不参与自动注入）。
     */
    @Select("""
            SELECT user_id AS userId, SUM(points) AS total
            FROM points_record
            WHERE deleted = 0
            GROUP BY user_id
            ORDER BY total DESC, user_id ASC
            """)
    List<Map<String, Object>> selectRanking();

    /** 指定用户积分总额（无记录返回 0） */
    @Select("SELECT COALESCE(SUM(points), 0) FROM points_record WHERE deleted = 0 AND user_id = #{userId}")
    Long sumByUser(@Param("userId") Long userId);

    /** 积分严格高于给定值的用户数（用于计算"我的排名"） */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT user_id FROM points_record
                WHERE deleted = 0
                GROUP BY user_id
                HAVING SUM(points) > #{total}
            ) t
            """)
    Long countUsersAbove(@Param("total") Long total);

    /** 有积分记录的用户总数（排行榜分母） */
    @Select("SELECT COUNT(DISTINCT user_id) FROM points_record WHERE deleted = 0")
    Long countRankedUsers();
}
