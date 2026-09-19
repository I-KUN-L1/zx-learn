package com.zhixing.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务后置回调工具。
 *
 * <p>用于「本地事务提交后再做跨服务写入」的场景（同步开课、券状态同步等）：
 * 若在事务内直接发起远程调用，一旦本地事务随后回滚，就会出现"远程改了、本地没改"的脏状态。
 * 本工具把动作挂到 {@code afterCommit}，保证顺序为：本地提交 → 远程写入。
 *
 * <p>无事务上下文时（如 MQ 消费端、对账任务）立即执行。
 */
@Slf4j
public final class TxSupport {

    private TxSupport() {
    }

    /**
     * 本地事务提交后执行；无事务时立即执行。动作内部的异常不外抛（调用方按 fail-open 处理）。
     */
    public static void afterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            run(action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                run(action);
            }
        });
    }

    private static void run(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            // 兜底：动作实现自身应已做异常隔离，这里只做最后的防线，绝不因补偿动作影响主流程
            log.warn("事务后置动作执行失败：{}", e.getMessage());
        }
    }
}
