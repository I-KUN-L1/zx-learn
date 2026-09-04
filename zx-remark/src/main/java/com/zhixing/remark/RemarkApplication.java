package com.zhixing.remark;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 点赞服务启动类
 */
@SpringBootApplication
public class RemarkApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(RemarkApplication.class).run(args);
    }
}
