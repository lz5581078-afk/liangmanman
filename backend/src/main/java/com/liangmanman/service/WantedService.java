package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liangmanman.common.BizException;
import com.liangmanman.common.GeoUtil;
import com.liangmanman.common.PageResult;
import com.liangmanman.dto.WantedView;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.Wanted;
import com.liangmanman.entity.WantedReply;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.mapper.StoreMapper;
import com.liangmanman.mapper.WantedMapper;
import com.liangmanman.mapper.WantedReplyMapper;
import com.liangmanman.notify.NotificationService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 求购：农户搜不到时发出，附近的店能看到并回复“我有货” */
@Service
public class WantedService {

    private static final int EXPIRE_DAYS = 3;
    private static final int MAX_OPEN_PER_USER = 10;
    private static final double MIN_RADIUS_KM = 30;

    private final WantedMapper wantedMapper;
    private final WantedReplyMapper replyMapper;
    private final StoreMapper storeMapper;
    private final ProductMapper productMapper;
    private final StoreService storeService;
    private final ContentGuard contentGuard;
    private final NotificationService notificationService;

    public WantedService(WantedMapper wantedMapper, WantedReplyMapper replyMapper, StoreMapper storeMapper,
                         ProductMapper productMapper, StoreService storeService, ContentGuard contentGuard,
                         NotificationService notificationService) {
        this.wantedMapper = wantedMapper;
        this.replyMapper = replyMapper;
        this.storeMapper = storeMapper;
        this.productMapper = productMapper;
        this.storeService = storeService;
        this.contentGuard = contentGuard;
        this.notificationService = notificationService;
    }

    public record CreateReq(
            @NotBlank(message = "请写要买什么") @Size(max = 64, message = "写短一点") String keyword,
            @Size(max = 200, message = "备注太长了") String note,
            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不对") String contactPhone,
            @NotNull(message = "需要你的位置") @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @NotNull(message = "需要你的位置") @DecimalMin("-180") @DecimalMax("180") BigDecimal lng) {}

    public record ReplyReq(
            @DecimalMin(value = "0", message = "价格不能小于 0") BigDecimal price,
            @NotBlank(message = "说一下有没有货、怎么卖") @Size(max = 200, message = "写短一点") String note,
            Long productId) {}

    // ---------- 买家 ----------

    @Transactional
    public WantedView create(Long userId, CreateReq req) {
        contentGuard.check(userId, ContentGuard.SCENE_COMMENT, req.keyword(), req.note());
        long open = wantedMapper.selectCount(new LambdaQueryWrapper<Wanted>()
                .eq(Wanted::getUserId, userId).eq(Wanted::getStatus, Wanted.OPEN)
                .gt(Wanted::getExpireAt, LocalDateTime.now()));
        if (open >= MAX_OPEN_PER_USER) {
            throw new BizException("同时最多发 " + MAX_OPEN_PER_USER + " 条求购，先关掉一些吧");
        }
        Wanted w = new Wanted();
        w.setUserId(userId);
        w.setKeyword(req.keyword().trim());
        w.setNote(req.note());
        w.setContactPhone(req.contactPhone());
        w.setLat(req.lat());
        w.setLng(req.lng());
        w.setStatus(Wanted.OPEN);
        w.setExpireAt(LocalDateTime.now().plusDays(EXPIRE_DAYS));
        wantedMapper.insert(w);
        return view(w, null, List.of());
    }

    public List<WantedView> mine(Long userId) {
        List<Wanted> list = wantedMapper.selectList(new LambdaQueryWrapper<Wanted>()
                .eq(Wanted::getUserId, userId).orderByDesc(Wanted::getId).last("LIMIT 50"));
        return withReplies(list, null, null);
    }

    public void close(Long userId, Long id) {
        Wanted w = wantedMapper.selectById(id);
        if (w == null || !w.getUserId().equals(userId)) {
            throw BizException.notFound("求购");
        }
        w.setStatus(Wanted.CLOSED);
        wantedMapper.updateById(w);
    }

    // ---------- 商家 ----------

    /** 附近（送货范围与 30 公里取大）还没过期的求购；回复只显示本店的 */
    public List<WantedView> nearbyForMerchant(Long userId) {
        Store store = storeService.requireMyStore(userId);
        double radius = Math.max(MIN_RADIUS_KM, store.getDeliveryRadiusKm().doubleValue());
        double lat = store.getLat().doubleValue();
        double lng = store.getLng().doubleValue();
        double[] box = GeoUtil.boundingBox(lat, lng, radius);
        List<Wanted> list = wantedMapper.selectList(new LambdaQueryWrapper<Wanted>()
                        .eq(Wanted::getStatus, Wanted.OPEN)
                        .gt(Wanted::getExpireAt, LocalDateTime.now())
                        .ne(Wanted::getUserId, store.getOwnerId())
                        .between(Wanted::getLat, box[0], box[1])
                        .between(Wanted::getLng, box[2], box[3])
                        .orderByDesc(Wanted::getId)
                        .last("LIMIT 200"))
                .stream()
                .filter(w -> GeoUtil.distanceKm(lat, lng, w.getLat().doubleValue(), w.getLng().doubleValue()) <= radius)
                .toList();
        return withReplies(list, store, new double[]{lat, lng});
    }

    public long nearbyCount(Long userId) {
        return nearbyForMerchant(userId).size();
    }

    @Transactional
    public WantedView reply(Long userId, Long wantedId, ReplyReq req) {
        Store store = storeService.requireMyStore(userId);
        Wanted w = wantedMapper.selectById(wantedId);
        if (w == null || !Wanted.OPEN.equals(w.getStatus()) || w.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException("这条求购已经结束了");
        }
        if (req.productId() != null) {
            Product p = productMapper.selectById(req.productId());
            if (p == null || !p.getStoreId().equals(store.getId())) {
                throw BizException.notFound("商品");
            }
        }
        contentGuard.check(userId, ContentGuard.SCENE_COMMENT, req.note());
        WantedReply r = replyMapper.selectOne(new LambdaQueryWrapper<WantedReply>()
                .eq(WantedReply::getWantedId, wantedId).eq(WantedReply::getStoreId, store.getId()));
        boolean isNew = r == null;
        if (isNew) {
            r = new WantedReply();
            r.setWantedId(wantedId);
            r.setStoreId(store.getId());
        }
        r.setPrice(req.price());
        r.setNote(req.note().trim());
        r.setProductId(req.productId());
        if (isNew) replyMapper.insert(r);
        else replyMapper.updateById(r);
        notificationService.buyerNotice(w.getUserId(), "求购有回复",
                store.getName() + "：" + req.note(), "pages/wanted/mine");
        return withReplies(List.of(w), store, new double[]{store.getLat().doubleValue(), store.getLng().doubleValue()}).get(0);
    }

    // ---------- 运营后台 ----------

    public PageResult<WantedView> adminList(String status, Integer page, Integer size) {
        LambdaQueryWrapper<Wanted> q = new LambdaQueryWrapper<Wanted>().orderByDesc(Wanted::getId);
        if ("EXPIRED".equals(status)) {
            q.eq(Wanted::getStatus, Wanted.OPEN).le(Wanted::getExpireAt, LocalDateTime.now());
        } else if ("OPEN".equals(status)) {
            q.eq(Wanted::getStatus, Wanted.OPEN).gt(Wanted::getExpireAt, LocalDateTime.now());
        } else if ("CLOSED".equals(status)) {
            q.eq(Wanted::getStatus, Wanted.CLOSED);
        }
        Page<Wanted> p = wantedMapper.selectPage(PageResult.page(page, size), q);
        return PageResult.of(withReplies(p.getRecords(), null, null), p.getTotal(), p.getCurrent(), p.getSize());
    }

    // ---------- 组装 ----------

    private List<WantedView> withReplies(List<Wanted> list, Store onlyStore, double[] from) {
        if (list.isEmpty()) return List.of();
        LambdaQueryWrapper<WantedReply> rq = new LambdaQueryWrapper<WantedReply>()
                .in(WantedReply::getWantedId, list.stream().map(Wanted::getId).toList())
                .orderByAsc(WantedReply::getId);
        if (onlyStore != null) rq.eq(WantedReply::getStoreId, onlyStore.getId());
        List<WantedReply> replies = replyMapper.selectList(rq);
        Map<Long, Store> stores = replies.isEmpty() ? Map.of() : storeMapper.selectBatchIds(
                        replies.stream().map(WantedReply::getStoreId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        Map<Long, List<WantedView.Reply>> byWanted = replies.stream().collect(Collectors.groupingBy(
                WantedReply::getWantedId,
                Collectors.mapping(r -> new WantedView.Reply(r.getId(), r.getStoreId(),
                        stores.containsKey(r.getStoreId()) ? stores.get(r.getStoreId()).getName() : null,
                        stores.containsKey(r.getStoreId()) ? stores.get(r.getStoreId()).getPhone() : null,
                        r.getPrice(), r.getNote(), r.getProductId(), r.getCreatedAt()), Collectors.toList())));
        return list.stream().map(w -> view(w, from, byWanted.getOrDefault(w.getId(), List.of()))).toList();
    }

    private WantedView view(Wanted w, double[] from, List<WantedView.Reply> replies) {
        String status = Wanted.OPEN.equals(w.getStatus()) && w.getExpireAt().isBefore(LocalDateTime.now())
                ? "EXPIRED" : w.getStatus();
        Double distance = from == null ? null
                : GeoUtil.round1(GeoUtil.distanceKm(from[0], from[1], w.getLat().doubleValue(), w.getLng().doubleValue()));
        return new WantedView(w.getId(), w.getKeyword(), w.getNote(), w.getContactPhone(), status,
                w.getCreatedAt(), w.getExpireAt(), distance, replies);
    }
}
