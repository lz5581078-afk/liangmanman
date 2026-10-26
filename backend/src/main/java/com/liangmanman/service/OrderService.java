package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liangmanman.common.BizException;
import com.liangmanman.common.GeoUtil;
import com.liangmanman.common.NoGenerator;
import com.liangmanman.dto.OrderPreview;
import com.liangmanman.dto.OrderReqs;
import com.liangmanman.dto.OrderView;
import com.liangmanman.dto.ProductView;
import com.liangmanman.entity.Address;
import com.liangmanman.entity.GroupBuy;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.OrderItem;
import com.liangmanman.entity.OrderLog;
import com.liangmanman.entity.Payment;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.Settlement;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.User;
import com.liangmanman.mapper.AddressMapper;
import com.liangmanman.mapper.GroupBuyMapper;
import com.liangmanman.mapper.OrderItemMapper;
import com.liangmanman.mapper.OrderLogMapper;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.PaymentMapper;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.mapper.SettlementMapper;
import com.liangmanman.mapper.StoreMapper;
import com.liangmanman.mapper.UserMapper;
import com.liangmanman.notify.NotificationService;
import com.liangmanman.order.Fulfillment;
import com.liangmanman.order.OrderStatus;
import com.liangmanman.order.PayMethod;
import com.liangmanman.pay.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    public static final String BUYER = "BUYER";
    public static final String MERCHANT = "MERCHANT";
    public static final String SYSTEM = "SYSTEM";
    public static final String ADMIN = "ADMIN";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderLogMapper orderLogMapper;
    private final ProductMapper productMapper;
    private final StoreMapper storeMapper;
    private final AddressMapper addressMapper;
    private final UserMapper userMapper;
    private final PaymentMapper paymentMapper;
    private final SettlementMapper settlementMapper;
    private final GroupBuyMapper groupBuyMapper;
    private final StoreService storeService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper, OrderLogMapper orderLogMapper,
                        ProductMapper productMapper, StoreMapper storeMapper, AddressMapper addressMapper,
                        UserMapper userMapper, PaymentMapper paymentMapper, SettlementMapper settlementMapper,
                        GroupBuyMapper groupBuyMapper, StoreService storeService, PaymentService paymentService,
                        NotificationService notificationService, PlatformTransactionManager txManager) {
        this.tx = new TransactionTemplate(txManager);
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderLogMapper = orderLogMapper;
        this.productMapper = productMapper;
        this.storeMapper = storeMapper;
        this.addressMapper = addressMapper;
        this.userMapper = userMapper;
        this.paymentMapper = paymentMapper;
        this.settlementMapper = settlementMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.storeService = storeService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }

    // =====================================================================
    // 下单
    // =====================================================================

    /** 购物车里的一行：商品 + 数量 + 单价（凑单时是凑单价）+ 小计 */
    private record Line(Product product, int quantity, BigDecimal price, BigDecimal amount) {}

    private record Cart(Store store, List<Line> lines, BigDecimal goodsAmount, GroupBuy group) {}

    public OrderPreview preview(Long buyerId, OrderReqs.Preview req) {
        Cart cart = loadCart(req.storeId(), req.items(), req.groupBuyId());
        Address addr = req.addressId() == null ? null : requireAddress(buyerId, req.addressId());
        boolean online = paymentService.available(cart.store());
        List<OrderPreview.Option> options = new ArrayList<>();
        for (Fulfillment f : Fulfillment.values()) {
            String reason = unavailableReason(cart, f, addr, true);
            BigDecimal fee = fee(cart, f);
            List<String> payMethods = new ArrayList<>();
            if (online) payMethods.add(PayMethod.ONLINE.name());
            if (cart.group() == null && codAllowed(cart.store(), f)) payMethods.add(PayMethod.COD.name());
            if (reason == null && payMethods.isEmpty()) {
                reason = cart.group() != null ? "这家店还没开通在线收款，暂时不能凑单" : "暂无可用的付款方式";
            }
            options.add(new OrderPreview.Option(f.name(), f.label(), reason == null, reason,
                    fee, cart.goodsAmount().add(fee), payMethods));
        }
        List<OrderView.Line> lines = cart.lines().stream().map(l -> new OrderView.Line(
                l.product().getId(), l.product().getName(), ProductView.of(l.product()).firstImage(),
                l.product().getUnit(), l.price(), l.quantity(), l.amount())).toList();
        return new OrderPreview(cart.goodsAmount(), lines, options);
    }

    @Transactional
    public OrderView create(Long buyerId, OrderReqs.Create req) {
        Cart cart = loadCart(req.storeId(), req.items(), req.groupBuyId());
        Store store = cart.store();
        if (store.getOwnerId().equals(buyerId)) {
            throw new BizException("不能在自己的店里下单");
        }
        Fulfillment f = Fulfillment.valueOf(req.fulfillment());
        PayMethod pm = PayMethod.valueOf(req.payMethod());
        Address addr = req.addressId() == null ? null : requireAddress(buyerId, req.addressId());

        String reason = unavailableReason(cart, f, addr, false);
        if (reason != null) {
            throw new BizException(reason);
        }
        GroupBuy group = cart.group();
        if (group != null && pm != PayMethod.ONLINE) {
            throw new BizException("凑单只能在线支付");
        }
        if (pm == PayMethod.COD && !codAllowed(store, f)) {
            throw new BizException(f == Fulfillment.EXPRESS ? "快递发货只能在线支付" : "这家店不支持货到付款");
        }
        if (pm == PayMethod.ONLINE && !paymentService.available(store)) {
            throw new BizException("这家店还没开通在线收款，请选择货到付款");
        }
        if (req.expectedDate() != null && req.expectedDate().isBefore(LocalDate.now())) {
            throw new BizException("期望送达日期不能早于今天");
        }
        if (group != null && group.getLimitPerUser() != null) {
            int bought = groupQtyOfUser(group.getId(), buyerId);
            int want = cart.lines().get(0).quantity();
            if (bought + want > group.getLimitPerUser()) {
                throw new BizException("每人最多凑 " + group.getLimitPerUser() + "，你已经凑了 " + bought);
            }
        }

        // 联系人：优先用地址里的；自提没选地址时用填写的或账号手机号
        String contactName;
        String contactPhone;
        if (addr != null) {
            contactName = addr.getContactName();
            contactPhone = addr.getPhone();
        } else {
            User buyer = userMapper.selectById(buyerId);
            contactName = req.contactName() != null ? req.contactName() : buyer.getNickname();
            contactPhone = req.contactPhone() != null ? req.contactPhone() : buyer.getPhone();
            if (contactPhone == null) {
                throw new BizException("请留一个联系电话，方便老板联系你");
            }
        }

        // 扣库存（不限量的商品跳过）；任一失败整单回滚
        for (Line l : cart.lines()) {
            if (l.product().getStock() != null && productMapper.deductStock(l.product().getId(), l.quantity()) == 0) {
                throw new BizException(l.product().getName() + " 库存不足");
            }
        }

        BigDecimal fee = fee(cart, f);
        OrderStatus initial = pm == PayMethod.COD ? OrderStatus.PENDING_ACCEPT : OrderStatus.PENDING_PAY;

        Order o = new Order();
        o.setOrderNo(NoGenerator.next("D"));
        o.setBuyerId(buyerId);
        o.setStoreId(store.getId());
        o.setGroupBuyId(group == null ? null : group.getId());
        o.setStatus(initial.name());
        o.setFulfillment(f.name());
        o.setPayMethod(pm.name());
        o.setGoodsAmount(cart.goodsAmount());
        o.setDeliveryFee(fee);
        o.setTotalAmount(cart.goodsAmount().add(fee));
        o.setPaid(false);
        o.setCodReceived(false);
        o.setReviewed(false);
        o.setRemindCount(0);
        o.setContactName(contactName);
        o.setContactPhone(contactPhone);
        if (addr != null) {
            o.setAddressText(addr.fullText());
            o.setAddressLat(addr.getLat());
            o.setAddressLng(addr.getLng());
            o.setVillage(addr.getVillage());
        }
        o.setExpectedDate(f == Fulfillment.EXPRESS ? null : req.expectedDate());
        o.setRemark(req.remark());
        orderMapper.insert(o);

        for (Line l : cart.lines()) {
            OrderItem item = new OrderItem();
            item.setOrderId(o.getId());
            item.setProductId(l.product().getId());
            item.setProductName(l.product().getName());
            item.setProductImage(ProductView.of(l.product()).firstImage());
            item.setUnit(l.product().getUnit());
            item.setPrice(l.price());
            item.setQuantity(l.quantity());
            item.setAmount(l.amount());
            orderItemMapper.insert(item);
        }
        writeLog(o.getId(), null, initial, BUYER, group == null ? "下单" : "参加凑单");

        if (initial == OrderStatus.PENDING_ACCEPT) {
            notificationService.merchantNewOrder(orderMapper.selectById(o.getId()));
        }
        return toView(o, true);
    }

    private Cart loadCart(Long storeId, List<OrderReqs.Item> items, Long groupBuyId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null || !Store.APPROVED.equals(store.getStatus())) {
            throw BizException.notFound("店铺");
        }
        if (!Boolean.TRUE.equals(store.getIsOpen())) {
            throw new BizException("店铺已打烊，暂不接单");
        }
        GroupBuy group = null;
        if (groupBuyId != null) {
            group = groupBuyMapper.selectById(groupBuyId);
            if (group == null || !group.getStoreId().equals(storeId)) {
                throw BizException.notFound("凑单");
            }
            if (!GroupBuy.ONGOING.equals(group.getStatus()) || !group.getEndAt().isAfter(LocalDateTime.now())) {
                throw new BizException("这个凑单已经结束了");
            }
            if (items.size() != 1 || !items.get(0).productId().equals(group.getProductId())) {
                throw new BizException("凑单只能买这一个商品");
            }
        }
        // 同一商品出现多次时合并数量
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (OrderReqs.Item it : items) {
            qty.merge(it.productId(), it.quantity(), Integer::sum);
        }
        Map<Long, Product> products = productMapper.selectBatchIds(qty.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Line> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : qty.entrySet()) {
            Product p = products.get(e.getKey());
            if (p == null || !p.getStoreId().equals(storeId)) {
                throw BizException.notFound("商品");
            }
            if (!Boolean.TRUE.equals(p.getOnShelf())) {
                throw new BizException(p.getName() + " 已下架");
            }
            if (p.getStock() != null && p.getStock() < e.getValue()) {
                throw new BizException(p.getName() + " 库存不足，只剩 " + p.getStock() + p.getUnit());
            }
            BigDecimal price = group != null ? group.getGroupPrice() : p.getPrice();
            BigDecimal amount = price.multiply(BigDecimal.valueOf(e.getValue()));
            lines.add(new Line(p, e.getValue(), price, amount));
            total = total.add(amount);
        }
        return new Cart(store, lines, total, group);
    }

    /**
     * 某种履约方式不能用的原因，能用返回 null。
     * previewing = true 时，“没选地址”也视为可用（结算页先显示可选项，提交时再要求地址）。
     */
    private String unavailableReason(Cart cart, Fulfillment f, Address addr, boolean previewing) {
        Store s = cart.store();
        if (f.isLocal()) {
            for (Line l : cart.lines()) {
                if (!Boolean.TRUE.equals(l.product().getLocalDelivery())) {
                    return l.product().getName() + " 只支持快递发货";
                }
            }
        } else {
            for (Line l : cart.lines()) {
                if (!Boolean.TRUE.equals(l.product().getExpressDelivery())) {
                    return l.product().getName() + " 不支持快递发货";
                }
            }
        }
        switch (f) {
            case DELIVERY -> {
                if (!Boolean.TRUE.equals(s.getDeliveryEnabled())) return "这家店暂不送货上门";
                if (cart.group() == null && cart.goodsAmount().compareTo(s.getMinOrderAmount()) < 0) {
                    return "还差 " + s.getMinOrderAmount().subtract(cart.goodsAmount()).stripTrailingZeros().toPlainString() + " 元起送";
                }
                if (addr == null) return previewing ? null : "请选择收货地址";
                double km = GeoUtil.distanceKm(addr.getLat(), addr.getLng(), s.getLat(), s.getLng());
                if (km > s.getDeliveryRadiusKm().doubleValue()) {
                    return "超出配送范围（这家店只送 " + s.getDeliveryRadiusKm().stripTrailingZeros().toPlainString() + " 公里内）";
                }
            }
            case PICKUP -> {
                if (!Boolean.TRUE.equals(s.getPickupEnabled())) return "这家店不支持到店自提";
            }
            case EXPRESS -> {
                if (addr == null) return previewing ? null : "请选择收货地址";
            }
        }
        return null;
    }

    private BigDecimal fee(Cart cart, Fulfillment f) {
        return switch (f) {
            case DELIVERY -> cart.store().getDeliveryFee();
            case PICKUP -> BigDecimal.ZERO;
            // 多件快递商品按其中最高的运费收一次
            case EXPRESS -> cart.lines().stream()
                    .map(l -> l.product().getExpressFee() == null ? BigDecimal.ZERO : l.product().getExpressFee())
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        };
    }

    private boolean codAllowed(Store s, Fulfillment f) {
        return f != Fulfillment.EXPRESS && Boolean.TRUE.equals(s.getCodEnabled());
    }

    private Address requireAddress(Long userId, Long addressId) {
        Address a = addressMapper.selectById(addressId);
        if (a == null || !a.getUserId().equals(userId)) {
            throw BizException.notFound("收货地址");
        }
        return a;
    }

    /** 某人在某个凑单里已经凑了多少（含未付款的） */
    private int groupQtyOfUser(Long groupBuyId, Long userId) {
        List<Long> orderIds = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .select(Order::getId)
                        .eq(Order::getGroupBuyId, groupBuyId)
                        .eq(Order::getBuyerId, userId)
                        .ne(Order::getStatus, OrderStatus.CANCELLED.name()))
                .stream().map(Order::getId).toList();
        if (orderIds.isEmpty()) return 0;
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                .stream().mapToInt(OrderItem::getQuantity).sum();
    }

    // =====================================================================
    // 支付
    // =====================================================================

    public PaymentService.PayParams pay(Long buyerId, Long orderId) {
        Order o = requireBuyerOrder(buyerId, orderId);
        if (!OrderStatus.PENDING_PAY.name().equals(o.getStatus())) {
            throw new BizException("订单不是待支付状态");
        }
        User buyer = userMapper.selectById(buyerId);
        Store store = storeMapper.selectById(o.getStoreId());
        return paymentService.prepay(o, store, buyer.getOpenId());
    }

    /** 支付成功回调（微信通知 / 模拟支付）。可重复调用。 */
    @Transactional
    public void onPaid(String outTradeNo, String transactionId) {
        Payment pay = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOutTradeNo, outTradeNo));
        if (pay == null) {
            throw BizException.notFound("支付单");
        }
        if (Payment.SUCCESS.equals(pay.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getId, pay.getId()).eq(Payment::getStatus, Payment.PENDING)
                .set(Payment::getStatus, Payment.SUCCESS)
                .set(Payment::getTransactionId, transactionId)
                .set(Payment::getPaidAt, now));
        if (updated == 0) {
            return; // 并发回调，另一个已处理
        }
        Order o = orderMapper.selectById(pay.getOrderId());
        if (!OrderStatus.PENDING_PAY.name().equals(o.getStatus())) {
            // 订单已因超时取消，钱到账了就原路退回
            log.warn("订单 {} 状态为 {} 时收到支付，执行退款", o.getOrderNo(), o.getStatus());
            orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, o.getId()).set(Order::getPaid, true).set(Order::getPaidAt, now));
            o.setPaid(true);
            if (!paymentService.refund(o, "订单已取消，自动退款")) {
                log.error("订单 {} 自动退款失败，需要人工处理", o.getOrderNo());
            }
            return;
        }
        Consumer<LambdaUpdateWrapper<Order>> markPaid = w -> w.set(Order::getPaid, true).set(Order::getPaidAt, now);

        if (o.getGroupBuyId() != null) {
            GroupBuy g = groupBuyMapper.selectById(o.getGroupBuyId());
            int qty = itemsOf(o.getId()).stream().mapToInt(OrderItem::getQuantity).sum();
            if (GroupBuy.SUCCESS.equals(g.getStatus())) {
                // 付款时凑单刚好成团：直接进入接单 / 发货
                o = transit(o, OrderStatus.GROUP_WAITING, SYSTEM, "支付成功", markPaid);
                groupBuyMapper.addQty(g.getId(), qty);
                proceedGroupOrder(o);
            } else if (GroupBuy.FAILED.equals(g.getStatus())) {
                o = transit(o, OrderStatus.GROUP_WAITING, SYSTEM, "支付成功", markPaid);
                cancel(o, SYSTEM, "凑单没成，钱原路退回");
            } else {
                o = transit(o, OrderStatus.GROUP_WAITING, SYSTEM, "支付成功，等待成团", markPaid);
                groupBuyMapper.addQty(g.getId(), qty);
            }
            return;
        }
        OrderStatus next = Fulfillment.EXPRESS.name().equals(o.getFulfillment())
                ? OrderStatus.PENDING_SHIP : OrderStatus.PENDING_ACCEPT;
        o = transit(o, next, SYSTEM, "支付成功", markPaid);
        notificationService.merchantNewOrder(o);
    }

    // =====================================================================
    // 凑单订单（由 GroupBuyService 调用，调用方负责事务）
    // =====================================================================

    /** 成团：待成团 → 待接单 / 待发货 */
    public Order proceedGroupOrder(Order o) {
        OrderStatus next = Fulfillment.EXPRESS.name().equals(o.getFulfillment())
                ? OrderStatus.PENDING_SHIP : OrderStatus.PENDING_ACCEPT;
        Order r = transit(o, next, SYSTEM, "凑单成功", null);
        notificationService.buyerOrderChanged(r, "凑单成功，已成团，等老板" + (next == OrderStatus.PENDING_SHIP ? "发货" : "接单"));
        return r;
    }

    /** 没成团：取消并退款 */
    public Order cancelGroupOrder(Order o, String reason) {
        Order r = cancel(o, SYSTEM, reason);
        notificationService.buyerOrderChanged(r, reason);
        return r;
    }

    // =====================================================================
    // 买家操作
    // =====================================================================

    @Transactional
    public OrderView buyerCancel(Long buyerId, Long orderId, String reason) {
        Order o = requireBuyerOrder(buyerId, orderId);
        OrderStatus s = OrderStatus.valueOf(o.getStatus());
        if (s != OrderStatus.PENDING_PAY && s != OrderStatus.PENDING_ACCEPT
                && s != OrderStatus.PENDING_SHIP && s != OrderStatus.GROUP_WAITING) {
            throw new BizException("老板已经接单了，取消请直接电话联系老板");
        }
        String r = reason == null || reason.isBlank()
                ? (s == OrderStatus.GROUP_WAITING ? "买家退出凑单" : "买家取消") : reason;
        o = cancel(o, BUYER, r);
        return toView(o, true);
    }

    @Transactional
    public OrderView buyerConfirm(Long buyerId, Long orderId) {
        Order o = requireBuyerOrder(buyerId, orderId);
        OrderStatus s = OrderStatus.valueOf(o.getStatus());
        boolean ok = s == OrderStatus.SHIPPED
                || (s == OrderStatus.DELIVERED && PayMethod.ONLINE.name().equals(o.getPayMethod()));
        if (!ok) {
            throw new BizException("现在还不能确认收货");
        }
        return toView(complete(o, BUYER, "买家确认收货"), true);
    }

    public List<OrderView> buyerOrders(Long buyerId, String status) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getBuyerId, buyerId)
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .orderByDesc(Order::getId)
                .last("LIMIT 100"));
        return toViews(orders);
    }

    public OrderView buyerOrder(Long buyerId, Long orderId) {
        return toView(requireBuyerOrder(buyerId, orderId), true);
    }

    public record Reorder(Long storeId, String storeName, List<ProductView> products, Map<Long, Integer> quantities) {}

    /** 再来一单：返回仍在售的商品和上次的数量，前端放进购物车 */
    public Reorder reorder(Long buyerId, Long orderId) {
        Order o = requireBuyerOrder(buyerId, orderId);
        Store store = storeMapper.selectById(o.getStoreId());
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (OrderItem i : itemsOf(o.getId())) {
            qty.merge(i.getProductId(), i.getQuantity(), Integer::sum);
        }
        List<ProductView> products = qty.isEmpty() ? List.of() : productMapper.selectBatchIds(qty.keySet()).stream()
                .filter(p -> Boolean.TRUE.equals(p.getOnShelf()))
                .map(ProductView::of).toList();
        Map<Long, Integer> kept = new LinkedHashMap<>();
        products.forEach(p -> kept.put(p.id(), qty.get(p.id())));
        if (products.isEmpty()) {
            throw new BizException("上次买的商品都已经下架了");
        }
        return new Reorder(store.getId(), store.getName(), products, kept);
    }

    public Order requireBuyerOrder(Long buyerId, Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null || !o.getBuyerId().equals(buyerId)) {
            throw BizException.notFound("订单");
        }
        return o;
    }

    // =====================================================================
    // 商家操作
    // =====================================================================

    public enum MerchantTab { NEW, DOING, DONE, CANCELLED }

    public List<OrderView> merchantOrders(Long userId, MerchantTab tab) {
        Store store = storeService.requireMyStore(userId);
        List<String> statuses = switch (tab) {
            case NEW -> List.of(OrderStatus.PENDING_ACCEPT.name(), OrderStatus.PENDING_SHIP.name());
            case DOING -> List.of(OrderStatus.ACCEPTED.name(), OrderStatus.DELIVERING.name(),
                    OrderStatus.DELIVERED.name(), OrderStatus.READY_PICKUP.name(), OrderStatus.SHIPPED.name());
            case DONE -> List.of(OrderStatus.COMPLETED.name());
            case CANCELLED -> List.of(OrderStatus.CANCELLED.name());
        };
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStoreId, store.getId())
                .in(Order::getStatus, statuses)
                .orderBy(true, tab == MerchantTab.NEW, Order::getId) // 新订单先来的排前面
                .last("LIMIT 200"));
        return toViews(orders);
    }

    public OrderView merchantOrder(Long userId, Long orderId) {
        return toView(requireStoreOrder(userId, orderId), true);
    }

    @Transactional
    public OrderView accept(Long userId, Long orderId) {
        Order o = requireStoreOrder(userId, orderId);
        OrderStatus next = Fulfillment.PICKUP.name().equals(o.getFulfillment())
                ? OrderStatus.READY_PICKUP : OrderStatus.ACCEPTED;
        o = transit(o, next, MERCHANT, "商家接单", w -> w.set(Order::getAcceptedAt, LocalDateTime.now()));
        notificationService.buyerOrderChanged(o, next == OrderStatus.READY_PICKUP ? "老板已接单，可自提了" : "老板已接单，准备送货");
        return toView(o, true);
    }

    @Transactional
    public OrderView reject(Long userId, Long orderId, String reason) {
        Order o = requireStoreOrder(userId, orderId);
        if (!OrderStatus.PENDING_ACCEPT.name().equals(o.getStatus())) {
            throw new BizException("只有待接单的订单能拒单");
        }
        o = cancel(o, MERCHANT, reason == null || reason.isBlank() ? "商家拒单" : reason);
        notificationService.buyerOrderChanged(o, "老板没接单，订单已取消：" + o.getCancelReason());
        return toView(o, true);
    }

    @Transactional
    public OrderView dispatch(Long userId, Long orderId) {
        Order o = requireStoreOrder(userId, orderId);
        o = transit(o, OrderStatus.DELIVERING, MERCHANT, "出发送货", w -> w.set(Order::getDispatchedAt, LocalDateTime.now()));
        notificationService.buyerOrderChanged(o, "老板出发送货了，送货中");
        return toView(o, true);
    }

    @Transactional
    public OrderView delivered(Long userId, Long orderId) {
        Order o = requireStoreOrder(userId, orderId);
        o = transit(o, OrderStatus.DELIVERED, MERCHANT, "已送达", w -> w.set(Order::getDeliveredAt, LocalDateTime.now()));
        notificationService.buyerOrderChanged(o, "货已送达，请检查");
        return toView(o, true);
    }

    /** 货到付款：老板收到钱后点“已收款”，订单完成 */
    @Transactional
    public OrderView codReceived(Long userId, Long orderId) {
        Order o = requireStoreOrder(userId, orderId);
        if (!PayMethod.COD.name().equals(o.getPayMethod())) {
            throw new BizException("这不是货到付款的订单");
        }
        OrderStatus s = OrderStatus.valueOf(o.getStatus());
        if (s != OrderStatus.DELIVERED && s != OrderStatus.READY_PICKUP) {
            throw new BizException("送达或自提时才能确认收款");
        }
        o = complete(o, MERCHANT, "已收款", w -> w.set(Order::getCodReceived, true));
        return toView(o, true);
    }

    /** 自提订单：买家来店里拿走了 */
    @Transactional
    public OrderView pickedUp(Long userId, Long orderId) {
        Order o = requireStoreOrder(userId, orderId);
        if (!OrderStatus.READY_PICKUP.name().equals(o.getStatus())) {
            throw new BizException("订单不是待自提状态");
        }
        boolean cod = PayMethod.COD.name().equals(o.getPayMethod());
        o = complete(o, MERCHANT, "已提货", w -> {
            if (cod) w.set(Order::getCodReceived, true);
        });
        return toView(o, true);
    }

    @Transactional
    public OrderView ship(Long userId, Long orderId, OrderReqs.Ship req) {
        Order o = requireStoreOrder(userId, orderId);
        o = transit(o, OrderStatus.SHIPPED, MERCHANT, "已发货 " + req.expressCompany() + " " + req.expressNo(), w -> w
                .set(Order::getExpressCompany, req.expressCompany().trim())
                .set(Order::getExpressNo, req.expressNo().trim())
                .set(Order::getShippedAt, LocalDateTime.now()));
        notificationService.buyerOrderChanged(o, "已发货：" + req.expressCompany() + " " + req.expressNo());
        return toView(o, true);
    }

    /** 接单后商家取消（如缺货），已付款的原路退款 */
    @Transactional
    public OrderView merchantCancel(Long userId, Long orderId, String reason) {
        Order o = requireStoreOrder(userId, orderId);
        OrderStatus s = OrderStatus.valueOf(o.getStatus());
        if (s != OrderStatus.ACCEPTED && s != OrderStatus.READY_PICKUP && s != OrderStatus.PENDING_SHIP) {
            throw new BizException("当前状态不能取消");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException("请填写取消原因");
        }
        o = cancel(o, MERCHANT, reason);
        notificationService.buyerOrderChanged(o, "老板取消了订单：" + reason);
        return toView(o, true);
    }

    /** 今日送货清单：送货上门、已接单 / 送货中，期望日期不晚于 date，按村分组 */
    public Map<String, List<OrderView>> deliverySheet(Long userId, LocalDate date) {
        Store store = storeService.requireMyStore(userId);
        LocalDate day = date == null ? LocalDate.now() : date;
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStoreId, store.getId())
                .eq(Order::getFulfillment, Fulfillment.DELIVERY.name())
                .in(Order::getStatus, OrderStatus.ACCEPTED.name(), OrderStatus.DELIVERING.name())
                .and(w -> w.isNull(Order::getExpectedDate).or().le(Order::getExpectedDate, day))
                .orderByAsc(Order::getVillage)
                .orderByAsc(Order::getId));
        Map<String, List<OrderView>> sheet = new LinkedHashMap<>();
        for (OrderView v : toViews(orders)) {
            String village = v.village() == null || v.village().isBlank() ? "未填村" : v.village();
            sheet.computeIfAbsent(village, k -> new ArrayList<>()).add(v);
        }
        return sheet;
    }

    private Order requireStoreOrder(Long userId, Long orderId) {
        Store store = storeService.requireMyStore(userId);
        Order o = orderMapper.selectById(orderId);
        // 未支付的在线订单对商家不可见
        if (o == null || !o.getStoreId().equals(store.getId())
                || OrderStatus.PENDING_PAY.name().equals(o.getStatus())) {
            throw BizException.notFound("订单");
        }
        return o;
    }

    // =====================================================================
    // 运营仲裁
    // =====================================================================

    /** 运营强制取消：任何未结束的订单都可以，已付款的原路退款 */
    @Transactional
    public OrderView adminCancel(Long orderId, String adminName, String reason) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.notFound("订单");
        }
        if (OrderStatus.valueOf(o.getStatus()).isFinished()) {
            throw new BizException("订单已经结束，不能取消");
        }
        String r = "平台处理（" + adminName + "）：" + reason;
        o = doCancel(o, ADMIN, r, true);
        notificationService.buyerOrderChanged(o, "订单已由平台取消");
        return toView(o, true);
    }

    // =====================================================================
    // 定时任务（由 OrderTimeoutJob 调用）
    // =====================================================================

    public int cancelUnpaid(int minutes) {
        LocalDateTime before = LocalDateTime.now().minusMinutes(minutes);
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_PAY.name())
                .lt(Order::getCreatedAt, before)
                .last("LIMIT 500"));
        return forEachSafely(list, o -> cancel(o, SYSTEM, "超时未支付，订单已取消"));
    }

    /** 待接单超时（凑单成团的订单不受限制：一次进来几十单，老板需要时间） */
    public int cancelUnaccepted(int minutes) {
        List<Order> list = unacceptedSince(minutes, null);
        return forEachSafely(list, o -> {
            Order c = cancel(o, SYSTEM, "老板 " + minutes + " 分钟内没接单，订单已取消");
            notificationService.buyerOrderChanged(c, "老板没及时接单，已取消" + (Boolean.TRUE.equals(c.getPaid()) ? "，钱会原路退回" : ""));
        });
    }

    /** 下单 N 分钟还没接单：再打一次电话（只催一次） */
    public int remindUnaccepted(int minutes) {
        List<Order> list = unacceptedSince(minutes, 0);
        int n = 0;
        for (Order o : list) {
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, o.getId()).eq(Order::getRemindCount, 0)
                    .eq(Order::getStatus, OrderStatus.PENDING_ACCEPT.name())
                    .set(Order::getRemindCount, 1));
            if (updated > 0) {
                notificationService.merchantRemind(o);
                n++;
            }
        }
        return n;
    }

    private List<Order> unacceptedSince(int minutes, Integer remindCount) {
        LocalDateTime before = LocalDateTime.now().minusMinutes(minutes);
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_ACCEPT.name())
                .isNull(Order::getGroupBuyId)
                .eq(remindCount != null, Order::getRemindCount, remindCount)
                .and(w -> w
                        .and(a -> a.eq(Order::getPayMethod, PayMethod.COD.name()).lt(Order::getCreatedAt, before))
                        .or(a -> a.eq(Order::getPayMethod, PayMethod.ONLINE.name()).lt(Order::getPaidAt, before)))
                .last("LIMIT 500"));
    }

    /** 在线支付的送货单送达后 N 小时、快递发货后 N 天，自动确认收货 */
    public int autoConfirm(int deliveredHours, int shippedDays) {
        LocalDateTime now = LocalDateTime.now();
        List<Order> list = new ArrayList<>(orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.DELIVERED.name())
                .eq(Order::getPayMethod, PayMethod.ONLINE.name())
                .lt(Order::getDeliveredAt, now.minusHours(deliveredHours))
                .last("LIMIT 500")));
        list.addAll(orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.SHIPPED.name())
                .lt(Order::getShippedAt, now.minusDays(shippedDays))
                .last("LIMIT 500")));
        return forEachSafely(list, o -> complete(o, SYSTEM, "自动确认收货"));
    }

    /** 每个订单单独一个事务：一单失败不影响其他单 */
    public int forEachSafely(List<Order> list, Consumer<Order> action) {
        int done = 0;
        for (Order o : list) {
            try {
                tx.executeWithoutResult(status -> action.accept(o));
                done++;
            } catch (BizException e) {
                // 状态已被其他操作改变（如买家刚好付款），跳过
                log.debug("订单 {} 跳过：{}", o.getOrderNo(), e.getMessage());
            } catch (Exception e) {
                log.error("订单 {} 自动处理失败", o.getOrderNo(), e);
            }
        }
        return done;
    }

    // =====================================================================
    // 状态流转核心
    // =====================================================================

    private Order transit(Order o, OrderStatus to, String operator, String note,
                          Consumer<LambdaUpdateWrapper<Order>> extra) {
        return transit(o, to, operator, note, extra, false);
    }

    /**
     * 条件更新：只有数据库里仍是原状态才更新成功，防止两个人同时操作同一订单。
     * force = true 时跳过流转规则（仅运营仲裁取消用）。
     */
    private Order transit(Order o, OrderStatus to, String operator, String note,
                          Consumer<LambdaUpdateWrapper<Order>> extra, boolean force) {
        OrderStatus from = OrderStatus.valueOf(o.getStatus());
        if (!force && !from.canGoTo(to)) {
            throw new BizException("订单" + from.label() + "，不能" + (to == OrderStatus.CANCELLED ? "取消" : "改成" + to.label()));
        }
        LambdaUpdateWrapper<Order> w = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, o.getId())
                .eq(Order::getStatus, from.name())
                .set(Order::getStatus, to.name())
                .set(Order::getUpdatedAt, LocalDateTime.now());
        if (extra != null) {
            extra.accept(w);
        }
        if (orderMapper.update(null, w) == 0) {
            throw new BizException(BizException.CONFLICT, "订单状态已变化，请刷新后再试");
        }
        writeLog(o.getId(), from, to, operator, note);
        return orderMapper.selectById(o.getId());
    }

    private Order complete(Order o, String operator, String note) {
        return complete(o, operator, note, null);
    }

    private Order complete(Order o, String operator, String note, Consumer<LambdaUpdateWrapper<Order>> extra) {
        Order done = transit(o, OrderStatus.COMPLETED, operator, note, w -> {
            w.set(Order::getCompletedAt, LocalDateTime.now());
            if (extra != null) extra.accept(w);
        });
        for (OrderItem item : itemsOf(done.getId())) {
            productMapper.addSales(item.getProductId(), item.getQuantity());
        }
        // 在线支付的钱在收付通里冻结，完成后记一条待解冻（由 SettlementJob 调用“完结分账”）；货到付款不经过平台
        if (Boolean.TRUE.equals(done.getPaid())) {
            Payment pay = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getOrderId, done.getId()).eq(Payment::getStatus, Payment.SUCCESS).last("LIMIT 1"));
            Settlement st = new Settlement();
            st.setOrderId(done.getId());
            st.setStoreId(done.getStoreId());
            st.setSubMchId(pay == null ? null : pay.getSubMchId());
            st.setTransactionId(pay == null ? null : pay.getTransactionId());
            st.setAmount(done.getTotalAmount());
            st.setPlatformFee(BigDecimal.ZERO);
            st.setStatus(Settlement.PENDING_RELEASE);
            settlementMapper.insert(st);
        }
        notificationService.buyerOrderChanged(done, "订单已完成，欢迎评价");
        return done;
    }

    private Order cancel(Order o, String operator, String reason) {
        return doCancel(o, operator, reason, false);
    }

    private Order doCancel(Order o, String operator, String reason, boolean force) {
        boolean wasGroupWaiting = OrderStatus.GROUP_WAITING.name().equals(o.getStatus());
        Order c = transit(o, OrderStatus.CANCELLED, operator, reason, w -> w
                .set(Order::getCancelReason, reason)
                .set(Order::getCancelledAt, LocalDateTime.now()), force);
        List<OrderItem> items = itemsOf(c.getId());
        for (OrderItem item : items) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
        }
        if (wasGroupWaiting && c.getGroupBuyId() != null) {
            groupBuyMapper.addQty(c.getGroupBuyId(), -items.stream().mapToInt(OrderItem::getQuantity).sum());
        }
        if (Boolean.TRUE.equals(c.getPaid())) {
            if (!paymentService.refund(c, reason)) {
                log.error("订单 {} 退款失败，需要人工处理", c.getOrderNo());
            }
        }
        return c;
    }

    private void writeLog(Long orderId, OrderStatus from, OrderStatus to, String operator, String note) {
        OrderLog l = new OrderLog();
        l.setOrderId(orderId);
        l.setFromStatus(from == null ? null : from.name());
        l.setToStatus(to.name());
        l.setOperator(operator);
        l.setNote(note == null ? null : (note.length() > 200 ? note.substring(0, 200) : note));
        orderLogMapper.insert(l);
    }

    // =====================================================================
    // 组装返回
    // =====================================================================

    public List<OrderItem> itemsOf(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    }

    public List<OrderView> toViews(List<Order> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, ids))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        Set<Long> storeIds = orders.stream().map(Order::getStoreId).collect(Collectors.toSet());
        Map<Long, Store> stores = storeMapper.selectBatchIds(storeIds).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
        return orders.stream()
                .map(o -> build(o, items.getOrDefault(o.getId(), List.of()), stores.get(o.getStoreId()), List.of()))
                .toList();
    }

    public OrderView toView(Order o, boolean withLogs) {
        List<OrderLog> logs = withLogs
                ? orderLogMapper.selectList(new LambdaQueryWrapper<OrderLog>()
                        .eq(OrderLog::getOrderId, o.getId()).orderByAsc(OrderLog::getId))
                : List.of();
        return build(o, itemsOf(o.getId()), storeMapper.selectById(o.getStoreId()), logs);
    }

    private OrderView build(Order o, List<OrderItem> items, Store store, List<OrderLog> logs) {
        OrderStatus s = OrderStatus.valueOf(o.getStatus());
        Fulfillment f = Fulfillment.valueOf(o.getFulfillment());
        PayMethod pm = PayMethod.valueOf(o.getPayMethod());
        return new OrderView(
                o.getId(), o.getOrderNo(), o.getGroupBuyId(), Boolean.TRUE.equals(o.getReviewed()),
                s.name(), statusLabel(o, s), f.name(), f.label(), pm.name(), pm.label(),
                o.getGoodsAmount(), o.getDeliveryFee(), o.getTotalAmount(),
                Boolean.TRUE.equals(o.getPaid()), Boolean.TRUE.equals(o.getCodReceived()),
                o.getContactName(), o.getContactPhone(), o.getAddressText(), o.getAddressLat(), o.getAddressLng(),
                o.getVillage(), o.getExpectedDate(), o.getRemark(), o.getExpressCompany(), o.getExpressNo(),
                o.getCancelReason(), o.getCreatedAt(), o.getPaidAt(), o.getCompletedAt(),
                o.getStoreId(), store == null ? null : store.getName(), store == null ? null : store.getPhone(),
                items.stream().map(i -> new OrderView.Line(i.getProductId(), i.getProductName(), i.getProductImage(),
                        i.getUnit(), i.getPrice(), i.getQuantity(), i.getAmount())).toList(),
                buyerActions(o, s), merchantActions(o, s),
                logs.stream().map(l -> new OrderView.Log(l.getToStatus(), OrderStatus.valueOf(l.getToStatus()).label(),
                        l.getOperator(), l.getNote(), l.getCreatedAt())).toList());
    }

    /** 送达但还没收钱的货到付款单，显示“已送达·待收款”更直观 */
    private String statusLabel(Order o, OrderStatus s) {
        if (s == OrderStatus.DELIVERED && PayMethod.COD.name().equals(o.getPayMethod())
                && !Boolean.TRUE.equals(o.getCodReceived())) {
            return "已送达·待收款";
        }
        return s.label();
    }

    private List<String> buyerActions(Order o, OrderStatus s) {
        List<String> a = new ArrayList<>();
        switch (s) {
            case PENDING_PAY -> { a.add("PAY"); a.add("CANCEL"); }
            case GROUP_WAITING, PENDING_ACCEPT, PENDING_SHIP -> a.add("CANCEL");
            case DELIVERED -> { if (PayMethod.ONLINE.name().equals(o.getPayMethod())) a.add("CONFIRM"); }
            case SHIPPED -> a.add("CONFIRM");
            case COMPLETED -> {
                if (!Boolean.TRUE.equals(o.getReviewed())) a.add("REVIEW");
                a.add("REORDER");
            }
            case CANCELLED -> a.add("REORDER");
            default -> { }
        }
        return a;
    }

    private List<String> merchantActions(Order o, OrderStatus s) {
        boolean cod = PayMethod.COD.name().equals(o.getPayMethod());
        List<String> a = new ArrayList<>();
        switch (s) {
            case PENDING_ACCEPT -> { a.add("ACCEPT"); a.add("REJECT"); }
            case ACCEPTED -> { a.add("DISPATCH"); a.add("CANCEL"); }
            case DELIVERING -> a.add("DELIVERED");
            case DELIVERED -> { if (cod && !Boolean.TRUE.equals(o.getCodReceived())) a.add("COD_RECEIVED"); }
            case READY_PICKUP -> { a.add("PICKED_UP"); a.add("CANCEL"); }
            case PENDING_SHIP -> { a.add("SHIP"); a.add("CANCEL"); }
            default -> { }
        }
        return a;
    }
}
