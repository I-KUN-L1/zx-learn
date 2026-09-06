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

    static String getRequestId() {
        String requestId = MDC.get(REQUEST_ID_HEADER);
        return requestId == null ? "" : requestId;
    }
}
