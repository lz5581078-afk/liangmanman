package com.liangmanman.controller;

import com.liangmanman.auth.UserContext;
import com.liangmanman.common.ApiResult;
import com.liangmanman.common.BizException;
import com.liangmanman.dto.OrderReqs;
import com.liangmanman.dto.OrderView;
import com.liangmanman.dto.ProductReqs;
import com.liangmanman.dto.ProductView;
import com.liangmanman.dto.StoreReqs;
import com.liangmanman.common.PageResult;
import com.liangmanman.dto.GroupBuyView;
import com.liangmanman.dto.ReviewView;
import com.liangmanman.dto.WantedView;
import com.liangmanman.entity.CreditCustomer;
import com.liangmanman.entity.Store;
import com.liangmanman.pay.PaymentService;
import com.liangmanman.service.FundService;
import com.liangmanman.service.GroupBuyService;
import com.liangmanman.service.MerchantToolsService;
import com.liangmanman.service.ReviewService;
import com.liangmanman.service.WantedService;
import com.liangmanman.service.OrderService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;
import com.liangmanman.service.ProductService;
import com.liangmanman.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 商家端：开店、店铺设置、商品、订单处理 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final StoreService storeService;
    private final ProductService productService;
    private final OrderService orderService;
    private final GroupBuyService groupBuyService;
    private final ReviewService reviewService;
    private final WantedService wantedService;
    private final MerchantToolsService tools;
    private final FundService fundService;

    public MerchantController(StoreService storeService, ProductService productService, OrderService orderService,
                              GroupBuyService groupBuyService, ReviewService reviewService, WantedService wantedService,
                              MerchantToolsService tools, FundService fundService) {
        this.storeService = storeService;
        this.productService = productService;
        this.orderService = orderService;
        this.groupBuyService = groupBuyService;
        this.reviewService = reviewService;
        this.wantedService = wantedService;
        this.tools = tools;
        this.fundService = fundService;
    }

    // ---------- 店铺 ----------

    @PostMapping("/store")
    public ApiResult<Store> open(@Valid @RequestBody StoreReqs.Open req) {
        return ApiResult.ok(storeService.open(UserContext.require(), req));
    }

    /** 还没开店时 data 为 null */
    @GetMapping("/store")
    public ApiResult<Store> myStore() {
        return ApiResult.ok(storeService.findByOwner(UserContext.require()));
    }

    @PutMapping("/store")
    public ApiResult<Store> update(@Valid @RequestBody StoreReqs.Update req) {
        return ApiResult.ok(storeService.update(UserContext.require(), req));
    }

    // ---------- 商品 ----------

    @GetMapping("/products")
    public ApiResult<List<ProductView>> products(@RequestParam(required = false) Boolean onShelf) {
        return ApiResult.ok(productService.mine(UserContext.require(), onShelf));
    }

    @PostMapping("/products")
    public ApiResult<ProductView> create(@Valid @RequestBody ProductReqs.Save req) {
        return ApiResult.ok(productService.create(UserContext.require(), req));
    }

    @PutMapping("/products/{id}")
    public ApiResult<ProductView> update(@PathVariable Long id, @Valid @RequestBody ProductReqs.Save req) {
        return ApiResult.ok(productService.update(UserContext.require(), id, req));
    }

    @PostMapping("/products/{id}/on-shelf")
    public ApiResult<Void> onShelf(@PathVariable Long id) {
        productService.setOnShelf(UserContext.require(), id, true);
        return ApiResult.ok();
    }

    @PostMapping("/products/{id}/off-shelf")
    public ApiResult<Void> offShelf(@PathVariable Long id) {
        productService.setOnShelf(UserContext.require(), id, false);
        return ApiResult.ok();
    }

    // ---------- 凑单 ----------

    @GetMapping("/group-buys")
    public ApiResult<List<GroupBuyView>> groupBuys() {
        return ApiResult.ok(groupBuyService.mine(UserContext.require()));
    }

    @PostMapping("/group-buys")
    public ApiResult<GroupBuyView> createGroupBuy(@Valid @RequestBody GroupBuyService.CreateReq req) {
        return ApiResult.ok(groupBuyService.create(UserContext.require(), req));
    }

    public record DecideReq(@NotBlank String action) {}

    @PostMapping("/group-buys/{id}/decide")
    public ApiResult<GroupBuyView> decide(@PathVariable Long id, @Valid @RequestBody DecideReq req) {
        return ApiResult.ok(groupBuyService.decide(UserContext.require(), id, req.action()));
    }

    @PostMapping("/group-buys/{id}/close")
    public ApiResult<GroupBuyView> closeGroupBuy(@PathVariable Long id) {
        return ApiResult.ok(groupBuyService.close(UserContext.require(), id));
    }

    // ---------- 评价、求购 ----------

    @GetMapping("/reviews")
    public ApiResult<PageResult<ReviewView>> reviews(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResult.ok(reviewService.mine(UserContext.require(), page, size));
    }

    @PostMapping("/reviews/{id}/reply")
    public ApiResult<ReviewView> replyReview(@PathVariable Long id, @Valid @RequestBody ReviewService.ReplyReq req) {
        return ApiResult.ok(reviewService.reply(UserContext.require(), id, req.reply()));
    }

    @GetMapping("/wanted")
    public ApiResult<List<WantedView>> wanted() {
        return ApiResult.ok(wantedService.nearbyForMerchant(UserContext.require()));
    }

    @PostMapping("/wanted/{id}/reply")
    public ApiResult<WantedView> replyWanted(@PathVariable Long id, @Valid @RequestBody WantedService.ReplyReq req) {
        return ApiResult.ok(wantedService.reply(UserContext.require(), id, req));
    }

    // ---------- 经营数据、证件 ----------

    @GetMapping("/stats")
    public ApiResult<MerchantToolsService.Stats> stats() {
        return ApiResult.ok(tools.stats(UserContext.require()));
    }

    @GetMapping("/licenses")
    public ApiResult<List<MerchantToolsService.LicenseView>> licenses() {
        return ApiResult.ok(tools.licenses(UserContext.require()));
    }

    @PostMapping("/licenses")
    public ApiResult<MerchantToolsService.LicenseView> addLicense(@Valid @RequestBody MerchantToolsService.LicenseReq req) {
        return ApiResult.ok(tools.addLicense(UserContext.require(), req));
    }

    // ---------- 收款开户、余额、提现 ----------

    @GetMapping("/account")
    public ApiResult<FundService.AccountView> account() {
        return ApiResult.ok(fundService.account(UserContext.require()));
    }

    @PostMapping("/onboarding/image")
    public ApiResult<Map<String, String>> onboardingImage(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(Map.of("imageId", fundService.uploadIdImage(UserContext.require(), file)));
    }

    @PostMapping("/onboarding")
    public ApiResult<FundService.AccountView> onboarding(@Valid @RequestBody FundService.OnboardingReq req) {
        return ApiResult.ok(fundService.submitOnboarding(UserContext.require(), req));
    }

    @GetMapping("/balance")
    public ApiResult<PaymentService.Balance> balance() {
        return ApiResult.ok(fundService.balance(UserContext.require()));
    }

    @PostMapping("/withdraw")
    public ApiResult<FundService.WithdrawView> withdraw(@Valid @RequestBody FundService.WithdrawReq req) {
        return ApiResult.ok(fundService.withdraw(UserContext.require(), req));
    }

    @GetMapping("/withdrawals")
    public ApiResult<PageResult<FundService.WithdrawView>> withdrawals(@RequestParam(required = false) Integer page,
                                                                       @RequestParam(required = false) Integer size) {
        return ApiResult.ok(fundService.withdrawals(UserContext.require(), page, size));
    }

    @GetMapping("/settlements")
    public ApiResult<PageResult<FundService.SettlementView>> settlements(@RequestParam(required = false) Integer page,
                                                                         @RequestParam(required = false) Integer size) {
        return ApiResult.ok(fundService.settlements(UserContext.require(), page, size));
    }

    // ---------- 赊账本 ----------

    @GetMapping("/credit/customers")
    public ApiResult<MerchantToolsService.CustomerList> creditCustomers(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(tools.customers(UserContext.require(), keyword));
    }

    @PostMapping("/credit/customers")
    public ApiResult<CreditCustomer> addCreditCustomer(@Valid @RequestBody MerchantToolsService.CustomerReq req) {
        return ApiResult.ok(tools.addCustomer(UserContext.require(), req));
    }

    @PutMapping("/credit/customers/{id}")
    public ApiResult<CreditCustomer> updateCreditCustomer(@PathVariable Long id,
                                                          @Valid @RequestBody MerchantToolsService.CustomerReq req) {
        return ApiResult.ok(tools.updateCustomer(UserContext.require(), id, req));
    }

    @GetMapping("/credit/customers/{id}/records")
    public ApiResult<PageResult<MerchantToolsService.RecordView>> creditRecords(@PathVariable Long id,
                                                                                @RequestParam(required = false) Integer page,
                                                                                @RequestParam(required = false) Integer size) {
        return ApiResult.ok(tools.records(UserContext.require(), id, page, size));
    }

    @PostMapping("/credit/customers/{id}/records")
    public ApiResult<CreditCustomer> addCreditRecord(@PathVariable Long id,
                                                     @Valid @RequestBody MerchantToolsService.RecordReq req) {
        return ApiResult.ok(tools.addRecord(UserContext.require(), id, req));
    }

    // ---------- 订单 ----------

    /** tab = NEW 新订单 | DOING 进行中 | DONE 已完成 | CANCELLED 已取消 */
    @GetMapping("/orders")
    public ApiResult<List<OrderView>> orders(@RequestParam(defaultValue = "NEW") OrderService.MerchantTab tab) {
        return ApiResult.ok(orderService.merchantOrders(UserContext.require(), tab));
    }

    @GetMapping("/orders/{id}")
    public ApiResult<OrderView> order(@PathVariable Long id) {
        return ApiResult.ok(orderService.merchantOrder(UserContext.require(), id));
    }

    /** 今日送货清单，按村分组 */
    @GetMapping("/delivery-sheet")
    public ApiResult<Map<String, List<OrderView>>> deliverySheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResult.ok(orderService.deliverySheet(UserContext.require(), date));
    }

    /**
     * 订单操作，action 与订单返回的 merchantActions 一一对应：
     * ACCEPT 接单 | REJECT 拒单 | DISPATCH 出发送货 | DELIVERED 已送达 | COD_RECEIVED 已收款
     * | PICKED_UP 已提货 | SHIP 发快递（需 expressCompany、expressNo） | CANCEL 取消（需 reason）
     */
    @PostMapping("/orders/{id}/{action}")
    public ApiResult<OrderView> act(@PathVariable Long id, @PathVariable String action,
                                    @RequestBody(required = false) Map<String, String> body) {
        Long uid = UserContext.require();
        Map<String, String> b = body == null ? Map.of() : body;
        OrderView v = switch (action.toUpperCase()) {
            case "ACCEPT" -> orderService.accept(uid, id);
            case "REJECT" -> orderService.reject(uid, id, b.get("reason"));
            case "DISPATCH" -> orderService.dispatch(uid, id);
            case "DELIVERED" -> orderService.delivered(uid, id);
            case "COD_RECEIVED" -> orderService.codReceived(uid, id);
            case "PICKED_UP" -> orderService.pickedUp(uid, id);
            case "SHIP" -> {
                String company = b.get("expressCompany");
                String no = b.get("expressNo");
                if (company == null || company.isBlank() || no == null || no.isBlank()) {
                    throw new BizException("请填写快递公司和单号");
                }
                if (company.length() > 32 || no.length() > 64) {
                    throw new BizException("快递信息太长了");
                }
                yield orderService.ship(uid, id, new OrderReqs.Ship(company, no));
            }
            case "CANCEL" -> orderService.merchantCancel(uid, id, b.get("reason"));
            default -> throw BizException.notFound("操作");
        };
        return ApiResult.ok(v);
    }
}
