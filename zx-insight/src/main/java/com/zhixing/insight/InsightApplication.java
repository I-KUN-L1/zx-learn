package com.zhixing.insight;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 智能学情分析服务启动类
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.zhixing.api.client")
@MapperScan("com.zhixing.insight.mapper")
public class InsightApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(InsightApplication.class).run(args);
    }
}
