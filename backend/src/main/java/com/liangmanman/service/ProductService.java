package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liangmanman.common.BizException;
import com.liangmanman.common.GeoUtil;
import com.liangmanman.common.JsonUtil;
import com.liangmanman.common.PinyinUtil;
import com.liangmanman.dto.ProductDetail;
import com.liangmanman.dto.ProductReqs;
import com.liangmanman.dto.ProductView;
import com.liangmanman.dto.SkuView;
import com.liangmanman.dto.StoreBrief;
import com.liangmanman.entity.Category;
import com.liangmanman.entity.Product;
import com.liangmanman.entity.StandardProduct;
import com.liangmanman.entity.Store;
import com.liangmanman.mapper.CategoryMapper;
import com.liangmanman.mapper.ProductMapper;
import com.liangmanman.mapper.StandardProductMapper;
import com.liangmanman.mapper.StoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final StandardProductMapper standardProductMapper;
    private final CategoryMapper categoryMapper;
    private final StoreMapper storeMapper;
    private final StoreService storeService;
    private final ContentGuard contentGuard;
    private final SkuService skuService;

    public ProductService(ProductMapper productMapper, StandardProductMapper standardProductMapper,
                          CategoryMapper categoryMapper, StoreMapper storeMapper, StoreService storeService,
                          ContentGuard contentGuard, SkuService skuService) {
        this.contentGuard = contentGuard;
        this.skuService = skuService;
        this.productMapper = productMapper;
        this.standardProductMapper = standardProductMapper;
        this.categoryMapper = categoryMapper;
        this.storeMapper = storeMapper;
        this.storeService = storeService;
    }

    // ---------- 商家端 ----------

    @Transactional
    public ProductView create(Long userId, ProductReqs.Save req) {
        Store store = storeService.requireMyStore(userId);
        contentGuard.check(userId, ContentGuard.SCENE_PROFILE, req.name(), req.description());
        Product p = new Product();
        p.setStoreId(store.getId());
        p.setSales(0);
        p.setOnShelf(true);
        apply(p, req);
        productMapper.insert(p);
        return ProductView.of(p);
    }

    @Transactional
    public ProductView update(Long userId, Long productId, ProductReqs.Save req) {
        Product p = requireMine(userId, productId);
        contentGuard.check(userId, ContentGuard.SCENE_PROFILE, req.name(), req.description());
        boolean wasTakenDown = p.getOffReason() != null;
        apply(p, req);
        if (wasTakenDown) {
            // 被平台下架的商品，商家修改后重新上架
            p.setOnShelf(true);
        }
        productMapper.updateById(p);
        // updateById 跳过 null：品牌、规格、所属款要能被清空
        productMapper.update(null, new LambdaUpdateWrapper<Product>().eq(Product::getId, productId)
                .set(Product::getBrand, p.getBrand()).set(Product::getSpec, p.getSpec())
                .set(Product::getStandardProductId, p.getStandardProductId())
                .set(Product::getNormalizeStatus, p.getNormalizeStatus()));
        if (wasTakenDown) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, productId).set(Product::getOffReason, null));
        }
        // updateById 会跳过 null 字段；库存改成“不限量”需要显式置空
        if (req.stock() == null) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, productId).set(Product::getStock, null));
        }
        return ProductView.of(productMapper.selectById(productId));
    }

    @Transactional
    public void setOnShelf(Long userId, Long productId, boolean onShelf) {
        Product p = requireMine(userId, productId);
        if (onShelf && p.getOffReason() != null) {
            throw new BizException("平台下架了这个商品（" + p.getOffReason() + "），修改后保存即可重新上架");
        }
        p.setOnShelf(onShelf);
        productMapper.updateById(p);
    }

    public List<ProductView> mine(Long userId, Boolean onShelf) {
        Store store = storeService.requireMyStore(userId);
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .eq(Product::getStoreId, store.getId())
                        .eq(onShelf != null, Product::getOnShelf, onShelf)
                        .orderByDesc(Product::getId))
                .stream().map(ProductView::of).toList();
    }

    private Product requireMine(Long userId, Long productId) {
        Store store = storeService.requireMyStore(userId);
        Product p = productMapper.selectById(productId);
        if (p == null || !p.getStoreId().equals(store.getId())) {
            throw BizException.notFound("商品");
        }
        return p;
    }

    /**
     * 把表单写进实体：默认值、配送方式校验，以及“这是哪一款”：
     * 1. 老板点了“是这一款”（standardProductId）→ 挂到这一款；
     * 2. 否则用包装上认出的条码 / 登记证号 + 规格去商品库找，找不到就自动建档（待核实）；
     * 3. 都没有 → 先不挂款（编辑时保留原来挂的款），等运营归一。
     */
    private void apply(Product p, ProductReqs.Save req) {
        boolean local = req.localDelivery() == null || req.localDelivery();
        boolean express = Boolean.TRUE.equals(req.expressDelivery());
        if (!local && !express) {
            throw new BizException("本地配送和快递发货至少选一项");
        }
        if (req.categoryId() != null && categoryMapper.selectById(req.categoryId()) == null) {
            throw BizException.notFound("分类");
        }

        String name = req.name() == null ? "" : req.name().trim();
        String brand = blankToNull(req.brand());
        String spec = SkuService.normSpec(blankToNull(req.spec()));
        String firstImage = req.images().isEmpty() ? null : req.images().get(0);

        SkuService.Identity id = new SkuService.Identity(blankToNull(req.barcode()), blankToNull(req.regNo()), spec,
                brand, name.isEmpty() ? null : name, blankToNull(req.manufacturer()));
        StandardProduct std;
        if (req.standardProductId() != null) {
            std = skuService.require(req.standardProductId());
            // 老板确认了这一款；包装上认出的条码 / 登记证号如果这一款还没有，补上
            skuService.enrich(std, id);
        } else {
            std = skuService.findOrCreate(id, req.categoryId(), firstImage, p.getStoreId());
            if (std == null && p.getStandardProductId() != null && !Boolean.TRUE.equals(req.notThisSku())) {
                std = standardProductMapper.selectById(p.getStandardProductId());
            }
        }
        Long categoryId = req.categoryId() != null ? req.categoryId() : (std != null ? std.getCategoryId() : null);
        if (std != null) {
            if (name.isEmpty()) name = std.getName();
            if (brand == null) brand = std.getBrand();
            if (spec == null) spec = std.getSpec();
        }
        if (name.isEmpty()) name = "商品";

        p.setName(name);
        p.setBrand(brand);
        p.setSpec(spec);
        p.setPrice(req.price());
        p.setImages(JsonUtil.toJson(req.images()));
        p.setCategoryId(categoryId);
        p.setStandardProductId(std != null ? std.getId() : null);
        p.setNormalizeStatus(std != null ? Product.LINKED : Product.PENDING);
        p.setUnit(req.unit() == null || req.unit().isBlank() ? "件" : req.unit().trim());
        p.setStock(req.stock());
        p.setDescription(req.description());
        p.setLocalDelivery(local);
        p.setExpressDelivery(express);
        p.setExpressFee(express && req.expressFee() != null ? req.expressFee() : BigDecimal.ZERO);
        p.setSearchText(searchText(p, std));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 搜索用文字：名称、品牌的拼音全拼和首字母 + 规格 + 所属款的名称、同义词、登记证号、条码 */
    public static String searchText(Product p, StandardProduct std) {
        StringBuilder sb = new StringBuilder(PinyinUtil.searchText(p.getName()));
        if (p.getBrand() != null) sb.append(' ').append(p.getBrand()).append(' ').append(PinyinUtil.searchText(p.getBrand()));
        if (p.getSpec() != null) sb.append(' ').append(p.getSpec());
        if (std != null) {
            sb.append(' ').append(std.displayName());
            if (std.getKeywords() != null) sb.append(' ').append(std.getKeywords());
            if (std.getRegNo() != null) sb.append(' ').append(std.getRegNo());
            if (std.getBarcode() != null) sb.append(' ').append(std.getBarcode());
        }
        String s = sb.toString().toLowerCase();
        return s.length() > 600 ? s.substring(0, 600) : s;
    }

    // ---------- 公共 ----------

    public ProductDetail detail(Long productId, Double lat, Double lng) {
        Product p = productMapper.selectById(productId);
        if (p == null || !Boolean.TRUE.equals(p.getOnShelf())) {
            throw BizException.notFound("商品");
        }
        Store s = storeMapper.selectById(p.getStoreId());
        if (s == null || !Store.APPROVED.equals(s.getStatus())) {
            throw BizException.notFound("商品");
        }
        Double distance = (lat != null && lng != null)
                ? GeoUtil.round1(GeoUtil.distanceKm(lat, lng, s.getLat().doubleValue(), s.getLng().doubleValue()))
                : null;

        StandardProduct std = p.getStandardProductId() == null ? null : standardProductMapper.selectById(p.getStandardProductId());
        long others = 0;
        if (std != null) {
            Set<Long> otherStoreIds = productMapper.selectList(new LambdaQueryWrapper<Product>()
                            .select(Product::getStoreId)
                            .eq(Product::getStandardProductId, std.getId())
                            .eq(Product::getOnShelf, true)
                            .ne(Product::getStoreId, p.getStoreId()))
                    .stream().map(Product::getStoreId).collect(Collectors.toSet());
            if (!otherStoreIds.isEmpty()) {
                others = storeMapper.selectCount(new LambdaQueryWrapper<Store>()
                        .in(Store::getId, otherStoreIds)
                        .eq(Store::getStatus, Store.APPROVED));
            }
        }
        return new ProductDetail(ProductView.of(p), StoreBrief.of(s, distance), SkuView.of(std), others);
    }

    public List<Category> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
    }

    /** 商家上架时“从商品库里找这一款”：按名称、品牌、同义词、登记证号、条码 */
    public List<StandardProduct> standardProducts(String keyword, Long categoryId) {
        String kw = keyword == null ? null : keyword.trim();
        return standardProductMapper.selectList(new LambdaQueryWrapper<StandardProduct>()
                .eq(categoryId != null, StandardProduct::getCategoryId, categoryId)
                .and(kw != null && !kw.isEmpty(), w -> w
                        .like(StandardProduct::getName, kw)
                        .or().like(StandardProduct::getBrand, kw)
                        .or().like(StandardProduct::getKeywords, kw)
                        .or().eq(StandardProduct::getRegNo, SkuService.normRegNo(kw))
                        .or().eq(StandardProduct::getBarcode, kw))
                .orderByAsc(StandardProduct::getCategoryId)
                .orderByAsc(StandardProduct::getId)
                .last("LIMIT 50"));
    }
}
