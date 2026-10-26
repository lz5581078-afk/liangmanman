package com.liangmanman.notify;

import com.liangmanman.config.AppProperties;
import com.liangmanman.entity.NotifyLog;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.User;
import com.liangmanman.integration.SmsService;
import com.liangmanman.integration.VoiceService;
import com.liangmanman.integration.WxApi;
import com.liangmanman.mapper.NotifyLogMapper;
import com.liangmanman.mapper.StoreMapper;
import com.liangmanman.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信 + 语音电话 + 微信订阅消息。
 *
 * <p>订阅消息模板字段（在微信公众平台选模板时按下面的字段选，或改这里的字段名）：
 * <ul>
 *   <li>买家“订单状态通知”：character_string1 订单号 / thing2 店铺 / phrase3 状态 / thing4 说明</li>
 *   <li>商家“新订单提醒”：character_string1 订单号 / amount2 金额 / thing3 内容 / time4 时间</li>
 * </ul>
 */
@Slf4j
@Service
public class ChannelNotificationService implements NotificationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SmsService sms;
    private final VoiceService voice;
    private final WxApi wx;
    private final StoreMapper storeMapper;
    private final UserMapper userMapper;
    private final NotifyLogMapper notifyLogMapper;
    private final TaskExecutor executor;
    private final AppProperties.Notify cfg;
    private final AppProperties.Wx wxCfg;

    public ChannelNotificationService(SmsService sms, VoiceService voice, WxApi wx, StoreMapper storeMapper,
                                      UserMapper userMapper, NotifyLogMapper notifyLogMapper,
                                      @Qualifier("notifyExecutor") TaskExecutor executor, AppProperties props) {
        this.sms = sms;
        this.voice = voice;
        this.wx = wx;
        this.storeMapper = storeMapper;
        this.userMapper = userMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.executor = executor;
        this.cfg = props.notification();
        this.wxCfg = props.wx();
    }

    @Override
    public void merchantNewOrder(Order order) {
        later(() -> {
            Store store = storeMapper.selectById(order.getStoreId());
            if (store == null) return;
            String amount = order.getTotalAmount().stripTrailingZeros().toPlainString();
            String shortNo = order.getOrderNo().substring(Math.max(0, order.getOrderNo().length() - 6));
            record("VOICE", store.getPhone(), "新订单 " + amount + " 元", order.getId(),
                    voice.call(store.getPhone(), cfg.voiceTemplateNewOrder(), List.of(amount)));
            record("SMS", store.getPhone(), "新订单 " + shortNo + "，" + amount + " 元", order.getId(),
                    sms.send(store.getPhone(), cfg.smsTemplateNewOrder(), List.of(shortNo, amount)));
            User owner = userMapper.selectById(store.getOwnerId());
            if (owner != null && owner.getOpenId() != null) {
                Map<String, String> data = new LinkedHashMap<>();
                data.put("character_string1", order.getOrderNo());
                data.put("amount2", amount + "元");
                data.put("thing3", cut(order.getFulfillment().equals("EXPRESS") ? "快递订单，请发货" : "新订单，请尽快接单", 20));
                data.put("time4", order.getCreatedAt() == null ? "" : order.getCreatedAt().format(TIME));
                record("SUBSCRIBE", owner.getOpenId(), "新订单 " + order.getOrderNo(), order.getId(),
                        wx.sendSubscribe(owner.getOpenId(), wxCfg.subscribeMerchantTemplate(),
                                "pages/merchant/orders", data));
            }
        });
    }

    @Override
    public void merchantRemind(Order order) {
        later(() -> {
            Store store = storeMapper.selectById(order.getStoreId());
            if (store == null) return;
            String amount = order.getTotalAmount().stripTrailingZeros().toPlainString();
            record("VOICE", store.getPhone(), "催接单 " + amount + " 元", order.getId(),
                    voice.call(store.getPhone(), cfg.voiceTemplateNewOrder(), List.of(amount)));
        });
    }

    @Override
    public void buyerOrderChanged(Order order, String message) {
        later(() -> {
            User buyer = userMapper.selectById(order.getBuyerId());
            Store store = storeMapper.selectById(order.getStoreId());
            String storeName = store == null ? "粮满满" : store.getName();
            String error = "没有 openid";
            if (buyer != null && buyer.getOpenId() != null) {
                Map<String, String> data = new LinkedHashMap<>();
                data.put("character_string1", order.getOrderNo());
                data.put("thing2", cut(storeName, 20));
                data.put("phrase3", cut(statusWord(message), 5));
                data.put("thing4", cut(message, 20));
                error = wx.sendSubscribe(buyer.getOpenId(), wxCfg.subscribeOrderTemplate(),
                        "pages/order/detail?id=" + order.getId(), data);
                record("SUBSCRIBE", buyer.getOpenId(), message, order.getId(), error);
            }
            if (error != null && cfg.buyerSmsFallback() && order.getContactPhone() != null) {
                record("SMS", order.getContactPhone(), message, order.getId(),
                        sms.send(order.getContactPhone(), cfg.smsTemplateBuyer(), List.of(cut(storeName, 12), cut(message, 20))));
            }
        });
    }

    @Override
    public void merchantNotice(Long storeId, String title, String message, String page) {
        later(() -> {
            Store store = storeMapper.selectById(storeId);
            if (store == null) return;
            User owner = userMapper.selectById(store.getOwnerId());
            if (owner == null || owner.getOpenId() == null) return;
            Map<String, String> data = new LinkedHashMap<>();
            data.put("character_string1", "-");
            data.put("amount2", "0元");
            data.put("thing3", cut(title + "：" + message, 20));
            data.put("time4", java.time.LocalDateTime.now().format(TIME));
            record("SUBSCRIBE", owner.getOpenId(), title + "：" + message, null,
                    wx.sendSubscribe(owner.getOpenId(), wxCfg.subscribeMerchantTemplate(), page, data));
        });
    }

    @Override
    public void buyerNotice(Long userId, String title, String message, String page) {
        later(() -> {
            User user = userMapper.selectById(userId);
            if (user == null || user.getOpenId() == null) return;
            Map<String, String> data = new LinkedHashMap<>();
            data.put("character_string1", "-");
            data.put("thing2", cut(title, 20));
            data.put("phrase3", cut(statusWord(title), 5));
            data.put("thing4", cut(message, 20));
            record("SUBSCRIBE", user.getOpenId(), title + "：" + message, null,
                    wx.sendSubscribe(user.getOpenId(), wxCfg.subscribeOrderTemplate(), page, data));
        });
    }

    // ---------- 内部 ----------

    /** 事务提交后再异步发送；没有事务时直接异步发送 */
    private void later(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("发送通知出错", e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(safe);
                }
            });
        } else {
            executor.execute(safe);
        }
    }

    private void record(String channel, String target, String content, Long orderId, String error) {
        NotifyLog l = new NotifyLog();
        l.setChannel(channel);
        l.setTarget(target == null ? "-" : cut(target, 64));
        l.setContent(cut(content, 500));
        l.setSuccess(error == null);
        l.setError(error == null ? null : cut(error, 300));
        l.setOrderId(orderId);
        notifyLogMapper.insert(l);
    }

    /** 订阅消息 phrase 字段只能是 5 个字以内的状态词 */
    private static String statusWord(String message) {
        if (message == null) return "有更新";
        for (String w : List.of("已接单", "已发货", "已送达", "已取消", "已完成", "可自提", "已成团", "有回复", "送货中")) {
            if (message.contains(w)) return w;
        }
        if (message.contains("出发")) return "送货中";
        if (message.contains("取消")) return "已取消";
        return "有更新";
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
