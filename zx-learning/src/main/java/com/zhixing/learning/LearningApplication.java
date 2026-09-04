package com.zhixing.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 学习服务启动类
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.zhixing.api.client")
@MapperScan("com.zhixing.learning.mapper")
public class LearningApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(LearningApplication.class).run(args);
    }
}
