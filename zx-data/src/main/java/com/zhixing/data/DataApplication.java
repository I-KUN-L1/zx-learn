package com.zhixing.data;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 数据看板服务启动类
 */
@SpringBootApplication
public class DataApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(DataApplication.class).run(args);
    }
}
