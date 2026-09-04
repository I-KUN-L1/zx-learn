package com.zhixing.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zhixing.common.handler.MyMetaObjectHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件 + 字段自动填充。
 * 仅在 classpath 存在 MyBatis-Plus 时生效（即实际使用数据库的模块）。
 */
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    public MyMetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
    }
}
