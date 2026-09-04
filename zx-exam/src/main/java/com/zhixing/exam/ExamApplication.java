package com.zhixing.exam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 考试服务启动类
 */
@SpringBootApplication
@MapperScan("com.zhixing.exam.mapper")
public class ExamApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ExamApplication.class).run(args);
    }
}
