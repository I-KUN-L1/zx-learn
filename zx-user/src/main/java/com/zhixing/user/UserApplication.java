package com.zhixing.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 用户服务启动类
 */
@SpringBootApplication
@MapperScan("com.zhixing.user.mapper")
public class UserApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(UserApplication.class).run(args);
    }
}
