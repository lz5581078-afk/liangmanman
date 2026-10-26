package com.liangmanman.notify;

import com.liangmanman.entity.Order;

/**
 * 通知出口。所有通知都在数据库事务提交之后异步发送，发送失败不影响业务。
 * 每次发送（无论成败）都记到 lm_notify_log，运营后台可查。
 */
public interface NotificationService {

    /** 新订单：给老板打电话 + 发短信 + 订阅消息 */
    void merchantNewOrder(Order order);

    /** 下单一段时间老板还没接：再打一次电话 */
    void merchantRemind(Order order);

    /** 订单状态变化告诉买家：订阅消息，失败时按配置补短信 */
    void buyerOrderChanged(Order order, String message);

    /** 给老板的一般通知（凑单结果、求购等） */
    void merchantNotice(Long storeId, String title, String message, String page);

    /** 给买家的一般通知（求购有回复等） */
    void buyerNotice(Long userId, String title, String message, String page);
}
