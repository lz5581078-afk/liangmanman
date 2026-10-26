package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liangmanman.common.GeoUtil;
import com.liangmanman.common.Mask;
import com.liangmanman.dto.GroupBuyView;
import com.liangmanman.dto.ProductView;
import com.liangmanman.entity.GroupBuy;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.OrderItem;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.User;
import com.liangmanman.mapper.OrderItemMapper;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.mapper.StoreMapper;
import com.liangmanman.mapper.UserMapper;
import com.liangmanman.order.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 组装凑单展示数据（只依赖 Mapper，店铺页、凑单页、运营后台共用） */
@Component
public class GroupBuyViewer {

    /** 已付款、未取消的凑单订单才算“参加了” */
    static final List<String> JOINED = List.of(OrderStatus.GROUP_WAITING.name(), OrderStatus.PENDING_ACCEPT.name(),
            OrderStatus.ACCEPTED.name(), OrderStatus.DELIVERING.name(), OrderStatus.DELIVERED.name(),
            OrderStatus.READY_PICKUP.name(), OrderStatus.PENDING_SHIP.name(), OrderStatus.SHIPPED.name(),
            OrderStatus.COMPLETED.name());

    private final ProductMapper productMapper;
    private final StoreMapper storeMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;

    public GroupBuyViewer(ProductMapper productMapper, StoreMapper storeMapper, OrderMapper orderMapper,
                          OrderItemMapper orderItemMapper, UserMapper userMapper) {
        this.productMapper = productMapper;
        this.storeMapper = storeMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.userMapper = userMapper;
    }

    public List<GroupBuyView> views(List<GroupBuy> groups, boolean withMembers, Long userId, Double lat, Double lng) {
        if (groups.isEmpty()) return List.of();
        Map<Long, Product> products = productMapper.selectBatchIds(groups.stream().map(GroupBuy::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Store> stores = storeMapper.selectBatchIds(groups.stream().map(GroupBuy::getStoreId).toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        List<GroupBuyView> out = new ArrayList<>();
        for (GroupBuy g : groups) {
            out.add(view(g, products.get(g.getProductId()), stores.get(g.getStoreId()), withMembers, userId, lat, lng));
        }
        return out;
    }

    public GroupBuyView view(GroupBuy g, boolean withMembers, Long userId, Double lat, Double lng) {
        return view(g, productMapper.selectById(g.getProductId()), storeMapper.selectById(g.getStoreId()),
                withMembers, userId, lat, lng);
    }

    private GroupBuyView view(GroupBuy g, Product p, Store s, boolean withMembers, Long userId, Double lat, Double lng) {
        List<GroupBuyView.Member> members = null;
        Integer mine = null;
        if (withMembers || userId != null) {
            List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .eq(Order::getGroupBuyId, g.getId())
                    .in(Order::getStatus, JOINED)
                    .orderByDesc(Order::getPaidAt));
            Map<Long, Integer> qty = orders.isEmpty() ? Map.of() : orderItemMapper.selectList(
                            new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orders.stream().map(Order::getId).toList()))
                    .stream().collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.summingInt(OrderItem::getQuantity)));
            if (userId != null) {
                mine = orders.stream().filter(o -> o.getBuyerId().equals(userId))
                        .mapToInt(o -> qty.getOrDefault(o.getId(), 0)).sum();
            }
            if (withMembers) {
                List<Order> shown = orders.stream().limit(30).toList();
                Map<Long, User> users = shown.isEmpty() ? Map.of() : userMapper.selectBatchIds(
                                shown.stream().map(Order::getBuyerId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(User::getId, Function.identity()));
                members = shown.stream().map(o -> new GroupBuyView.Member(
                        Mask.nickname(users.containsKey(o.getBuyerId()) ? users.get(o.getBuyerId()).getNickname() : null),
                        o.getVillage(), qty.getOrDefault(o.getId(), 0), o.getPaidAt())).toList();
            }
        }
        Double distance = (lat != null && lng != null && s != null)
                ? GeoUtil.round1(GeoUtil.distanceKm(lat, lng, s.getLat().doubleValue(), s.getLng().doubleValue()))
                : null;
        ProductView pv = p == null ? null : ProductView.of(p);
        return new GroupBuyView(
                g.getId(), g.getStoreId(), s == null ? null : s.getName(), s == null ? null : s.getPhone(),
                g.getProductId(), pv == null ? "商品已删除" : pv.name(), pv == null ? null : pv.firstImage(),
                pv == null ? null : pv.unit(), pv == null ? null : pv.price(), g.getGroupPrice(),
                g.getTargetQty(), Math.max(0, g.getCurrentQty()), g.getLimitPerUser(), g.getEndAt(),
                Boolean.TRUE.equals(g.getEndWhenReached()), g.getStatus(), GroupBuy.statusLabel(g.getStatus()),
                g.getDecisionDeadline(), members, mine, distance,
                pv != null && pv.localDelivery(), pv != null && pv.expressDelivery());
    }
}
