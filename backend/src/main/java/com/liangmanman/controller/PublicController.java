package com.liangmanman.controller;

import com.liangmanman.auth.UserContext;
import com.liangmanman.common.ApiResult;
import com.liangmanman.common.PageResult;
import com.liangmanman.config.AppProperties;
import com.liangmanman.dto.GroupBuyView;
import com.liangmanman.dto.ProductDetail;
import com.liangmanman.dto.ReviewView;
import com.liangmanman.dto.SearchResult;
import com.liangmanman.dto.SkuView;
import com.liangmanman.dto.StoreBrief;
import com.liangmanman.dto.StoreDetail;
import com.liangmanman.entity.Category;
import com.liangmanman.service.GroupBuyService;
import com.liangmanman.service.MiscService;
import com.liangmanman.service.ProductService;
import com.liangmanman.service.ReviewService;
import com.liangmanman.service.SearchService;
import com.liangmanman.service.SkuService;
import com.liangmanman.service.StoreService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** 不登录也能用的接口：附近的店、店铺页、商品页、搜索、凑单、评价、轮播、小程序码 */
@RestController
public class PublicController {

    private final StoreService storeService;
    private final ProductService productService;
    private final SearchService searchService;
    private final GroupBuyService groupBuyService;
    private final ReviewService reviewService;
    private final MiscService miscService;
    private final AppProperties props;
    private final SkuService skuService;

    public PublicController(StoreService storeService, ProductService productService, SearchService searchService,
                            GroupBuyService groupBuyService, ReviewService reviewService, MiscService miscService,
                            AppProperties props, SkuService skuService) {
        this.props = props;
        this.skuService = skuService;
        this.storeService = storeService;
        this.productService = productService;
        this.searchService = searchService;
        this.groupBuyService = groupBuyService;
        this.reviewService = reviewService;
        this.miscService = miscService;
    }

    @GetMapping("/api/stores/nearby")
    public ApiResult<List<StoreBrief>> nearby(@RequestParam double lat, @RequestParam double lng,
                                              @RequestParam(required = false) Double radiusKm,
                                              @RequestParam(required = false) Long categoryId) {
        return ApiResult.ok(storeService.nearby(lat, lng, radiusKm, categoryId));
    }

    @GetMapping("/api/stores/{id}")
    public ApiResult<StoreDetail> store(@PathVariable Long id,
                                        @RequestParam(required = false) Double lat,
                                        @RequestParam(required = false) Double lng) {
        return ApiResult.ok(storeService.detail(id, lat, lng));
    }

    @GetMapping("/api/stores/{id}/reviews")
    public ApiResult<PageResult<ReviewView>> storeReviews(@PathVariable Long id,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResult.ok(reviewService.ofStore(id, page, size));
    }

    @GetMapping("/api/products/{id}")
    public ApiResult<ProductDetail> product(@PathVariable Long id,
                                            @RequestParam(required = false) Double lat,
                                            @RequestParam(required = false) Double lng) {
        return ApiResult.ok(productService.detail(id, lat, lng));
    }

    /**
     * 搜索：平铺列表，可翻页。
     * sort = DEFAULT 综合（默认）| DISTANCE | PRICE_ASC | PRICE_DESC | SALES；
     * delivery = DELIVER | PICKUP | EXPRESS（可空）。搜条码 / 登记证号时 exact 里有同款比价。
     */
    @GetMapping("/api/search")
    public ApiResult<SearchResult> search(@RequestParam String keyword,
                                          @RequestParam(required = false) Double lat,
                                          @RequestParam(required = false) Double lng,
                                          @RequestParam(defaultValue = "DEFAULT") SearchService.Sort sort,
                                          @RequestParam(required = false) SearchService.Delivery delivery,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return ApiResult.ok(searchService.search(new SearchService.Query(keyword, lat, lng, sort, delivery, categoryId,
                page, size, UserContext.currentOrNull())));
    }

    /** 同款比价：这一款在各家店的价格。sort 同搜索（默认价格从低到高） */
    @GetMapping("/api/skus/{id}/offers")
    public ApiResult<SearchResult.SkuOffers> skuOffers(@PathVariable Long id,
                                                       @RequestParam(required = false) Double lat,
                                                       @RequestParam(required = false) Double lng,
                                                       @RequestParam(defaultValue = "DEFAULT") SearchService.Sort sort) {
        return ApiResult.ok(searchService.compare(id, lat, lng, sort));
    }

    /** 扫条码：商品库里有这一款就返回（data 为空表示还没有） */
    @GetMapping("/api/skus/lookup")
    public ApiResult<SkuView> skuLookup(@RequestParam(required = false) String barcode,
                                        @RequestParam(required = false) String regNo,
                                        @RequestParam(required = false) String spec) {
        return ApiResult.ok(SkuView.of(skuService.match(new SkuService.Identity(barcode, regNo, spec, null, null, null))));
    }

    @GetMapping("/api/group-buys/nearby")
    public ApiResult<List<GroupBuyView>> nearbyGroupBuys(@RequestParam double lat, @RequestParam double lng) {
        return ApiResult.ok(groupBuyService.nearby(lat, lng));
    }

    @GetMapping("/api/group-buys/{id}")
    public ApiResult<GroupBuyView> groupBuy(@PathVariable Long id,
                                            @RequestParam(required = false) Double lat,
                                            @RequestParam(required = false) Double lng) {
        return ApiResult.ok(groupBuyService.detail(id, UserContext.currentOrNull(), lat, lng));
    }

    @GetMapping("/api/categories")
    public ApiResult<List<Category>> categories() {
        return ApiResult.ok(productService.categories());
    }

    /** 商品库搜索（商家上架时“找这一款”） */
    @GetMapping("/api/standard-products")
    public ApiResult<List<SkuView>> standardProducts(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long categoryId) {
        return ApiResult.ok(productService.standardProducts(keyword, categoryId).stream().map(SkuView::of).toList());
    }

    @GetMapping("/api/banners")
    public ApiResult<List<MiscService.BannerView>> banners(@RequestParam(required = false) Double lat,
                                                           @RequestParam(required = false) Double lng) {
        return ApiResult.ok(miscService.banners(lat, lng));
    }

    public record AppConfig(String subscribeOrderTemplate, String subscribeMerchantTemplate) {}

    /** 小程序启动时取：订阅消息模板 ID（用于 wx.requestSubscribeMessage） */
    @GetMapping("/api/app-config")
    public ApiResult<AppConfig> appConfig() {
        return ApiResult.ok(new AppConfig(props.wx().subscribeOrderTemplate(), props.wx().subscribeMerchantTemplate()));
    }

    /** 小程序码图片：type = store | product | group */
    @GetMapping("/api/qrcode")
    public ResponseEntity<byte[]> qrcode(@RequestParam String type, @RequestParam Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(miscService.qrCode(type, id));
    }
}
