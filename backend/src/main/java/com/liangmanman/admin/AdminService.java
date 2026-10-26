package com.liangmanman.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liangmanman.common.BizException;
import com.liangmanman.common.PageResult;
import com.liangmanman.dto.GroupBuyView;
import com.liangmanman.dto.ProductView;
import com.liangmanman.entity.Admin;
import com.liangmanman.entity.Banner;
import com.liangmanman.entity.Category;
import com.liangmanman.entity.GroupBuy;
import com.liangmanman.entity.MerchantAccount;
import com.liangmanman.entity.NotifyLog;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.Region;
import com.liangmanman.entity.SearchLog;
import com.liangmanman.entity.StandardProduct;
import com.liangmanman.entity.SearchWord;
import com.liangmanman.mapper.SearchWordMapper;
import com.liangmanman.service.SkuService;
import com.liangmanman.dto.SkuView;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.StoreLicense;
import com.liangmanman.entity.User;
import com.liangmanman.mapper.BannerMapper;
import com.liangmanman.mapper.CategoryMapper;
import com.liangmanman.mapper.GroupBuyMapper;
import com.liangmanman.mapper.MerchantAccountMapper;
import com.liangmanman.mapper.NotifyLogMapper;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.mapper.RegionMapper;
import com.liangmanman.mapper.SearchLogMapper;
import com.liangmanman.mapper.StandardProductMapper;
import com.liangmanman.mapper.StoreLicenseMapper;
import com.liangmanman.mapper.StoreMapper;
import com.liangmanman.mapper.UserMapper;
import com.liangmanman.notify.NotificationService;
import com.liangmanman.order.OrderStatus;
import com.liangmanman.service.GroupBuyViewer;
import com.liangmanman.service.MerchantToolsService;
import com.liangmanman.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 运营后台：看板、审核、商品与品库、区域、轮播、用户、通知记录 */
@Service
public class AdminService {

    private final StoreMapper storeMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final StandardProductMapper standardMapper;
    private final CategoryMapper categoryMapper;
    private final StoreLicenseMapper licenseMapper;
    private final MerchantAccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final GroupBuyMapper groupBuyMapper;
    private final SearchLogMapper searchLogMapper;
    private final RegionMapper regionMapper;
    private final BannerMapper bannerMapper;
    private final NotifyLogMapper notifyLogMapper;
    private final GroupBuyViewer groupBuyViewer;
    private final NotificationService notificationService;
    private final AdminAccountService accounts;
    private final ObjectMapper objectMapper;
    private final SearchWordMapper searchWordMapper;

    public AdminService(StoreMapper storeMapper, UserMapper userMapper, ProductMapper productMapper,
                        StandardProductMapper standardMapper, CategoryMapper categoryMapper,
                        StoreLicenseMapper licenseMapper, MerchantAccountMapper accountMapper, OrderMapper orderMapper,
                        GroupBuyMapper groupBuyMapper, SearchLogMapper searchLogMapper, RegionMapper regionMapper,
                        BannerMapper bannerMapper, NotifyLogMapper notifyLogMapper, GroupBuyViewer groupBuyViewer,
                        NotificationService notificationService, AdminAccountService accounts, ObjectMapper objectMapper,
                        SearchWordMapper searchWordMapper) {
        this.searchWordMapper = searchWordMapper;
        this.storeMapper = storeMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.standardMapper = standardMapper;
        this.categoryMapper = categoryMapper;
        this.licenseMapper = licenseMapper;
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.searchLogMapper = searchLogMapper;
        this.regionMapper = regionMapper;
        this.bannerMapper = bannerMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.groupBuyViewer = groupBuyViewer;
        this.notificationService = notificationService;
        this.accounts = accounts;
        this.objectMapper = objectMapper;
    }

    // =====================================================================
    // 看板
    // =====================================================================

    public record Daily(String date, int orders, BigDecimal amount) {}

    public record KeywordCount(String keyword, long count) {}

    public record Dashboard(long users, long storesApproved, long storesPending, long products,
                            long ordersToday, BigDecimal amountToday, long orders7d, BigDecimal amount7d,
                            List<Daily> daily, List<KeywordCount> noResultKeywords,
                            long pendingLicenses, long pendingNormalize, long pendingSkus) {}

    /** 订单数、金额：不含未付款和已取消的订单，按下单日期 */
    public Dashboard dashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(13).atStartOfDay();
        List<Order> recent = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .select(Order::getCreatedAt, Order::getTotalAmount)
                .ge(Order::getCreatedAt, from)
                .notIn(Order::getStatus, OrderStatus.PENDING_PAY.name(), OrderStatus.CANCELLED.name()));
        Map<LocalDate, List<Order>> byDay = recent.stream().collect(Collectors.groupingBy(o -> o.getCreatedAt().toLocalDate()));
        List<Daily> daily = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<Order> l = byDay.getOrDefault(d, List.of());
            daily.add(new Daily(d.toString(), l.size(), sum(l)));
        }
        List<Order> todays = byDay.getOrDefault(today, List.of());
        List<Order> week = recent.stream().filter(o -> !o.getCreatedAt().toLocalDate().isBefore(today.minusDays(6))).toList();

        List<KeywordCount> noResult = searchLogMapper.selectMaps(new QueryWrapper<SearchLog>()
                        .select("keyword", "COUNT(*) AS cnt")
                        .eq("result_count", 0)
                        .ge("created_at", today.minusDays(6).atStartOfDay())
                        .groupBy("keyword")
                        .orderByDesc("cnt")
                        .last("LIMIT 20"))
                .stream().map(m -> new KeywordCount(String.valueOf(get(m, "keyword")), ((Number) get(m, "cnt")).longValue()))
                .toList();

        return new Dashboard(
                userMapper.selectCount(null),
                storeMapper.selectCount(new LambdaQueryWrapper<Store>().eq(Store::getStatus, Store.APPROVED)),
                storeMapper.selectCount(new LambdaQueryWrapper<Store>().eq(Store::getStatus, Store.PENDING)),
                productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getOnShelf, true)),
                todays.size(), sum(todays), week.size(), sum(week), daily, noResult,
                licenseMapper.selectCount(new LambdaQueryWrapper<StoreLicense>().eq(StoreLicense::getStatus, StoreLicense.PENDING)),
                productMapper.selectCount(new LambdaQueryWrapper<Product>()
                        .eq(Product::getOnShelf, true).eq(Product::getNormalizeStatus, Product.PENDING)),
                standardMapper.selectCount(new LambdaQueryWrapper<StandardProduct>()
                        .eq(StandardProduct::getStatus, StandardProduct.PENDING)));
    }

    private static Object get(Map<String, Object> m, String key) {
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return 0;
    }

    private static BigDecimal sum(List<Order> l) {
        return l.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // =====================================================================
    // 店铺
    // =====================================================================

    public PageResult<Map<String, Object>> stores(String status, String keyword, Integer page, Integer size) {
        String kw = keyword == null ? null : keyword.trim();
        Page<Store> p = storeMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Store>()
                .eq(status != null && !status.isBlank(), Store::getStatus, status)
                .and(kw != null && !kw.isEmpty(), w -> w.like(Store::getName, kw).or().like(Store::getPhone, kw)
                        .or().like(Store::getTown, kw))
                .orderByDesc(Store::getId));
        Map<Long, User> owners = usersOf(p.getRecords().stream().map(Store::getOwnerId).collect(Collectors.toSet()));
        return PageResult.of(p.getRecords().stream().map(s -> adminStore(s, owners.get(s.getOwnerId()))).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    public Map<String, Object> storeDetail(Long id) {
        Store s = requireStore(id);
        User owner = userMapper.selectById(s.getOwnerId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("store", adminStore(s, owner));
        Map<String, Object> ownerInfo = new LinkedHashMap<>();
        if (owner != null) {
            // 不把 openid 等内部标识给到后台页面
            ownerInfo.put("id", owner.getId());
            ownerInfo.put("nickname", owner.getNickname());
            ownerInfo.put("phone", owner.getPhone());
            ownerInfo.put("createdAt", owner.getCreatedAt());
        }
        out.put("owner", ownerInfo);
        out.put("licenses", licenseMapper.selectList(new LambdaQueryWrapper<StoreLicense>()
                        .eq(StoreLicense::getStoreId, id).orderByDesc(StoreLicense::getId))
                .stream().map(l -> MerchantToolsService.LicenseView.of(l, s.getName())).toList());
        MerchantAccount a = accountMapper.selectOne(new LambdaQueryWrapper<MerchantAccount>().eq(MerchantAccount::getStoreId, id));
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("status", a == null ? MerchantAccount.NONE : a.getStatus());
        account.put("statusLabel", MerchantAccount.statusLabel(a == null ? MerchantAccount.NONE : a.getStatus()));
        if (a != null) {
            account.put("subMchId", a.getSubMchId());
            account.put("bankCardMasked", a.getBankCardMasked());
            account.put("idNameMasked", a.getIdNameMasked());
            account.put("rejectReason", a.getRejectReason());
        }
        out.put("account", account);
        out.put("productCount", productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getStoreId, id)));
        out.put("orderCount", orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getStoreId, id)));
        return out;
    }

    @Transactional
    public Store setStoreStatus(Admin me, Long id, String action, String reason) {
        Store s = requireStore(id);
        String next;
        String notice;
        switch (action) {
            case "approve" -> {
                next = Store.APPROVED;
                notice = "店铺审核通过，附近的农户已经能看到你的店了";
            }
            case "reject" -> {
                if (!Store.PENDING.equals(s.getStatus())) throw new BizException("只有待审核的店铺能拒绝");
                next = Store.REJECTED;
                notice = "店铺审核没通过：" + reason;
            }
            case "ban" -> {
                next = Store.BANNED;
                notice = "店铺已被停用：" + reason;
            }
            case "unban" -> {
                if (!Store.BANNED.equals(s.getStatus())) throw new BizException("店铺没有停用");
                next = Store.APPROVED;
                notice = "店铺已恢复营业";
            }
            default -> throw BizException.notFound("操作");
        }
        if (("reject".equals(action) || "ban".equals(action)) && (reason == null || reason.isBlank())) {
            throw new BizException("请填写原因");
        }
        storeMapper.update(null, new LambdaUpdateWrapper<Store>()
                .eq(Store::getId, id).set(Store::getStatus, next)
                .set(Store::getStatusReason, "reject".equals(action) || "ban".equals(action) ? reason : null));
        accounts.log(me, "店铺-" + action, s.getName() + "#" + id, reason);
        notificationService.merchantNotice(id, "店铺状态", notice, "pages/merchant/home");
        return storeMapper.selectById(id);
    }

    private Map<String, Object> adminStore(Store s, User owner) {
        Map<String, Object> m = objectMapper.convertValue(s, new TypeReference<LinkedHashMap<String, Object>>() {});
        m.put("ownerNickname", owner == null ? null : owner.getNickname());
        m.put("ownerPhone", owner == null ? null : owner.getPhone());
        m.put("productCount", productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getStoreId, s.getId())));
        m.values().removeIf(v -> v == null);
        return m;
    }

    private Store requireStore(Long id) {
        Store s = storeMapper.selectById(id);
        if (s == null) throw BizException.notFound("店铺");
        return s;
    }

    // =====================================================================
    // 证件
    // =====================================================================

    public PageResult<MerchantToolsService.LicenseView> licenses(String status, Integer page, Integer size) {
        Page<StoreLicense> p = licenseMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<StoreLicense>()
                .eq(status != null && !status.isBlank(), StoreLicense::getStatus, status).orderByDesc(StoreLicense::getId));
        Map<Long, Store> stores = storesOf(p.getRecords().stream().map(StoreLicense::getStoreId).collect(Collectors.toSet()));
        return PageResult.of(p, l -> MerchantToolsService.LicenseView.of(l,
                stores.containsKey(l.getStoreId()) ? stores.get(l.getStoreId()).getName() : null));
    }

    public void reviewLicense(Admin me, Long id, boolean approve, String reason) {
        StoreLicense l = licenseMapper.selectById(id);
        if (l == null) throw BizException.notFound("证件");
        if (!approve && (reason == null || reason.isBlank())) throw new BizException("请填写原因");
        l.setStatus(approve ? StoreLicense.APPROVED : StoreLicense.REJECTED);
        l.setRejectReason(approve ? null : reason);
        licenseMapper.updateById(l);
        accounts.log(me, approve ? "证件通过" : "证件拒绝", "license#" + id, reason);
        notificationService.merchantNotice(l.getStoreId(), "证件审核",
                StoreLicense.typeLabel(l.getType()) + (approve ? "已通过" : "没通过：" + reason), "pages/merchant/licenses");
    }

    // =====================================================================
    // 商品与标准品库
    // =====================================================================

    public PageResult<Map<String, Object>> products(String keyword, Long storeId, String normalize, Boolean onShelf,
                                                    Integer page, Integer size) {
        String kw = keyword == null ? null : keyword.trim();
        Page<Product> p = productMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Product>()
                .eq(storeId != null, Product::getStoreId, storeId)
                .eq(normalize != null && !normalize.isBlank(), Product::getNormalizeStatus, normalize)
                .eq(onShelf != null, Product::getOnShelf, onShelf)
                .like(kw != null && !kw.isEmpty(), Product::getName, kw)
                .orderByDesc(Product::getId));
        Map<Long, Store> stores = storesOf(p.getRecords().stream().map(Product::getStoreId).collect(Collectors.toSet()));
        Set<Long> stdIds = p.getRecords().stream().map(Product::getStandardProductId).filter(x -> x != null).collect(Collectors.toSet());
        Map<Long, StandardProduct> stds = stdIds.isEmpty() ? Map.of() : standardMapper.selectBatchIds(stdIds).stream()
                .collect(Collectors.toMap(StandardProduct::getId, Function.identity()));
        return PageResult.of(p.getRecords().stream().map(x -> {
            Map<String, Object> m = objectMapper.convertValue(ProductView.of(x), new TypeReference<LinkedHashMap<String, Object>>() {});
            m.put("storeName", stores.containsKey(x.getStoreId()) ? stores.get(x.getStoreId()).getName() : null);
            m.put("normalizeStatus", x.getNormalizeStatus());
            StandardProduct sp = x.getStandardProductId() == null ? null : stds.get(x.getStandardProductId());
            m.put("standardName", sp == null ? null : sp.displayName());
            m.values().removeIf(v -> v == null);
            return m;
        }).toList(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public void offShelf(Admin me, Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new BizException("请填写下架原因");
        Product p = requireProduct(id);
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id).set(Product::getOnShelf, false).set(Product::getOffReason, reason));
        accounts.log(me, "下架商品", p.getName() + "#" + id, reason);
        notificationService.merchantNotice(p.getStoreId(), "商品被下架", p.getName() + "：" + reason, "pages/merchant/products");
    }

    public void link(Admin me, Long id, Long standardProductId) {
        Product p = requireProduct(id);
        StandardProduct std = standardMapper.selectById(standardProductId);
        if (std == null) throw BizException.notFound("标准商品");
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getStandardProductId, std.getId())
                .set(Product::getNormalizeStatus, Product.LINKED)
                .set(p.getCategoryId() == null, Product::getCategoryId, std.getCategoryId())
                .set(Product::getBrand, p.getBrand() != null ? p.getBrand() : std.getBrand())
                .set(Product::getSpec, p.getSpec() != null ? p.getSpec() : std.getSpec())
                .set(Product::getSearchText, ProductService.searchText(p, std)));
        accounts.log(me, "商品归一", p.getName() + "#" + id, std.displayName());
    }

    public void unlink(Admin me, Long id) {
        Product p = requireProduct(id);
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getStandardProductId, null)
                .set(Product::getNormalizeStatus, Product.PENDING)
                .set(Product::getSearchText, ProductService.searchText(p, null)));
        accounts.log(me, "取消归一", p.getName() + "#" + id, null);
    }

    private Product requireProduct(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw BizException.notFound("商品");
        return p;
    }

    public record StandardReq(Long categoryId, String brand, String name, String spec, String regNo, String barcode,
                              String manufacturer, String image, String keywords) {}

    /** 商品库列表；status = PENDING 看商家上架时自动建档、待核实的款 */
    public PageResult<Map<String, Object>> standards(String keyword, Long categoryId, String status, Integer page, Integer size) {
        String kw = keyword == null ? null : keyword.trim();
        Page<StandardProduct> p = standardMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<StandardProduct>()
                .eq(categoryId != null, StandardProduct::getCategoryId, categoryId)
                .eq(status != null && !status.isBlank(), StandardProduct::getStatus, status)
                .and(kw != null && !kw.isEmpty(), w -> w.like(StandardProduct::getName, kw).or().like(StandardProduct::getBrand, kw)
                        .or().like(StandardProduct::getKeywords, kw).or().eq(StandardProduct::getRegNo, SkuService.normRegNo(kw))
                        .or().eq(StandardProduct::getBarcode, kw))
                .orderByDesc(StandardProduct::getId));
        Set<Long> ids = p.getRecords().stream().map(StandardProduct::getId).collect(Collectors.toSet());
        Map<Long, Long> used = new HashMap<>();
        if (!ids.isEmpty()) {
            productMapper.selectList(new LambdaQueryWrapper<Product>().select(Product::getStandardProductId)
                            .in(Product::getStandardProductId, ids))
                    .forEach(x -> used.merge(x.getStandardProductId(), 1L, Long::sum));
        }
        Map<Long, Store> stores = storesOf(p.getRecords().stream().map(StandardProduct::getSourceStoreId)
                .filter(x -> x != null).collect(Collectors.toSet()));
        return PageResult.of(p.getRecords().stream().map(x -> {
            Map<String, Object> m = objectMapper.convertValue(SkuView.of(x), new TypeReference<LinkedHashMap<String, Object>>() {});
            m.put("productCount", used.getOrDefault(x.getId(), 0L));
            m.put("keywords", x.getKeywords());
            m.put("sourceStoreName", x.getSourceStoreId() != null && stores.containsKey(x.getSourceStoreId())
                    ? stores.get(x.getSourceStoreId()).getName() : null);
            m.put("createdAt", x.getCreatedAt());
            m.values().removeIf(v -> v == null);
            return m;
        }).toList(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public SkuView saveStandard(Admin me, Long id, StandardReq req) {
        if (req.name() == null || req.name().isBlank()) throw new BizException("请填写名称");
        if (req.categoryId() == null || categoryMapper.selectById(req.categoryId()) == null) throw new BizException("请选择分类");
        String barcode = blankToNull(req.barcode());
        if (barcode != null) {
            barcode = SkuService.normBarcode(barcode);
            if (barcode == null) throw new BizException("条码应该是 8 到 14 位数字");
            long dup = standardMapper.selectCount(new LambdaQueryWrapper<StandardProduct>()
                    .eq(StandardProduct::getBarcode, barcode).ne(id != null, StandardProduct::getId, id));
            if (dup > 0) throw new BizException("这个条码已经有了，可以用“合并”");
        }
        String regNo = SkuService.normRegNo(blankToNull(req.regNo()));
        String spec = SkuService.normSpec(blankToNull(req.spec()));
        if (regNo != null && spec != null) {
            long dup = standardMapper.selectCount(new LambdaQueryWrapper<StandardProduct>()
                    .eq(StandardProduct::getRegNo, regNo).eq(StandardProduct::getSpec, spec).ne(id != null, StandardProduct::getId, id));
            if (dup > 0) throw new BizException("同一个登记证号、同一个规格的款已经有了，可以用“合并”");
        }
        StandardProduct s = id == null ? new StandardProduct() : standardMapper.selectById(id);
        if (s == null) throw BizException.notFound("这款商品");
        s.setCategoryId(req.categoryId());
        s.setBrand(blankToNull(req.brand()));
        s.setName(req.name().trim());
        s.setSpec(spec);
        s.setRegNo(regNo);
        s.setBarcode(barcode);
        s.setManufacturer(blankToNull(req.manufacturer()));
        s.setImage(blankToNull(req.image()));
        s.setKeywords(blankToNull(req.keywords()));
        // 运营新增或修改过的，就算核实过了
        s.setStatus(StandardProduct.VERIFIED);
        if (id == null) standardMapper.insert(s);
        else {
            standardMapper.updateById(s);
            // 允许把字段清空
            standardMapper.update(null, new LambdaUpdateWrapper<StandardProduct>().eq(StandardProduct::getId, id)
                    .set(StandardProduct::getBrand, s.getBrand()).set(StandardProduct::getSpec, s.getSpec())
                    .set(StandardProduct::getRegNo, s.getRegNo()).set(StandardProduct::getBarcode, s.getBarcode())
                    .set(StandardProduct::getManufacturer, s.getManufacturer()).set(StandardProduct::getImage, s.getImage())
                    .set(StandardProduct::getKeywords, s.getKeywords()));
            refreshSearchText(id);
        }
        accounts.log(me, id == null ? "新增商品库款" : "修改商品库款", s.displayName(), null);
        return SkuView.of(standardMapper.selectById(s.getId()));
    }

    /** 核实：商家自动建档的款，运营看过照片、信息无误 */
    public void verifyStandard(Admin me, Long id) {
        StandardProduct s = standardMapper.selectById(id);
        if (s == null) throw BizException.notFound("这款商品");
        standardMapper.update(null, new LambdaUpdateWrapper<StandardProduct>().eq(StandardProduct::getId, id)
                .set(StandardProduct::getStatus, StandardProduct.VERIFIED));
        accounts.log(me, "核实商品库款", s.displayName(), null);
    }

    /** 合并：同一款被建了两条（比如一条有条码、一条只有登记证号），把 from 的商品都挂到 into，删掉 from */
    @Transactional
    public void mergeStandard(Admin me, Long fromId, Long intoId) {
        if (fromId.equals(intoId)) throw new BizException("不能和自己合并");
        StandardProduct from = standardMapper.selectById(fromId);
        StandardProduct into = standardMapper.selectById(intoId);
        if (from == null || into == null) throw BizException.notFound("这款商品");
        // 目标缺的身份信息，从被合并的那条补上
        LambdaUpdateWrapper<StandardProduct> fill = new LambdaUpdateWrapper<StandardProduct>().eq(StandardProduct::getId, intoId);
        boolean changed = false;
        if (into.getBarcode() == null && from.getBarcode() != null) { fill.set(StandardProduct::getBarcode, from.getBarcode()); changed = true; }
        if (into.getRegNo() == null && from.getRegNo() != null) { fill.set(StandardProduct::getRegNo, from.getRegNo()); changed = true; }
        if (into.getImage() == null && from.getImage() != null) { fill.set(StandardProduct::getImage, from.getImage()); changed = true; }
        if (into.getManufacturer() == null && from.getManufacturer() != null) { fill.set(StandardProduct::getManufacturer, from.getManufacturer()); changed = true; }
        standardMapper.deleteById(fromId);
        if (changed) standardMapper.update(null, fill);
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getStandardProductId, fromId).set(Product::getStandardProductId, intoId));
        refreshSearchText(intoId);
        accounts.log(me, "合并商品库款", from.displayName() + "#" + fromId, "并入 " + into.displayName() + "#" + intoId);
    }

    private void refreshSearchText(Long stdId) {
        StandardProduct std = standardMapper.selectById(stdId);
        for (Product p : productMapper.selectList(new LambdaQueryWrapper<Product>().eq(Product::getStandardProductId, stdId))) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>().eq(Product::getId, p.getId())
                    .set(Product::getSearchText, ProductService.searchText(p, std)));
        }
    }

    public void deleteStandard(Admin me, Long id) {
        long used = productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getStandardProductId, id));
        if (used > 0) throw new BizException("还有 " + used + " 个商品归到了它，不能删除（可以合并到别的款）");
        StandardProduct s = standardMapper.selectById(id);
        if (s == null) throw BizException.notFound("这款商品");
        standardMapper.deleteById(id);
        accounts.log(me, "删除商品库款", s.displayName(), null);
    }

    // ---------- 搜索同义词 ----------

    public record SearchWordReq(String word, String synonyms) {}

    public List<SearchWord> searchWords(String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        return searchWordMapper.selectList(new LambdaQueryWrapper<SearchWord>()
                .and(kw != null && !kw.isEmpty(), w -> w.like(SearchWord::getWord, kw).or().like(SearchWord::getSynonyms, kw))
                .orderByAsc(SearchWord::getId));
    }

    public SearchWord saveSearchWord(Admin me, Long id, SearchWordReq req) {
        String word = blankToNull(req.word());
        String syn = blankToNull(req.synonyms());
        if (word == null || syn == null) throw new BizException("请填写词和同义词");
        if (word.length() > 32 || syn.length() > 300) throw new BizException("太长了");
        SearchWord w = id == null ? new SearchWord() : searchWordMapper.selectById(id);
        if (w == null) throw BizException.notFound("同义词");
        w.setWord(word);
        w.setSynonyms(syn.replaceAll("[，,、；;]", " ").replaceAll("\\s+", " "));
        if (id == null) searchWordMapper.insert(w);
        else searchWordMapper.updateById(w);
        accounts.log(me, id == null ? "新增同义词" : "修改同义词", word, w.getSynonyms());
        return w;
    }

    public void deleteSearchWord(Admin me, Long id) {
        SearchWord w = searchWordMapper.selectById(id);
        if (w == null) throw BizException.notFound("同义词");
        searchWordMapper.deleteById(id);
        accounts.log(me, "删除同义词", w.getWord(), null);
    }

    public record CategoryReq(String name, Integer sort, String icon) {}

    public List<Category> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
    }

    public Category saveCategory(Admin me, Long id, CategoryReq req) {
        if (req.name() == null || req.name().isBlank() || req.name().length() > 16) throw new BizException("分类名 1 到 16 个字");
        Category c = id == null ? new Category() : categoryMapper.selectById(id);
        if (c == null) throw BizException.notFound("分类");
        c.setName(req.name().trim());
        c.setSort(req.sort() == null ? 0 : req.sort());
        c.setIcon(blankToNull(req.icon()));
        if (id == null) categoryMapper.insert(c);
        else categoryMapper.updateById(c);
        accounts.log(me, id == null ? "新增分类" : "修改分类", c.getName(), null);
        return c;
    }

    // =====================================================================
    // 订单、凑单（订单查询；仲裁取消在 OrderService）
    // =====================================================================

    public Page<Order> orders(String status, String keyword, Long storeId, Integer page, Integer size) {
        String kw = keyword == null ? null : keyword.trim();
        return orderMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Order>()
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .eq(storeId != null, Order::getStoreId, storeId)
                .and(kw != null && !kw.isEmpty(), w -> w.eq(Order::getOrderNo, kw).or().eq(Order::getContactPhone, kw))
                .orderByDesc(Order::getId));
    }

    public PageResult<GroupBuyView> groupBuys(String status, Integer page, Integer size) {
        Page<GroupBuy> p = groupBuyMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<GroupBuy>()
                .eq(status != null && !status.isBlank(), GroupBuy::getStatus, status).orderByDesc(GroupBuy::getId));
        return PageResult.of(groupBuyViewer.views(p.getRecords(), false, null, null, null), p.getTotal(), p.getCurrent(), p.getSize());
    }

    // =====================================================================
    // 用户、区域、轮播、通知记录
    // =====================================================================

    public record UserRow(Long id, String nickname, String phone, LocalDateTime createdAt, Long storeId) {}

    public PageResult<UserRow> users(String keyword, Integer page, Integer size) {
        String kw = keyword == null ? null : keyword.trim();
        Page<User> p = userMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<User>()
                .and(kw != null && !kw.isEmpty(), w -> w.like(User::getNickname, kw).or().like(User::getPhone, kw))
                .orderByDesc(User::getId));
        Set<Long> ids = p.getRecords().stream().map(User::getId).collect(Collectors.toSet());
        Map<Long, Long> storeByOwner = ids.isEmpty() ? Map.of() : storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .in(Store::getOwnerId, ids)).stream().collect(Collectors.toMap(Store::getOwnerId, Store::getId));
        return PageResult.of(p, u -> new UserRow(u.getId(), u.getNickname(), u.getPhone(), u.getCreatedAt(), storeByOwner.get(u.getId())));
    }

    public record RegionReq(String name, BigDecimal lat, BigDecimal lng, BigDecimal radiusKm, Boolean enabled) {}

    public List<Region> regions() {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>().orderByAsc(Region::getId));
    }

    public Region saveRegion(Admin me, Long id, RegionReq req) {
        if (req.name() == null || req.name().isBlank()) throw new BizException("请填写区域名称");
        if (req.lat() == null || req.lng() == null || req.lat().abs().doubleValue() > 90 || req.lng().abs().doubleValue() > 180) {
            throw new BizException("经纬度不对");
        }
        if (req.radiusKm() == null || req.radiusKm().doubleValue() <= 0 || req.radiusKm().doubleValue() > 500) {
            throw new BizException("半径填 0 到 500 公里");
        }
        Region r = id == null ? new Region() : regionMapper.selectById(id);
        if (r == null) throw BizException.notFound("区域");
        r.setName(req.name().trim());
        r.setLat(req.lat());
        r.setLng(req.lng());
        r.setRadiusKm(req.radiusKm());
        r.setEnabled(req.enabled() == null || req.enabled());
        if (id == null) regionMapper.insert(r);
        else regionMapper.updateById(r);
        accounts.log(me, id == null ? "新增区域" : "修改区域", r.getName(), null);
        return r;
    }

    public void deleteRegion(Admin me, Long id) {
        Region r = regionMapper.selectById(id);
        if (r == null) throw BizException.notFound("区域");
        regionMapper.deleteById(id);
        accounts.log(me, "删除区域", r.getName(), null);
    }

    public record BannerReq(String image, String title, String link, Integer sort, Boolean enabled, Long regionId) {}

    public List<Banner> banners() {
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>().orderByAsc(Banner::getSort).orderByDesc(Banner::getId));
    }

    public Banner saveBanner(Admin me, Long id, BannerReq req) {
        if (req.image() == null || req.image().isBlank()) throw new BizException("请上传图片");
        Banner b = id == null ? new Banner() : bannerMapper.selectById(id);
        if (b == null) throw BizException.notFound("轮播图");
        b.setImage(req.image());
        b.setTitle(blankToNull(req.title()));
        b.setLink(blankToNull(req.link()));
        b.setSort(req.sort() == null ? 0 : req.sort());
        b.setEnabled(req.enabled() == null || req.enabled());
        b.setRegionId(req.regionId());
        if (id == null) bannerMapper.insert(b);
        else {
            bannerMapper.updateById(b);
            bannerMapper.update(null, new LambdaUpdateWrapper<Banner>().eq(Banner::getId, id)
                    .set(Banner::getTitle, b.getTitle()).set(Banner::getLink, b.getLink()).set(Banner::getRegionId, b.getRegionId()));
        }
        accounts.log(me, id == null ? "新增轮播图" : "修改轮播图", b.getTitle(), null);
        return bannerMapper.selectById(b.getId());
    }

    public void deleteBanner(Admin me, Long id) {
        bannerMapper.deleteById(id);
        accounts.log(me, "删除轮播图", "banner#" + id, null);
    }

    public PageResult<NotifyLog> notifyLogs(Long orderId, Integer page, Integer size) {
        return PageResult.of(notifyLogMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<NotifyLog>()
                .eq(orderId != null, NotifyLog::getOrderId, orderId).orderByDesc(NotifyLog::getId)));
    }

    // =====================================================================

    private Map<Long, User> usersOf(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Store> storesOf(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : storeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
