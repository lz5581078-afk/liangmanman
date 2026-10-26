package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liangmanman.common.BizException;
import com.liangmanman.common.JsonUtil;
import com.liangmanman.common.Mask;
import com.liangmanman.common.PageResult;
import com.liangmanman.dto.ReviewView;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Review;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.User;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.ReviewMapper;
import com.liangmanman.mapper.StoreMapper;
import com.liangmanman.mapper.UserMapper;
import com.liangmanman.order.OrderStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final StoreMapper storeMapper;
    private final UserMapper userMapper;
    private final StoreService storeService;
    private final ContentGuard contentGuard;

    public ReviewService(ReviewMapper reviewMapper, OrderMapper orderMapper, StoreMapper storeMapper,
                         UserMapper userMapper, StoreService storeService, ContentGuard contentGuard) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.storeMapper = storeMapper;
        this.userMapper = userMapper;
        this.storeService = storeService;
        this.contentGuard = contentGuard;
    }

    public record CreateReq(
            @NotNull(message = "请打分") @Min(value = 1, message = "最少 1 星") @Max(value = 5, message = "最多 5 星") Integer score,
            @Size(max = 500, message = "评价太长了") String content,
            @Size(max = 3, message = "最多 3 张照片") List<@Size(max = 500) String> images) {}

    public record ReplyReq(@NotBlank(message = "请填写回复") @Size(max = 500, message = "回复太长了") String reply) {}

    @Transactional
    public ReviewView create(Long userId, Long orderId, CreateReq req) {
        Order o = orderMapper.selectById(orderId);
        if (o == null || !o.getBuyerId().equals(userId)) {
            throw BizException.notFound("订单");
        }
        if (!OrderStatus.COMPLETED.name().equals(o.getStatus())) {
            throw new BizException("订单完成后才能评价");
        }
        contentGuard.check(userId, ContentGuard.SCENE_COMMENT, req.content());
        int marked = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId).eq(Order::getReviewed, false).set(Order::getReviewed, true));
        if (marked == 0) {
            throw new BizException("已经评价过了");
        }
        Review r = new Review();
        r.setOrderId(orderId);
        r.setStoreId(o.getStoreId());
        r.setUserId(userId);
        r.setScore(req.score());
        r.setContent(req.content() == null ? null : req.content().trim());
        r.setImages(req.images() == null || req.images().isEmpty() ? null : JsonUtil.toJson(req.images()));
        reviewMapper.insert(r);
        refreshStoreRating(o.getStoreId());
        return toViews(List.of(r)).get(0);
    }

    public PageResult<ReviewView> ofStore(Long storeId, Integer page, Integer size) {
        Page<Review> p = reviewMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Review>()
                .eq(Review::getStoreId, storeId).orderByDesc(Review::getId));
        return PageResult.of(toViews(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public PageResult<ReviewView> mine(Long userId, Integer page, Integer size) {
        Store store = storeService.requireMyStore(userId);
        return ofStore(store.getId(), page, size);
    }

    @Transactional
    public ReviewView reply(Long userId, Long reviewId, String reply) {
        Store store = storeService.requireMyStore(userId);
        Review r = reviewMapper.selectById(reviewId);
        if (r == null || !r.getStoreId().equals(store.getId())) {
            throw BizException.notFound("评价");
        }
        contentGuard.check(userId, ContentGuard.SCENE_COMMENT, reply);
        r.setReply(reply.trim());
        r.setReplyAt(LocalDateTime.now());
        reviewMapper.updateById(r);
        return toViews(List.of(r)).get(0);
    }

    public long unrepliedCount(Long storeId) {
        return reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getStoreId, storeId).isNull(Review::getReply));
    }

    // ---------- 运营后台 ----------

    public PageResult<ReviewView> adminList(Long storeId, Integer page, Integer size) {
        Page<Review> p = reviewMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Review>()
                .eq(storeId != null, Review::getStoreId, storeId).orderByDesc(Review::getId));
        return PageResult.of(toViews(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Transactional
    public Review adminDelete(Long reviewId) {
        Review r = reviewMapper.selectById(reviewId);
        if (r == null) throw BizException.notFound("评价");
        reviewMapper.deleteById(reviewId);
        refreshStoreRating(r.getStoreId());
        return r;
    }

    /** 店铺评分 = 所有评价平均分（保留 1 位）；没有评价时 5.0 */
    private void refreshStoreRating(Long storeId) {
        List<Map<String, Object>> rows = reviewMapper.selectMaps(new QueryWrapper<Review>()
                .select("COUNT(*) AS c", "COALESCE(AVG(score), 5) AS a").eq("store_id", storeId));
        Map<String, Object> row = rows.get(0);
        int count = ((Number) value(row, "c")).intValue();
        BigDecimal avg = new BigDecimal(value(row, "a").toString()).setScale(1, RoundingMode.HALF_UP);
        storeMapper.update(null, new LambdaUpdateWrapper<Store>()
                .eq(Store::getId, storeId)
                .set(Store::getReviewCount, count)
                .set(Store::getRating, count == 0 ? new BigDecimal("5.0") : avg));
    }

    /** 不同数据库返回的列名大小写不一样 */
    private static Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return 0;
    }

    private List<ReviewView> toViews(List<Review> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(list.stream().map(Review::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Store> stores = storeMapper.selectBatchIds(list.stream().map(Review::getStoreId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        return list.stream().map(r -> new ReviewView(
                r.getId(), r.getOrderId(), r.getStoreId(),
                stores.containsKey(r.getStoreId()) ? stores.get(r.getStoreId()).getName() : null,
                r.getScore(), r.getContent(), JsonUtil.toStringList(r.getImages()),
                Mask.nickname(users.containsKey(r.getUserId()) ? users.get(r.getUserId()).getNickname() : null),
                r.getReply(), r.getCreatedAt(), r.getReplyAt())).toList();
    }
}
