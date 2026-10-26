package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liangmanman.common.BizException;
import com.liangmanman.common.GeoUtil;
import com.liangmanman.config.AppProperties;
import com.liangmanman.dto.GroupBuyView;
import com.liangmanman.dto.ProductView;
import com.liangmanman.entity.GroupBuy;
import com.liangmanman.entity.Region;
import com.liangmanman.mapper.GroupBuyMapper;
import com.liangmanman.mapper.RegionMapper;
import java.time.LocalDateTime;
import com.liangmanman.dto.StoreBrief;
import com.liangmanman.dto.StoreDetail;
import com.liangmanman.dto.StoreReqs;
import com.liangmanman.entity.Category;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.Store;
import com.liangmanman.mapper.CategoryMapper;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.mapper.StoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StoreService {

    private static final int NEARBY_MAX = 100;

    private final StoreMapper storeMapper;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final AppProperties props;
    private final GroupBuyMapper groupBuyMapper;
    private final GroupBuyViewer groupBuyViewer;
    private final RegionMapper regionMapper;
    private final ContentGuard contentGuard;

    public StoreService(StoreMapper storeMapper, ProductMapper productMapper,
                        CategoryMapper categoryMapper, AppProperties props, GroupBuyMapper groupBuyMapper,
                        GroupBuyViewer groupBuyViewer, RegionMapper regionMapper, ContentGuard contentGuard) {
        this.groupBuyMapper = groupBuyMapper;
        this.groupBuyViewer = groupBuyViewer;
        this.regionMapper = regionMapper;
        this.contentGuard = contentGuard;
        this.storeMapper = storeMapper;
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.props = props;
    }

    // ---------- 商家端 ----------

    @Transactional
    public Store open(Long userId, StoreReqs.Open req) {
        if (findByOwner(userId) != null) {
            throw new BizException(BizException.CONFLICT, "你已经开过店了");
        }
        checkRegion(req.lat(), req.lng());
        contentGuard.check(userId, ContentGuard.SCENE_PROFILE, req.name());
        Store s = new Store();
        s.setOwnerId(userId);
        s.setName(req.name().trim());
        s.setPhone(req.phone());
        s.setCoverImage(req.coverImage());
        s.setLat(req.lat());
        s.setLng(req.lng());
        s.setAddressText(req.addressText());
        s.setTown(req.town());
        s.setMainCategories(req.mainCategories());
        s.setIsOpen(true);
        s.setDeliveryEnabled(true);
        s.setPickupEnabled(true);
        s.setDeliveryRadiusKm(new BigDecimal("10.0"));
        s.setMinOrderAmount(BigDecimal.ZERO);
        s.setDeliveryFee(BigDecimal.ZERO);
        s.setCodEnabled(true);
        s.setRating(new BigDecimal("5.0"));
        s.setReviewCount(0);
        s.setStatus(props.store().autoApprove() ? Store.APPROVED : Store.PENDING);
        storeMapper.insert(s);
        return s;
    }

    public Store findByOwner(Long userId) {
        return storeMapper.selectOne(new LambdaQueryWrapper<Store>().eq(Store::getOwnerId, userId));
    }

    /** 商家操作前调用：必须有店且未被封 */
    public Store requireMyStore(Long userId) {
        Store s = findByOwner(userId);
        if (s == null) {
            throw new BizException(BizException.NOT_FOUND, "你还没有开店");
        }
        if (Store.BANNED.equals(s.getStatus())) {
            throw new BizException(BizException.FORBIDDEN, "店铺已被停用，请联系平台");
        }
        return s;
    }

    @Transactional
    public Store update(Long userId, StoreReqs.Update req) {
        Store s = requireMyStore(userId);
        contentGuard.check(userId, ContentGuard.SCENE_PROFILE, req.name(), req.notice());
        if (req.lat() != null && req.lng() != null
                && (req.lat().compareTo(s.getLat()) != 0 || req.lng().compareTo(s.getLng()) != 0)) {
            checkRegion(req.lat(), req.lng());
        }
        if (req.name() != null && !req.name().isBlank()) s.setName(req.name().trim());
        if (req.phone() != null) s.setPhone(req.phone());
        if (req.coverImage() != null) s.setCoverImage(req.coverImage());
        if (req.lat() != null && req.lng() != null) {
            s.setLat(req.lat());
            s.setLng(req.lng());
        }
        if (req.addressText() != null) s.setAddressText(req.addressText());
        if (req.town() != null) s.setTown(req.town());
        if (req.notice() != null) s.setNotice(req.notice());
        if (req.businessHours() != null) s.setBusinessHours(req.businessHours());
        if (req.isOpen() != null) s.setIsOpen(req.isOpen());
        if (req.deliveryEnabled() != null) s.setDeliveryEnabled(req.deliveryEnabled());
        if (req.pickupEnabled() != null) s.setPickupEnabled(req.pickupEnabled());
        if (req.deliveryRadiusKm() != null) s.setDeliveryRadiusKm(req.deliveryRadiusKm());
        if (req.minOrderAmount() != null) s.setMinOrderAmount(req.minOrderAmount());
        if (req.deliveryFee() != null) s.setDeliveryFee(req.deliveryFee());
        if (req.codEnabled() != null) s.setCodEnabled(req.codEnabled());
        if (req.mainCategories() != null) s.setMainCategories(req.mainCategories());
        storeMapper.updateById(s);
        return s;
    }

    // ---------- 买家端 ----------

    /**
     * 附近的店：先用经纬度范围粗筛，再算精确距离。营业中的排前面，然后按距离。
     * 店铺数量上来后（几万家）换成 Redis GEO，接口不变。
     */
    public List<StoreBrief> nearby(double lat, double lng, Double radiusKm, Long categoryId) {
        double radius = radiusKm != null && radiusKm > 0 ? Math.min(radiusKm, 100) : props.search().defaultRadiusKm();
        List<Store> candidates = approvedWithin(lat, lng, radius);
        if (categoryId != null && !candidates.isEmpty()) {
            Set<Long> withCategory = productMapper.selectList(new LambdaQueryWrapper<Product>()
                            .select(Product::getStoreId)
                            .in(Product::getStoreId, candidates.stream().map(Store::getId).toList())
                            .eq(Product::getCategoryId, categoryId)
                            .eq(Product::getOnShelf, true))
                    .stream().map(Product::getStoreId).collect(Collectors.toSet());
            candidates = candidates.stream().filter(s -> withCategory.contains(s.getId())).toList();
        }
        return candidates.stream()
                .map(s -> StoreBrief.of(s, GeoUtil.round1(GeoUtil.distanceKm(lat, lng, s.getLat().doubleValue(), s.getLng().doubleValue()))))
                .filter(b -> b.distanceKm() <= radius)
                .sorted(Comparator.comparing((StoreBrief b) -> !b.isOpen()).thenComparing(StoreBrief::distanceKm))
                .limit(NEARBY_MAX)
                .toList();
    }

    /** 半径内、审核通过的店（未算精确距离） */
    public List<Store> approvedWithin(double lat, double lng, double radiusKm) {
        double[] box = GeoUtil.boundingBox(lat, lng, radiusKm);
        return storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .eq(Store::getStatus, Store.APPROVED)
                .between(Store::getLat, box[0], box[1])
                .between(Store::getLng, box[2], box[3]));
    }

    public StoreDetail detail(Long storeId, Double lat, Double lng) {
        Store s = storeMapper.selectById(storeId);
        if (s == null || !Store.APPROVED.equals(s.getStatus())) {
            throw BizException.notFound("店铺");
        }
        Double distance = (lat != null && lng != null)
                ? GeoUtil.round1(GeoUtil.distanceKm(lat, lng, s.getLat().doubleValue(), s.getLng().doubleValue()))
                : null;
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStoreId, storeId)
                .eq(Product::getOnShelf, true)
                .orderByDesc(Product::getSales)
                .orderByDesc(Product::getId));
        List<GroupBuyView> groupBuys = groupBuyViewer.views(groupBuyMapper.selectList(new LambdaQueryWrapper<GroupBuy>()
                .eq(GroupBuy::getStoreId, storeId)
                .eq(GroupBuy::getStatus, GroupBuy.ONGOING)
                .gt(GroupBuy::getEndAt, LocalDateTime.now())
                .orderByAsc(GroupBuy::getEndAt)), false, null, lat, lng);
        StoreDetail.ReviewSummary reviews = new StoreDetail.ReviewSummary(
                s.getReviewCount() == null ? 0 : s.getReviewCount(), s.getRating());
        return new StoreDetail(StoreBrief.of(s, distance), s.getNotice(), s.getBusinessHours(),
                groupBuys, reviews, groupByCategory(products));
    }

    /** 有启用的开通区域时，店铺位置必须在某个区域内 */
    void checkRegion(BigDecimal lat, BigDecimal lng) {
        List<Region> regions = regionMapper.selectList(new LambdaQueryWrapper<Region>().eq(Region::getEnabled, true));
        if (regions.isEmpty()) return;
        boolean inside = regions.stream().anyMatch(r ->
                GeoUtil.distanceKm(lat, lng, r.getLat(), r.getLng()) <= r.getRadiusKm().doubleValue());
        if (!inside) {
            throw new BizException("你所在的地区还没开通，开通后会第一时间通知你");
        }
    }

    /** 按分类排序分组；没有分类的放到“其他” */
    List<StoreDetail.CategoryGroup> groupByCategory(List<Product> products) {
        Map<Long, Category> categories = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, List<ProductView>> grouped = new LinkedHashMap<>();
        for (Product p : products) {
            Long cid = p.getCategoryId() != null && categories.containsKey(p.getCategoryId()) ? p.getCategoryId() : null;
            grouped.computeIfAbsent(cid, k -> new ArrayList<>()).add(ProductView.of(p));
        }
        return grouped.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey() == null ? Integer.MAX_VALUE
                        : Objects.requireNonNullElse(categories.get(e.getKey()).getSort(), 0)))
                .map(e -> new StoreDetail.CategoryGroup(
                        e.getKey(),
                        e.getKey() == null ? "其他" : categories.get(e.getKey()).getName(),
                        e.getValue()))
                .toList();
    }
}
