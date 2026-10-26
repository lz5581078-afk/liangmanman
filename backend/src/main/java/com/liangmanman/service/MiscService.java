package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liangmanman.common.BizException;
import com.liangmanman.common.GeoUtil;
import com.liangmanman.entity.Banner;
import com.liangmanman.entity.GroupBuy;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.Region;
import com.liangmanman.entity.ShareLog;
import com.liangmanman.entity.Store;
import com.liangmanman.integration.WxApi;
import com.liangmanman.mapper.BannerMapper;
import com.liangmanman.mapper.GroupBuyMapper;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.mapper.RegionMapper;
import com.liangmanman.mapper.ShareLogMapper;
import com.liangmanman.mapper.StoreMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 轮播图、小程序码、分享记录 */
@Service
public class MiscService {

    private static final int QR_CACHE_MAX = 2000;

    private final BannerMapper bannerMapper;
    private final RegionMapper regionMapper;
    private final ShareLogMapper shareLogMapper;
    private final StoreMapper storeMapper;
    private final ProductMapper productMapper;
    private final GroupBuyMapper groupBuyMapper;
    private final WxApi wx;
    /** 小程序码生成后不会变，缓存起来少调微信接口 */
    private final Map<String, byte[]> qrCache = new ConcurrentHashMap<>();

    public MiscService(BannerMapper bannerMapper, RegionMapper regionMapper, ShareLogMapper shareLogMapper,
                       StoreMapper storeMapper, ProductMapper productMapper, GroupBuyMapper groupBuyMapper, WxApi wx) {
        this.bannerMapper = bannerMapper;
        this.regionMapper = regionMapper;
        this.shareLogMapper = shareLogMapper;
        this.storeMapper = storeMapper;
        this.productMapper = productMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.wx = wx;
    }

    public record BannerView(Long id, String image, String title, String link) {}

    /** 启用的轮播图：不限区域的 + 买家位置所在区域的 */
    public List<BannerView> banners(Double lat, Double lng) {
        List<Banner> list = bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .eq(Banner::getEnabled, true).orderByAsc(Banner::getSort).orderByDesc(Banner::getId));
        Map<Long, Region> regions = regionMapper.selectList(null).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
        return list.stream()
                .filter(b -> {
                    if (b.getRegionId() == null) return true;
                    Region r = regions.get(b.getRegionId());
                    if (r == null || !Boolean.TRUE.equals(r.getEnabled()) || lat == null || lng == null) return false;
                    return GeoUtil.distanceKm(lat, lng, r.getLat().doubleValue(), r.getLng().doubleValue()) <= r.getRadiusKm().doubleValue();
                })
                .map(b -> new BannerView(b.getId(), b.getImage(), b.getTitle(), b.getLink()))
                .toList();
    }

    /** 店铺 / 商品 / 凑单的小程序码（PNG） */
    public byte[] qrCode(String type, Long id) {
        String scene;
        String page;
        switch (type == null ? "" : type) {
            case "store" -> {
                Store s = storeMapper.selectById(id);
                if (s == null || !Store.APPROVED.equals(s.getStatus())) throw BizException.notFound("店铺");
                scene = "s=" + id;
                page = "pages/store/store";
            }
            case "product" -> {
                Product p = productMapper.selectById(id);
                if (p == null) throw BizException.notFound("商品");
                scene = "p=" + id;
                page = "pages/product/product";
            }
            case "group" -> {
                GroupBuy g = groupBuyMapper.selectById(id);
                if (g == null) throw BizException.notFound("凑单");
                scene = "g=" + id;
                page = "pages/group/detail";
            }
            default -> throw new BizException("类型不对");
        }
        String key = scene + "@" + page;
        byte[] cached = qrCache.get(key);
        if (cached != null) return cached;
        byte[] img = wx.qrCode(scene, page);
        if (qrCache.size() < QR_CACHE_MAX) qrCache.put(key, img);
        return img;
    }

    public void shareVisit(Long visitorId, Long sharerId, String targetType, Long targetId) {
        if (sharerId == null || sharerId.equals(visitorId)) return;
        if (!List.of("STORE", "PRODUCT", "GROUP_BUY").contains(targetType)) return;
        ShareLog l = new ShareLog();
        l.setSharerId(sharerId);
        l.setVisitorId(visitorId);
        l.setTargetType(targetType);
        l.setTargetId(targetId);
        shareLogMapper.insert(l);
    }
}
