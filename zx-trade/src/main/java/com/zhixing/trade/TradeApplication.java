package com.zhixing.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 交易服务启动类
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.zhixing.api.client")
@MapperScan("com.zhixing.trade.mapper")
public class TradeApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(TradeApplication.class).run(args);
    }
}
