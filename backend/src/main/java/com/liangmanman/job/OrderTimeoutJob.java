package com.liangmanman.job;

import com.liangmanman.config.AppProperties;
import com.liangmanman.service.GroupBuyService;
import com.liangmanman.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟：催接单、超时未支付取消、超时未接单取消（自动退款）、到期自动确认收货、凑单到期结算。
 * 多实例部署时需要加分布式锁（如 ShedLock），单实例不需要。
 */
@Slf4j
@Component
public class OrderTimeoutJob {

    private final OrderService orderService;
    private final GroupBuyService groupBuyService;
    private final AppProperties.Order cfg;

    public OrderTimeoutJob(OrderService orderService, GroupBuyService groupBuyService, AppProperties props) {
        this.orderService = orderService;
        this.groupBuyService = groupBuyService;
        this.cfg = props.order();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        int reminded = safe(() -> orderService.remindUnaccepted(cfg.remindAfterMinutes()));
        int unpaid = safe(() -> orderService.cancelUnpaid(cfg.payTimeoutMinutes()));
        int unaccepted = safe(() -> orderService.cancelUnaccepted(cfg.acceptTimeoutMinutes()));
        int confirmed = safe(() -> orderService.autoConfirm(cfg.autoConfirmHours(), cfg.expressAutoConfirmDays()));
        int groups = safe(groupBuyService::settleDue);
        int decisions = safe(groupBuyService::decisionTimeout);
        if (reminded + unpaid + unaccepted + confirmed + groups + decisions > 0) {
            log.info("订单定时任务：催接单 {}，超时未支付 {}，超时未接单 {}，自动确认 {}，凑单结算 {}，凑单自动退款 {}",
                    reminded, unpaid, unaccepted, confirmed, groups, decisions);
        }
    }

    private static int safe(java.util.function.IntSupplier task) {
        try {
            return task.getAsInt();
        } catch (Exception e) {
            log.error("订单定时任务出错", e);
            return 0;
        }
    }
}
