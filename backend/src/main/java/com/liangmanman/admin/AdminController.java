package com.liangmanman.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liangmanman.common.ApiResult;
import com.liangmanman.common.BizException;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.common.PageResult;
import com.liangmanman.dto.GroupBuyView;
import com.liangmanman.dto.OrderView;
import com.liangmanman.dto.ReviewView;
import com.liangmanman.dto.WantedView;
import com.liangmanman.entity.Admin;
import com.liangmanman.entity.AdminLog;
import com.liangmanman.entity.Banner;
import com.liangmanman.entity.Category;
import com.liangmanman.entity.NotifyLog;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Region;
import com.liangmanman.entity.Review;
import com.liangmanman.entity.Settlement;
import com.liangmanman.entity.SearchWord;
import com.liangmanman.dto.SkuView;
import com.liangmanman.entity.Store;
import com.liangmanman.service.FileService;
import com.liangmanman.service.FundService;
import com.liangmanman.service.MerchantToolsService;
import com.liangmanman.service.OrderService;
import com.liangmanman.service.ReviewService;
import com.liangmanman.service.WantedService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 运营后台接口，见 docs/api-v2.md 第九节 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAccountService accounts;
    private final AdminService admin;
    private final OrderService orderService;
    private final FundService fundService;
    private final ReviewService reviewService;
    private final WantedService wantedService;
    private final FileService fileService;
    private final OrderMapper orderMapper;

    public AdminController(AdminAccountService accounts, AdminService admin, OrderService orderService,
                           FundService fundService, ReviewService reviewService, WantedService wantedService,
                           FileService fileService, OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
        this.accounts = accounts;
        this.admin = admin;
        this.orderService = orderService;
        this.fundService = fundService;
        this.reviewService = reviewService;
        this.wantedService = wantedService;
        this.fileService = fileService;
    }

    public record LoginReq(@NotBlank(message = "请输入账号") String username, @NotBlank(message = "请输入密码") String password) {}

    public record PasswordReq(@NotBlank String oldPassword, @NotBlank String newPassword) {}

    public record ReasonReq(@Size(max = 200, message = "原因太长了") String reason) {}

    public record LinkReq(@NotNull Long standardProductId) {}

    public record CreateAdminReq(@NotBlank @Size(max = 32) String username, @NotBlank String password,
                                 @NotBlank @Size(max = 32) String name, @NotBlank String role) {}

    public record ResetReq(@NotBlank String password) {}

    private static String reason(ReasonReq r) {
        return r == null ? null : r.reason();
    }

    // ---------- 登录与账号 ----------

    @PostMapping("/login")
    public ApiResult<AdminAccountService.LoginResp> login(@Valid @RequestBody LoginReq req) {
        return ApiResult.ok(accounts.login(req.username().trim(), req.password()));
    }

    @GetMapping("/me")
    public ApiResult<Admin> me() {
        return ApiResult.ok(AdminContext.require());
    }

    @PostMapping("/me/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody PasswordReq req) {
        accounts.changePassword(AdminContext.require(), req.oldPassword(), req.newPassword());
        return ApiResult.ok();
    }

    @GetMapping("/admins")
    public ApiResult<List<Admin>> admins() {
        AdminContext.requireSuper();
        return ApiResult.ok(accounts.list());
    }

    @PostMapping("/admins")
    public ApiResult<Admin> createAdmin(@Valid @RequestBody CreateAdminReq req) {
        return ApiResult.ok(accounts.create(AdminContext.requireSuper(), req.username().trim(), req.password(),
                req.name().trim(), req.role()));
    }

    @PostMapping("/admins/{id}/reset-password")
    public ApiResult<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetReq req) {
        accounts.resetPassword(AdminContext.requireSuper(), id, req.password());
        return ApiResult.ok();
    }

    @PostMapping("/admins/{id}/toggle")
    public ApiResult<Admin> toggleAdmin(@PathVariable Long id) {
        return ApiResult.ok(accounts.toggle(AdminContext.requireSuper(), id));
    }

    @GetMapping("/logs")
    public ApiResult<PageResult<AdminLog>> logs(@RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return ApiResult.ok(accounts.logs(page, size));
    }

    // ---------- 看板 ----------

    @GetMapping("/dashboard")
    public ApiResult<AdminService.Dashboard> dashboard() {
        return ApiResult.ok(admin.dashboard());
    }

    // ---------- 店铺与证件 ----------

    @GetMapping("/stores")
    public ApiResult<PageResult<Map<String, Object>>> stores(@RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResult.ok(admin.stores(status, keyword, page, size));
    }

    @GetMapping("/stores/{id}")
    public ApiResult<Map<String, Object>> store(@PathVariable Long id) {
        return ApiResult.ok(admin.storeDetail(id));
    }

    @PostMapping("/stores/{id}/{action:approve|reject|ban|unban}")
    public ApiResult<Store> storeAction(@PathVariable Long id, @PathVariable String action,
                                        @RequestBody(required = false) ReasonReq req) {
        return ApiResult.ok(admin.setStoreStatus(AdminContext.require(), id, action, reason(req)));
    }

    @GetMapping("/licenses")
    public ApiResult<PageResult<MerchantToolsService.LicenseView>> licenses(@RequestParam(required = false) String status,
                                                                            @RequestParam(required = false) Integer page,
                                                                            @RequestParam(required = false) Integer size) {
        return ApiResult.ok(admin.licenses(status, page, size));
    }

    @PostMapping("/licenses/{id}/approve")
    public ApiResult<Void> approveLicense(@PathVariable Long id) {
        admin.reviewLicense(AdminContext.require(), id, true, null);
        return ApiResult.ok();
    }

    @PostMapping("/licenses/{id}/reject")
    public ApiResult<Void> rejectLicense(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        admin.reviewLicense(AdminContext.require(), id, false, reason(req));
        return ApiResult.ok();
    }

    // ---------- 商品、标准品库、分类 ----------

    @GetMapping("/products")
    public ApiResult<PageResult<Map<String, Object>>> products(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Long storeId,
                                                               @RequestParam(required = false) String normalize,
                                                               @RequestParam(required = false) Boolean onShelf,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return ApiResult.ok(admin.products(keyword, storeId, normalize, onShelf, page, size));
    }

    @PostMapping("/products/{id}/off-shelf")
    public ApiResult<Void> offShelf(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        admin.offShelf(AdminContext.require(), id, reason(req));
        return ApiResult.ok();
    }

    @PostMapping("/products/{id}/link")
    public ApiResult<Void> link(@PathVariable Long id, @Valid @RequestBody LinkReq req) {
        admin.link(AdminContext.require(), id, req.standardProductId());
        return ApiResult.ok();
    }

    @PostMapping("/products/{id}/unlink")
    public ApiResult<Void> unlink(@PathVariable Long id) {
        admin.unlink(AdminContext.require(), id);
        return ApiResult.ok();
    }

    @GetMapping("/standard-products")
    public ApiResult<PageResult<Map<String, Object>>> standards(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Long categoryId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return ApiResult.ok(admin.standards(keyword, categoryId, status, page, size));
    }

    @PostMapping("/standard-products")
    public ApiResult<SkuView> createStandard(@RequestBody AdminService.StandardReq req) {
        return ApiResult.ok(admin.saveStandard(AdminContext.require(), null, req));
    }

    @PutMapping("/standard-products/{id}")
    public ApiResult<SkuView> updateStandard(@PathVariable Long id, @RequestBody AdminService.StandardReq req) {
        return ApiResult.ok(admin.saveStandard(AdminContext.require(), id, req));
    }

    @PostMapping("/standard-products/{id}/verify")
    public ApiResult<Void> verifyStandard(@PathVariable Long id) {
        admin.verifyStandard(AdminContext.require(), id);
        return ApiResult.ok();
    }

    public record MergeReq(@NotNull Long intoId) {}

    /** 把这一款并入 intoId：商品都改挂到 intoId，这一条删掉 */
    @PostMapping("/standard-products/{id}/merge")
    public ApiResult<Void> mergeStandard(@PathVariable Long id, @Valid @RequestBody MergeReq req) {
        admin.mergeStandard(AdminContext.require(), id, req.intoId());
        return ApiResult.ok();
    }

    @DeleteMapping("/standard-products/{id}")
    public ApiResult<Void> deleteStandard(@PathVariable Long id) {
        admin.deleteStandard(AdminContext.require(), id);
        return ApiResult.ok();
    }

    @GetMapping("/search-words")
    public ApiResult<List<SearchWord>> searchWords(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(admin.searchWords(keyword));
    }

    @PostMapping("/search-words")
    public ApiResult<SearchWord> createSearchWord(@RequestBody AdminService.SearchWordReq req) {
        return ApiResult.ok(admin.saveSearchWord(AdminContext.require(), null, req));
    }

    @PutMapping("/search-words/{id}")
    public ApiResult<SearchWord> updateSearchWord(@PathVariable Long id, @RequestBody AdminService.SearchWordReq req) {
        return ApiResult.ok(admin.saveSearchWord(AdminContext.require(), id, req));
    }

    @DeleteMapping("/search-words/{id}")
    public ApiResult<Void> deleteSearchWord(@PathVariable Long id) {
        admin.deleteSearchWord(AdminContext.require(), id);
        return ApiResult.ok();
    }

    @GetMapping("/categories")
    public ApiResult<List<Category>> categories() {
        return ApiResult.ok(admin.categories());
    }

    @PostMapping("/categories")
    public ApiResult<Category> createCategory(@RequestBody AdminService.CategoryReq req) {
        return ApiResult.ok(admin.saveCategory(AdminContext.require(), null, req));
    }

    @PutMapping("/categories/{id}")
    public ApiResult<Category> updateCategory(@PathVariable Long id, @RequestBody AdminService.CategoryReq req) {
        return ApiResult.ok(admin.saveCategory(AdminContext.require(), id, req));
    }

    // ---------- 订单、凑单 ----------

    @GetMapping("/orders")
    public ApiResult<PageResult<OrderView>> orders(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Long storeId,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        Page<Order> p = admin.orders(status, keyword, storeId, page, size);
        return ApiResult.ok(PageResult.of(orderService.toViews(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize()));
    }

    @GetMapping("/orders/{id}")
    public ApiResult<OrderView> order(@PathVariable Long id) {
        Order o = orderMapper.selectById(id);
        if (o == null) {
            throw BizException.notFound("订单");
        }
        return ApiResult.ok(orderService.toView(o, true));
    }

    @PostMapping("/orders/{id}/force-cancel")
    public ApiResult<OrderView> forceCancel(@PathVariable Long id, @RequestBody(required = false) ReasonReq req) {
        Admin me = AdminContext.require();
        String r = reason(req);
        if (r == null || r.isBlank()) {
            throw new BizException("请填写仲裁原因");
        }
        OrderView v = orderService.adminCancel(id, me.getName(), r);
        accounts.log(me, "仲裁取消订单", v.orderNo(), r);
        return ApiResult.ok(v);
    }

    @GetMapping("/group-buys")
    public ApiResult<PageResult<GroupBuyView>> groupBuys(@RequestParam(required = false) String status,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResult.ok(admin.groupBuys(status, page, size));
    }

    // ---------- 资金 ----------

    @GetMapping("/settlements")
    public ApiResult<PageResult<FundService.SettlementView>> settlements(@RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) Integer page,
                                                                         @RequestParam(required = false) Integer size) {
        return ApiResult.ok(fundService.adminSettlements(status, page, size));
    }

    @PostMapping("/settlements/{id}/retry")
    public ApiResult<Settlement> retrySettlement(@PathVariable Long id) {
        Settlement s = fundService.retrySettlement(id);
        accounts.log(AdminContext.require(), "重试解冻", "settlement#" + id, s.getStatus());
        return ApiResult.ok(s);
    }

    @GetMapping("/withdrawals")
    public ApiResult<PageResult<FundService.WithdrawView>> withdrawals(@RequestParam(required = false) String status,
                                                                       @RequestParam(required = false) Integer page,
                                                                       @RequestParam(required = false) Integer size) {
        return ApiResult.ok(fundService.adminWithdrawals(status, page, size));
    }

    @GetMapping("/refunds")
    public ApiResult<PageResult<FundService.RefundView>> refunds(@RequestParam(required = false) Integer page,
                                                                 @RequestParam(required = false) Integer size) {
        return ApiResult.ok(fundService.adminRefunds(page, size));
    }

    // ---------- 评价、求购、用户 ----------

    @GetMapping("/reviews")
    public ApiResult<PageResult<ReviewView>> reviews(@RequestParam(required = false) Long storeId,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResult.ok(reviewService.adminList(storeId, page, size));
    }

    @DeleteMapping("/reviews/{id}")
    public ApiResult<Void> deleteReview(@PathVariable Long id) {
        Review r = reviewService.adminDelete(id);
        accounts.log(AdminContext.require(), "删除评价", "review#" + id, r.getContent());
        return ApiResult.ok();
    }

    @GetMapping("/wanted")
    public ApiResult<PageResult<WantedView>> wanted(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResult.ok(wantedService.adminList(status, page, size));
    }

    @GetMapping("/users")
    public ApiResult<PageResult<AdminService.UserRow>> users(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResult.ok(admin.users(keyword, page, size));
    }

    // ---------- 区域、轮播 ----------

    @GetMapping("/regions")
    public ApiResult<List<Region>> regions() {
        return ApiResult.ok(admin.regions());
    }

    @PostMapping("/regions")
    public ApiResult<Region> createRegion(@RequestBody AdminService.RegionReq req) {
        return ApiResult.ok(admin.saveRegion(AdminContext.require(), null, req));
    }

    @PutMapping("/regions/{id}")
    public ApiResult<Region> updateRegion(@PathVariable Long id, @RequestBody AdminService.RegionReq req) {
        return ApiResult.ok(admin.saveRegion(AdminContext.require(), id, req));
    }

    @DeleteMapping("/regions/{id}")
    public ApiResult<Void> deleteRegion(@PathVariable Long id) {
        admin.deleteRegion(AdminContext.require(), id);
        return ApiResult.ok();
    }

    @GetMapping("/banners")
    public ApiResult<List<Banner>> banners() {
        return ApiResult.ok(admin.banners());
    }

    @PostMapping("/banners")
    public ApiResult<Banner> createBanner(@RequestBody AdminService.BannerReq req) {
        return ApiResult.ok(admin.saveBanner(AdminContext.require(), null, req));
    }

    @PutMapping("/banners/{id}")
    public ApiResult<Banner> updateBanner(@PathVariable Long id, @RequestBody AdminService.BannerReq req) {
        return ApiResult.ok(admin.saveBanner(AdminContext.require(), id, req));
    }

    @DeleteMapping("/banners/{id}")
    public ApiResult<Void> deleteBanner(@PathVariable Long id) {
        admin.deleteBanner(AdminContext.require(), id);
        return ApiResult.ok();
    }

    // ---------- 通知记录、上传 ----------

    @GetMapping("/notify-logs")
    public ApiResult<PageResult<NotifyLog>> notifyLogs(@RequestParam(required = false) Long orderId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResult.ok(admin.notifyLogs(orderId, page, size));
    }

    @PostMapping("/files")
    public ApiResult<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        AdminContext.require();
        return ApiResult.ok(Map.of("url", fileService.saveImage(file)));
    }
}
