package com.zhixing.promotion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 营销服务启动类
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.zhixing.api.client")
@EnableScheduling
@MapperScan("com.zhixing.promotion.mapper")
public class PromotionApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(PromotionApplication.class).run(args);
    }
}
