package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liangmanman.common.BizException;
import com.liangmanman.common.GeoUtil;
import com.liangmanman.config.AppProperties;
import com.liangmanman.dto.ProductView;
import com.liangmanman.dto.SearchResult;
import com.liangmanman.dto.SkuView;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.SearchLog;
import com.liangmanman.entity.SearchWord;
import com.liangmanman.entity.StandardProduct;
import com.liangmanman.entity.Store;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.mapper.SearchLogMapper;
import com.liangmanman.mapper.SearchWordMapper;
import com.liangmanman.mapper.StandardProductMapper;
import com.liangmanman.mapper.StoreMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关键词搜索。第一版用数据库 LIKE + 同义词表 + 拼音；商品量上来后换 Elasticsearch，接口保持不变。
 * <ul>
 *   <li>普通搜索：所有匹配的商品平铺出来，可排序、筛选、翻页；</li>
 *   <li>精准找货：搜的是条码 / 登记证号，额外给出这一款在各家店的比价；</li>
 *   <li>同款比价：商品页、列表里的“N 家有同款”进 {@link #compare}。</li>
 * </ul>
 */
@Service
public class SearchService {

    private static final int MAX_CANDIDATES = 500;
    private static final int MAX_TERMS = 15;
    private static final int MAX_PAGE_SIZE = 50;

    private final ProductMapper productMapper;
    private final StandardProductMapper standardProductMapper;
    private final StoreMapper storeMapper;
    private final StoreService storeService;
    private final SkuService skuService;
    private final AppProperties props;
    private final SearchLogMapper searchLogMapper;
    private final SearchWordMapper searchWordMapper;

    public SearchService(ProductMapper productMapper, StandardProductMapper standardProductMapper,
                         StoreMapper storeMapper, StoreService storeService, SkuService skuService, AppProperties props,
                         SearchLogMapper searchLogMapper, SearchWordMapper searchWordMapper) {
        this.productMapper = productMapper;
        this.standardProductMapper = standardProductMapper;
        this.storeMapper = storeMapper;
        this.storeService = storeService;
        this.skuService = skuService;
        this.props = props;
        this.searchLogMapper = searchLogMapper;
        this.searchWordMapper = searchWordMapper;
    }

    /** DEFAULT 综合 / DISTANCE 离我近 / PRICE_ASC 价格从低到高 / PRICE_DESC 从高到低 / SALES 销量 */
    public enum Sort { DEFAULT, DISTANCE, PRICE_ASC, PRICE_DESC, SALES }

    /** 拿货方式筛选：DELIVER 能送货上门 / PICKUP 能到店自提 / EXPRESS 能发快递 */
    public enum Delivery { DELIVER, PICKUP, EXPRESS }

    public record Query(String keyword, Double lat, Double lng, Sort sort, Delivery delivery, Long categoryId,
                        Integer page, Integer size, Long userId) {}

    public SearchResult search(Query query) {
        String q = query.keyword() == null ? "" : query.keyword().trim();
        if (q.isEmpty()) {
            throw new BizException("请输入要找的商品");
        }
        if (q.length() > 32) q = q.substring(0, 32);
        Sort sort = query.sort() == null ? Sort.DEFAULT : query.sort();
        long page = query.page() == null || query.page() < 1 ? 1 : query.page();
        long size = query.size() == null || query.size() < 1 ? 20 : Math.min(query.size(), MAX_PAGE_SIZE);
        Reach reach = reach(query.lat(), query.lng());

        // 1. 精准找货：条码、登记证号
        List<StandardProduct> exactSkus = skuService.exactByCode(q);

        // 2. 关键词 + 同义词 + 命中的款
        List<String> terms = expand(q);
        List<String> tokens = java.util.Arrays.stream(q.toLowerCase().split("\\s+")).filter(t -> !t.isBlank()).limit(6).toList();
        Set<Long> skuIds = new HashSet<>(exactSkus.stream().map(StandardProduct::getId).toList());
        List<StandardProduct> skuHits = standardProductMapper.selectList(new LambdaQueryWrapper<StandardProduct>()
                .select(StandardProduct::getId)
                .and(w -> {
                    boolean first = true;
                    for (String t : terms) {
                        if (!first) w.or();
                        first = false;
                        w.like(StandardProduct::getName, t).or().like(StandardProduct::getBrand, t)
                                .or().like(StandardProduct::getKeywords, t);
                    }
                })
                .last("LIMIT 100"));
        skuHits.forEach(s -> skuIds.add(s.getId()));

        LambdaQueryWrapper<Product> pq = new LambdaQueryWrapper<Product>()
                .eq(Product::getOnShelf, true)
                .eq(query.categoryId() != null, Product::getCategoryId, query.categoryId())
                .and(w -> {
                    boolean first = true;
                    for (String t : terms) {
                        if (!first) w.or();
                        first = false;
                        w.like(Product::getName, t).or().like(Product::getBrand, t).or().like(Product::getSearchText, t);
                    }
                    if (!skuIds.isEmpty()) w.or().in(Product::getStandardProductId, skuIds);
                    // 多个词（如“演示牌 复合肥 50kg”）：每个词都出现在名称、品牌、规格或搜索文字里也算
                    if (tokens.size() > 1) {
                        w.or(x -> tokens.forEach(tok -> x.apply(
                                "LOWER(CONCAT_WS(' ', name, brand, spec, search_text)) LIKE {0}", "%" + tok + "%")));
                    }
                });
        restrictToReach(pq, reach);
        pq.last("LIMIT " + MAX_CANDIDATES);
        List<Product> products = productMapper.selectList(pq);
        Map<Long, Store> stores = storesFor(products, reach);

        // 3. 组装、筛选、排序、翻页
        String ql = q.toLowerCase();
        List<Scored> items = new ArrayList<>();
        for (Product p : products) {
            SearchResult.Offer o = offer(p, stores.get(p.getStoreId()), reach);
            if (o == null || !matches(o, query.delivery())) continue;
            items.add(new Scored(p, o, relevance(p, ql, tokens, terms, skuIds)));
        }
        items.sort(order(sort));
        long total = items.size();
        List<Scored> pageItems = items.stream().skip((page - 1) * size).limit(size).toList();

        Map<Long, Integer> sameCount = new HashMap<>();
        Set<Long> pageSkus = pageItems.stream().map(x -> x.p.getStandardProductId()).filter(x -> x != null).collect(Collectors.toSet());
        offersBySku(pageSkus, reach).forEach((id, offers) ->
                sameCount.put(id, (int) offers.stream().map(SearchResult.Offer::storeId).distinct().count()));

        List<SearchResult.Item> records = pageItems.stream().map(x -> {
            SearchResult.Offer o = x.o;
            Long skuId = x.p.getStandardProductId();
            return new SearchResult.Item(o.productId(), o.productName(), x.p.getBrand(), x.p.getSpec(), o.image(), o.price(),
                    o.unit(), x.p.getSales(), o.storeId(), o.storeName(), o.storeOpen(), o.distanceKm(), o.canDeliver(),
                    o.canPickup(), o.express(), o.expressFee(), o.deliveryFee(), skuId,
                    skuId == null ? 1 : sameCount.getOrDefault(skuId, 1));
        }).toList();

        List<SearchResult.SkuOffers> exact = new ArrayList<>();
        if (!exactSkus.isEmpty()) {
            Map<Long, List<SearchResult.Offer>> bySku = offersBySku(exactSkus.stream().map(StandardProduct::getId).toList(), reach);
            for (StandardProduct s : exactSkus) {
                exact.add(skuOffers(s, bySku.getOrDefault(s.getId(), List.of()), Sort.DEFAULT));
            }
        }
        if (page == 1) logSearch(q, total, query.userId(), query.lat(), query.lng());
        return new SearchResult(q, total, page, size, records, exact);
    }

    /** 同款比价：这一款在附近（以及能发快递的）各家店的报价 */
    public SearchResult.SkuOffers compare(Long skuId, Double lat, Double lng, Sort sort) {
        StandardProduct s = skuService.require(skuId);
        Reach reach = reach(lat, lng);
        List<SearchResult.Offer> offers = offersBySku(List.of(skuId), reach).getOrDefault(skuId, List.of());
        return skuOffers(s, offers, sort == null ? Sort.DEFAULT : sort);
    }

    // ---------------------------------------------------------------------

    /** 买家位置与可达的店：有位置时，范围内的店 + 范围外能发快递的商品 */
    private record Reach(Double lat, Double lng, Map<Long, Store> nearby) {
        boolean located() {
            return lat != null && lng != null;
        }
    }

    private record Scored(Product p, SearchResult.Offer o, int relevance) {}

    private Reach reach(Double lat, Double lng) {
        Map<Long, Store> nearby = new HashMap<>();
        if (lat != null && lng != null) {
            for (Store s : storeService.approvedWithin(lat, lng, props.search().defaultRadiusKm())) {
                nearby.put(s.getId(), s);
            }
        }
        return new Reach(lat, lng, nearby);
    }

    private static void restrictToReach(LambdaQueryWrapper<Product> pq, Reach reach) {
        if (!reach.located()) return;
        pq.and(w -> {
            w.eq(Product::getExpressDelivery, true);
            if (!reach.nearby().isEmpty()) w.or().in(Product::getStoreId, reach.nearby().keySet());
        });
    }

    /** 补齐范围外（快递）商品的店铺，只保留审核通过的 */
    private Map<Long, Store> storesFor(List<Product> products, Reach reach) {
        Map<Long, Store> all = new HashMap<>(reach.nearby());
        Set<Long> missing = products.stream().map(Product::getStoreId).filter(id -> !all.containsKey(id)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            storeMapper.selectList(new LambdaQueryWrapper<Store>().in(Store::getId, missing).eq(Store::getStatus, Store.APPROVED))
                    .forEach(s -> all.put(s.getId(), s));
        }
        return all;
    }

    /** 一条报价：店铺不可用、或只支持本地配送又太远时返回 null */
    private SearchResult.Offer offer(Product p, Store s, Reach reach) {
        if (s == null) return null;
        boolean local = Boolean.TRUE.equals(p.getLocalDelivery());
        boolean express = Boolean.TRUE.equals(p.getExpressDelivery());
        boolean nearby = reach.nearby().containsKey(s.getId());
        Double distance = reach.located()
                ? GeoUtil.round1(GeoUtil.distanceKm(reach.lat(), reach.lng(), s.getLat().doubleValue(), s.getLng().doubleValue()))
                : null;
        if (reach.located() && !express && !nearby) return null;
        boolean canDeliver = local && Boolean.TRUE.equals(s.getDeliveryEnabled())
                && distance != null && distance <= s.getDeliveryRadiusKm().doubleValue();
        boolean canPickup = local && Boolean.TRUE.equals(s.getPickupEnabled()) && (!reach.located() || nearby);
        return new SearchResult.Offer(p.getId(), p.getName(), p.getPrice(), p.getUnit(), ProductView.of(p).firstImage(),
                s.getId(), s.getName(), Boolean.TRUE.equals(s.getIsOpen()), distance,
                canDeliver, canPickup, express, p.getExpressFee(), s.getDeliveryFee());
    }

    private static boolean matches(SearchResult.Offer o, Delivery d) {
        if (d == null) return true;
        return switch (d) {
            case DELIVER -> o.canDeliver();
            case PICKUP -> o.canPickup();
            case EXPRESS -> o.express();
        };
    }

    /** 各款在可达范围内的报价 */
    private Map<Long, List<SearchResult.Offer>> offersBySku(Collection<Long> skuIds, Reach reach) {
        if (skuIds.isEmpty()) return Map.of();
        LambdaQueryWrapper<Product> pq = new LambdaQueryWrapper<Product>()
                .eq(Product::getOnShelf, true)
                .in(Product::getStandardProductId, skuIds);
        restrictToReach(pq, reach);
        pq.last("LIMIT " + MAX_CANDIDATES);
        List<Product> products = productMapper.selectList(pq);
        Map<Long, Store> stores = storesFor(products, reach);
        Map<Long, List<SearchResult.Offer>> out = new HashMap<>();
        for (Product p : products) {
            SearchResult.Offer o = offer(p, stores.get(p.getStoreId()), reach);
            if (o != null) out.computeIfAbsent(p.getStandardProductId(), k -> new ArrayList<>()).add(o);
        }
        return out;
    }

    private static SearchResult.SkuOffers skuOffers(StandardProduct s, List<SearchResult.Offer> offers, Sort sort) {
        Comparator<SearchResult.Offer> c = switch (sort) {
            case PRICE_ASC, DEFAULT, SALES -> Comparator.comparing(SearchResult.Offer::price);
            case PRICE_DESC -> Comparator.comparing(SearchResult.Offer::price).reversed();
            case DISTANCE -> Comparator.comparing(SearchResult.Offer::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        List<SearchResult.Offer> sorted = offers.stream()
                .sorted(Comparator.comparing((SearchResult.Offer o) -> !o.storeOpen()).thenComparing(c)).toList();
        BigDecimal min = offers.stream().map(SearchResult.Offer::price).min(BigDecimal::compareTo).orElse(null);
        return new SearchResult.SkuOffers(SkuView.of(s), min, sorted);
    }

    /** 相关度：名称里有原词最相关，其次同义词、品牌、所属款、拼音 */
    private static int relevance(Product p, String q, List<String> tokens, List<String> terms, Set<Long> skuIds) {
        String name = p.getName() == null ? "" : p.getName().toLowerCase();
        if (name.contains(q)) return 100;
        if (tokens.size() > 1) {
            String all = (name + " " + (p.getBrand() == null ? "" : p.getBrand()) + " " + (p.getSpec() == null ? "" : p.getSpec())).toLowerCase();
            if (tokens.stream().allMatch(all::contains)) return 90;
        }
        if (p.getBrand() != null && p.getBrand().toLowerCase().contains(q)) return 70;
        for (String t : terms) {
            if (name.contains(t)) return 60;
        }
        if (p.getStandardProductId() != null && skuIds.contains(p.getStandardProductId())) return 50;
        return 20;
    }

    private static Comparator<Scored> order(Sort sort) {
        Comparator<Scored> byDistance = Comparator.comparing((Scored x) -> x.o.distanceKm(), Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Scored> byPrice = Comparator.comparing((Scored x) -> x.o.price());
        Comparator<Scored> openFirst = Comparator.comparing((Scored x) -> !x.o.storeOpen());
        return switch (sort) {
            // 综合：营业中优先 → 相关度 → 能送到的优先 → 距离
            case DEFAULT -> openFirst
                    .thenComparing(Comparator.comparingInt((Scored x) -> x.relevance).reversed())
                    .thenComparing((Scored x) -> !(x.o.canDeliver() || x.o.canPickup()))
                    .thenComparing(byDistance);
            case DISTANCE -> byDistance.thenComparing(byPrice);
            case PRICE_ASC -> byPrice.thenComparing(byDistance);
            case PRICE_DESC -> byPrice.reversed().thenComparing(byDistance);
            case SALES -> Comparator.comparing((Scored x) -> x.p.getSales() == null ? 0 : x.p.getSales()).reversed()
                    .thenComparing(byDistance);
        };
    }

    /** 关键词 + 同义词：搜“打虫药”也会去找“吡虫啉”“杀虫剂” */
    List<String> expand(String q) {
        String ql = q.toLowerCase();
        Set<String> terms = new LinkedHashSet<>();
        terms.add(ql);
        for (SearchWord w : searchWordMapper.selectList(null)) {
            List<String> group = w.terms();
            boolean hit = group.stream().anyMatch(t -> t.equals(ql)
                    || (t.length() >= 2 && ql.contains(t))
                    || (ql.length() >= 2 && t.contains(ql)));
            if (hit) terms.addAll(group);
            if (terms.size() >= MAX_TERMS) break;
        }
        return terms.stream().limit(MAX_TERMS).toList();
    }

    /** 记录搜索词：运营看板里的“搜不到的词”用来补品库、找商家 */
    private void logSearch(String keyword, long count, Long userId, Double lat, Double lng) {
        try {
            SearchLog l = new SearchLog();
            l.setKeyword(keyword);
            l.setResultCount((int) Math.min(count, Integer.MAX_VALUE));
            l.setUserId(userId);
            if (lat != null && lng != null) {
                l.setLat(BigDecimal.valueOf(lat));
                l.setLng(BigDecimal.valueOf(lng));
            }
            searchLogMapper.insert(l);
        } catch (Exception e) {
            // 日志失败不影响搜索
        }
    }
}
