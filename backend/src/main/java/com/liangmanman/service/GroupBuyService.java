package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liangmanman.common.BizException;
import com.liangmanman.common.GeoUtil;
import com.liangmanman.config.AppProperties;
import com.liangmanman.dto.GroupBuyView;
import com.liangmanman.entity.GroupBuy;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.Store;
import com.liangmanman.mapper.GroupBuyMapper;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.notify.NotificationService;
import com.liangmanman.order.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 凑单：老板设目标数量和凑单价，农户付款参加；
 * 到期（或设置了“凑够就截止”并已凑够）后：
 * - 凑够了 → 成团，所有订单进入待接单 / 待发货
 * - 没凑够 → 老板 24 小时内决定“照样发货”或“全部退款”，不决定自动退款
 * - 没人参加 → 直接结束
 */
@Slf4j
@Service
public class GroupBuyService {

    private static final int DECISION_HOURS = 24;

    private final GroupBuyMapper groupBuyMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final StoreService storeService;
    private final OrderService orderService;
    private final GroupBuyViewer viewer;
    private final NotificationService notificationService;
    private final AppProperties props;

    public GroupBuyService(GroupBuyMapper groupBuyMapper, ProductMapper productMapper, OrderMapper orderMapper,
                           StoreService storeService, OrderService orderService, GroupBuyViewer viewer,
                           NotificationService notificationService, AppProperties props) {
        this.groupBuyMapper = groupBuyMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.storeService = storeService;
        this.orderService = orderService;
        this.viewer = viewer;
        this.notificationService = notificationService;
        this.props = props;
    }

    public record CreateReq(
            @NotNull(message = "请选择商品") Long productId,
            @NotNull(message = "请填写凑单价") @DecimalMin(value = "0.01", message = "凑单价要大于 0") BigDecimal groupPrice,
            @NotNull(message = "请填写凑够多少") @Min(value = 2, message = "至少凑 2 份") @Max(value = 99999, message = "数量太大了") Integer targetQty,
            @NotNull(message = "请选择几天") @Min(value = 1, message = "至少 1 天") @Max(value = 30, message = "最多 30 天") Integer days,
            @Min(value = 1, message = "每人限购至少 1") Integer limitPerUser,
            Boolean endWhenReached) {}

    // ---------- 商家 ----------

    @Transactional
    public GroupBuyView create(Long userId, CreateReq req) {
        Store store = storeService.requireMyStore(userId);
        Product p = productMapper.selectById(req.productId());
        if (p == null || !p.getStoreId().equals(store.getId())) {
            throw BizException.notFound("商品");
        }
        if (!Boolean.TRUE.equals(p.getOnShelf())) {
            throw new BizException("商品已下架，先上架再开凑单");
        }
        if (req.groupPrice().compareTo(p.getPrice()) >= 0) {
            throw new BizException("凑单价要比原价（" + p.getPrice().stripTrailingZeros().toPlainString() + " 元）低");
        }
        long ongoing = groupBuyMapper.selectCount(new LambdaQueryWrapper<GroupBuy>()
                .eq(GroupBuy::getProductId, p.getId()).eq(GroupBuy::getStatus, GroupBuy.ONGOING));
        if (ongoing > 0) {
            throw new BizException("这个商品已经有一个进行中的凑单");
        }
        GroupBuy g = new GroupBuy();
        g.setStoreId(store.getId());
        g.setProductId(p.getId());
        g.setGroupPrice(req.groupPrice());
        g.setTargetQty(req.targetQty());
        g.setCurrentQty(0);
        g.setLimitPerUser(req.limitPerUser());
        g.setEndWhenReached(Boolean.TRUE.equals(req.endWhenReached()));
        g.setEndAt(LocalDateTime.now().plusDays(req.days()));
        g.setStatus(GroupBuy.ONGOING);
        groupBuyMapper.insert(g);
        return viewer.view(g, true, null, null, null);
    }

    public List<GroupBuyView> mine(Long userId) {
        Store store = storeService.requireMyStore(userId);
        List<GroupBuy> list = groupBuyMapper.selectList(new LambdaQueryWrapper<GroupBuy>()
                .eq(GroupBuy::getStoreId, store.getId())
                .orderByDesc(GroupBuy::getId)
                .last("LIMIT 100"));
        return viewer.views(list, true, null, null, null);
    }

    /** 没凑够时老板决定：SHIP 照样发货 / REFUND 全部退款 */
    public GroupBuyView decide(Long userId, Long id, String action) {
        GroupBuy g = requireMine(userId, id);
        if (!GroupBuy.WAIT_DECISION.equals(g.getStatus())) {
            throw new BizException("现在不需要做决定");
        }
        if ("SHIP".equals(action)) {
            succeed(g, GroupBuy.WAIT_DECISION, "老板决定照样发货");
        } else if ("REFUND".equals(action)) {
            fail(g, GroupBuy.WAIT_DECISION, "凑单没凑够，老板选择退款，钱原路退回");
        } else {
            throw new BizException("请选择照样发货或全部退款");
        }
        return viewer.view(groupBuyMapper.selectById(id), true, null, null, null);
    }

    /** 提前结束：马上按是否凑够处理 */
    public GroupBuyView close(Long userId, Long id) {
        GroupBuy g = requireMine(userId, id);
        if (!GroupBuy.ONGOING.equals(g.getStatus())) {
            throw new BizException("凑单已经结束了");
        }
        settle(g);
        return viewer.view(groupBuyMapper.selectById(id), true, null, null, null);
    }

    private GroupBuy requireMine(Long userId, Long id) {
        Store store = storeService.requireMyStore(userId);
        GroupBuy g = groupBuyMapper.selectById(id);
        if (g == null || !g.getStoreId().equals(store.getId())) {
            throw BizException.notFound("凑单");
        }
        return g;
    }

    // ---------- 买家 ----------

    public GroupBuyView detail(Long id, Long userId, Double lat, Double lng) {
        GroupBuy g = groupBuyMapper.selectById(id);
        if (g == null) {
            throw BizException.notFound("凑单");
        }
        return viewer.view(g, true, userId, lat, lng);
    }

    public List<GroupBuyView> nearby(double lat, double lng) {
        double radius = props.search().defaultRadiusKm();
        Set<Long> storeIds = storeService.approvedWithin(lat, lng, radius).stream()
                .filter(s -> GeoUtil.distanceKm(lat, lng, s.getLat().doubleValue(), s.getLng().doubleValue()) <= radius)
                .map(Store::getId).collect(Collectors.toSet());
        if (storeIds.isEmpty()) return List.of();
        List<GroupBuy> list = groupBuyMapper.selectList(new LambdaQueryWrapper<GroupBuy>()
                .in(GroupBuy::getStoreId, storeIds)
                .eq(GroupBuy::getStatus, GroupBuy.ONGOING)
                .gt(GroupBuy::getEndAt, LocalDateTime.now())
                .orderByAsc(GroupBuy::getEndAt)
                .last("LIMIT 20"));
        return viewer.views(list, false, null, lat, lng).stream()
                .sorted((a, b) -> Double.compare(a.distanceKm() == null ? 0 : a.distanceKm(), b.distanceKm() == null ? 0 : b.distanceKm()))
                .toList();
    }

    /** 店铺页用：进行中的凑单 */
    public List<GroupBuyView> ongoingOfStore(Long storeId) {
        return viewer.views(groupBuyMapper.selectList(new LambdaQueryWrapper<GroupBuy>()
                .eq(GroupBuy::getStoreId, storeId)
                .eq(GroupBuy::getStatus, GroupBuy.ONGOING)
                .gt(GroupBuy::getEndAt, LocalDateTime.now())
                .orderByAsc(GroupBuy::getEndAt)), false, null, null, null);
    }

    // ---------- 结算（定时任务 + 提前结束） ----------

    /** 到期或已凑够（凑够即截止）的凑单 */
    public int settleDue() {
        List<GroupBuy> due = groupBuyMapper.selectList(new LambdaQueryWrapper<GroupBuy>()
                .eq(GroupBuy::getStatus, GroupBuy.ONGOING)
                .and(w -> w.le(GroupBuy::getEndAt, LocalDateTime.now())
                        .or(a -> a.eq(GroupBuy::getEndWhenReached, true)
                                .apply("current_qty >= target_qty")))
                .last("LIMIT 100"));
        int n = 0;
        for (GroupBuy g : due) {
            try {
                settle(g);
                n++;
            } catch (Exception e) {
                log.error("凑单 {} 结算失败", g.getId(), e);
            }
        }
        return n;
    }

    /** 老板 24 小时没决定：自动退款 */
    public int decisionTimeout() {
        List<GroupBuy> list = groupBuyMapper.selectList(new LambdaQueryWrapper<GroupBuy>()
                .eq(GroupBuy::getStatus, GroupBuy.WAIT_DECISION)
                .lt(GroupBuy::getDecisionDeadline, LocalDateTime.now())
                .last("LIMIT 100"));
        list.forEach(g -> fail(g, GroupBuy.WAIT_DECISION, "凑单没凑够，钱原路退回"));
        return list.size();
    }

    void settle(GroupBuy g) {
        GroupBuy fresh = groupBuyMapper.selectById(g.getId());
        if (fresh.getCurrentQty() >= fresh.getTargetQty()) {
            succeed(fresh, GroupBuy.ONGOING, null);
        } else if (waitingOrders(fresh.getId()).isEmpty()) {
            // 没人参加，直接结束
            if (changeStatus(fresh, GroupBuy.ONGOING, GroupBuy.FAILED, null)) {
                notificationService.merchantNotice(fresh.getStoreId(), "凑单结束", "这次凑单没人参加", "pages/merchant/group-buys");
            }
        } else if (changeStatus(fresh, GroupBuy.ONGOING, GroupBuy.WAIT_DECISION,
                LocalDateTime.now().plusHours(DECISION_HOURS))) {
            notificationService.merchantNotice(fresh.getStoreId(), "凑单没凑够",
                    "凑了 " + fresh.getCurrentQty() + "/" + fresh.getTargetQty() + "，请 24 小时内决定发货还是退款",
                    "pages/merchant/group-buys");
        }
    }

    private void succeed(GroupBuy g, String from, String note) {
        if (!changeStatus(g, from, GroupBuy.SUCCESS, null)) return;
        List<Order> orders = waitingOrders(g.getId());
        int done = orderService.forEachSafely(orders, orderService::proceedGroupOrder);
        notificationService.merchantNotice(g.getStoreId(), "凑单成功",
                (note == null ? "" : note + "，") + done + " 单已进入待处理，请接单发货", "pages/merchant/orders");
    }

    private void fail(GroupBuy g, String from, String reason) {
        if (!changeStatus(g, from, GroupBuy.FAILED, null)) return;
        orderService.forEachSafely(waitingOrders(g.getId()), o -> orderService.cancelGroupOrder(o, reason));
    }

    private List<Order> waitingOrders(Long groupBuyId) {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getGroupBuyId, groupBuyId)
                .eq(Order::getStatus, OrderStatus.GROUP_WAITING.name()));
    }

    /** 条件更新状态，防止定时任务和老板同时操作 */
    private boolean changeStatus(GroupBuy g, String from, String to, LocalDateTime deadline) {
        LambdaUpdateWrapper<GroupBuy> w = new LambdaUpdateWrapper<GroupBuy>()
                .eq(GroupBuy::getId, g.getId()).eq(GroupBuy::getStatus, from)
                .set(GroupBuy::getStatus, to)
                .set(GroupBuy::getDecisionDeadline, deadline);
        if (!GroupBuy.WAIT_DECISION.equals(to)) {
            w.set(GroupBuy::getFinishedAt, LocalDateTime.now());
        }
        return groupBuyMapper.update(null, w) > 0;
    }
}
