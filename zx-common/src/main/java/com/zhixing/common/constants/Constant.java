package com.zhixing.common.constants;

import org.slf4j.MDC;

/**
 * 通用常量
 */
public interface Constant {

    String REQUEST_ID_HEADER = "requestId";
    String REQUEST_ID_ATTR = "requestId";
    String USER_INFO_HEADER = "user-info";
    String ROLE_INFO_HEADER = "role-info";
    String AUTHORIZATION_HEADER = "authorization";

    String HEADER_USER_ID = "userId";

    /** 逻辑删除标记 */
    int NOT_DELETED = 0;
    int DELETED = 1;

    /**
     * 课程状态：已上架（对访客与学员可见）。
     * <p>
     * 课程表 {@code course.status} 只有 0/1 两个取值。该常量集中定义，
     * 避免课程服务（查询过滤）与交易服务（下单前校验）各写一份魔法数字后漂移。
     */
    int COURSE_STATUS_ON_SHELF = 1;

    /** 课程状态：已下架（对访客与学员不可见，仅管理端/教师端工作台可见） */
    int COURSE_STATUS_OFF_SHELF = 0;

    static String getRequestId() {
        String requestId = MDC.get(REQUEST_ID_HEADER);
        return requestId == null ? "" : requestId;
    }
}
