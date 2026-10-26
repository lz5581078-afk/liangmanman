package com.liangmanman.job;

import com.liangmanman.service.FundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 资金相关：解冻货款、查提现结果、查开户进度 */
@Slf4j
@Component
public class FundJob {

    private final FundService fundService;

    public FundJob(FundService fundService) {
        this.fundService = fundService;
    }

    /** 订单完成后的货款解冻，每 2 分钟 */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void release() {
        try {
            fundService.releasePending();
        } catch (Exception e) {
            log.error("解冻任务出错", e);
        }
    }

    /** 提现结果、开户进度，每 10 分钟 */
    @Scheduled(fixedDelay = 600_000, initialDelay = 90_000)
    public void poll() {
        try {
            fundService.pollWithdrawals();
            fundService.pollApplyments();
        } catch (Exception e) {
            log.error("资金查询任务出错", e);
        }
    }
}
