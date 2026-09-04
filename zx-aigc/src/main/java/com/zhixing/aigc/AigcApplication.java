package com.zhixing.aigc;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 服务启动类
 */
@SpringBootApplication
@EnableAsync
@EnableFeignClients(basePackages = "com.zhixing.api.client")
public class AigcApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(AigcApplication.class).run(args);
    }
}
