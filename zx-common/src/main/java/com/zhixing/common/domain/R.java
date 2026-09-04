package com.zhixing.common.domain;

import com.zhixing.common.constants.Constant;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

    private int code;
    private String msg;
    private T data;
    private String requestId;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMsg("OK");
        r.setData(data);
        r.setRequestId(Constant.getRequestId());
        return r;
    }

    public static <T> R<T> error(String msg) {
        return error(0, msg);
    }

    public static <T> R<T> error(int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setRequestId(Constant.getRequestId());
        return r;
    }

    public boolean success() {
        return code == 200;
    }
}
