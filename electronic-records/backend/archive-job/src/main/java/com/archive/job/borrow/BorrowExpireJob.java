package com.archive.job.borrow;

import com.archive.core.service.BorrowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 借阅权限到期回收：每分钟扫描过期 Token，置 EXPIRED、撤销并清缓存（AGENTS.md 3.3）。
 */
@Component
public class BorrowExpireJob {

    private static final Logger log = LoggerFactory.getLogger(BorrowExpireJob.class);

    private final BorrowService borrowService;

    public BorrowExpireJob(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireBorrowTokens() {
        try {
            int count = borrowService.expireExpiredBorrows();
            if (count > 0) {
                log.info("借阅到期回收: {} 个 Token 已过期撤销", count);
            }
        } catch (Exception e) {
            log.warn("借阅到期回收执行失败: {}", e.getMessage());
        }
    }
}
