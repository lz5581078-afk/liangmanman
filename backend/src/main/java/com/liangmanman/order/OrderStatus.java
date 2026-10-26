package com.liangmanman.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态与允许的流转。
 *
 * <pre>
 * 送货上门：待支付 → 待接单 → 已接单 → 送货中 → 已送达 → 已完成
 * 到店自提：待支付 → 待接单 → 待自提 → 已完成
 * 快递发货：待支付 → 待发货 → 已发货 → 已完成
 * 凑单：待支付 → 待成团 →（成团后）待接单 / 待发货 → …
 * 货到付款的订单没有“待支付”，下单直接进入“待接单”。
 * 已完成之前的大多数状态都可以取消（谁能取消由 OrderService 判断）。
 * </pre>
 */
public enum OrderStatus {
    PENDING_PAY("待支付"),
    GROUP_WAITING("待成团"),
    PENDING_ACCEPT("待接单"),
    ACCEPTED("已接单"),
    DELIVERING("送货中"),
    DELIVERED("已送达"),
    READY_PICKUP("待自提"),
    PENDING_SHIP("待发货"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.ofEntries(
            Map.entry(PENDING_PAY, EnumSet.of(GROUP_WAITING, PENDING_ACCEPT, PENDING_SHIP, CANCELLED)),
            Map.entry(GROUP_WAITING, EnumSet.of(PENDING_ACCEPT, PENDING_SHIP, CANCELLED)),
            Map.entry(PENDING_ACCEPT, EnumSet.of(ACCEPTED, READY_PICKUP, CANCELLED)),
            Map.entry(ACCEPTED, EnumSet.of(DELIVERING, CANCELLED)),
            Map.entry(DELIVERING, EnumSet.of(DELIVERED)),
            Map.entry(DELIVERED, EnumSet.of(COMPLETED)),
            Map.entry(READY_PICKUP, EnumSet.of(COMPLETED, CANCELLED)),
            Map.entry(PENDING_SHIP, EnumSet.of(SHIPPED, CANCELLED)),
            Map.entry(SHIPPED, EnumSet.of(COMPLETED)),
            Map.entry(COMPLETED, EnumSet.noneOf(OrderStatus.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(OrderStatus.class)));

    public boolean canGoTo(OrderStatus next) {
        return NEXT.get(this).contains(next);
    }

    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED;
    }
}
