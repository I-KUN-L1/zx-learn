package com.zhixing.auth.config;

import com.zhixing.auth.service.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后执行首个管理员安全引导（生成失败不阻断服务启动，仅记录告警）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            adminBootstrapService.bootstrapIfNeeded();
        } catch (Exception e) {
            log.error("首个管理员引导失败，请检查 zx-user 服务是否可用（将重试/手动处理）", e);
        }
    }
}