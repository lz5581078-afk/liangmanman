package com.liangmanman.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liangmanman.auth.UserContext;
import com.liangmanman.common.ApiResult;
import com.liangmanman.common.BizException;
import com.liangmanman.dto.OrderPreview;
import com.liangmanman.dto.OrderReqs;
import com.liangmanman.dto.OrderView;
import com.liangmanman.entity.Address;
import com.liangmanman.mapper.AddressMapper;
import com.liangmanman.dto.ReviewView;
import com.liangmanman.dto.WantedView;
import com.liangmanman.pay.PaymentService;
import com.liangmanman.service.MiscService;
import com.liangmanman.service.OrderService;
import com.liangmanman.service.RecognizeService;
import com.liangmanman.service.ReviewService;
import com.liangmanman.service.WantedService;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 买家端：收货地址、结算、下单、支付、订单 */
@RestController
public class BuyerController {

    private final OrderService orderService;
    private final AddressMapper addressMapper;
    private final ReviewService reviewService;
    private final WantedService wantedService;
    private final RecognizeService recognizeService;
    private final MiscService miscService;

    public BuyerController(OrderService orderService, AddressMapper addressMapper, ReviewService reviewService,
                           WantedService wantedService, RecognizeService recognizeService, MiscService miscService) {
        this.orderService = orderService;
        this.addressMapper = addressMapper;
        this.reviewService = reviewService;
        this.wantedService = wantedService;
        this.recognizeService = recognizeService;
        this.miscService = miscService;
    }

    // ---------- 收货地址 ----------

    public record AddressReq(
            @NotBlank(message = "请填写收货人") @Size(max = 32) String contactName,
            @NotBlank(message = "请填写手机号") @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不对") String phone,
            @NotNull(message = "请定位收货位置") @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @NotNull(message = "请定位收货位置") @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
            @Size(max = 32) String town,
            @Size(max = 32) String village,
            @Size(max = 200) String detail,
            Boolean isDefault) {}

    @GetMapping("/api/addresses")
    public ApiResult<List<Address>> addresses() {
        return ApiResult.ok(addressMapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, UserContext.require())
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getId)));
    }

    @PostMapping("/api/addresses")
    @Transactional
    public ApiResult<Address> addAddress(@Valid @RequestBody AddressReq req) {
        Long uid = UserContext.require();
        long count = addressMapper.selectCount(new LambdaQueryWrapper<Address>().eq(Address::getUserId, uid));
        if (count >= 20) {
            throw new BizException("最多保存 20 个地址");
        }
        Address a = new Address();
        a.setUserId(uid);
        fill(a, req);
        boolean makeDefault = count == 0 || Boolean.TRUE.equals(req.isDefault());
        if (makeDefault) clearDefault(uid);
        a.setIsDefault(makeDefault);
        addressMapper.insert(a);
        return ApiResult.ok(a);
    }

    @PutMapping("/api/addresses/{id}")
    @Transactional
    public ApiResult<Address> updateAddress(@PathVariable Long id, @Valid @RequestBody AddressReq req) {
        Long uid = UserContext.require();
        Address a = requireAddress(uid, id);
        fill(a, req);
        if (Boolean.TRUE.equals(req.isDefault())) {
            clearDefault(uid);
            a.setIsDefault(true);
        }
        addressMapper.updateById(a);
        return ApiResult.ok(a);
    }

    @DeleteMapping("/api/addresses/{id}")
    public ApiResult<Void> deleteAddress(@PathVariable Long id) {
        Address a = requireAddress(UserContext.require(), id);
        addressMapper.deleteById(a.getId());
        return ApiResult.ok();
    }

    private Address requireAddress(Long uid, Long id) {
        Address a = addressMapper.selectById(id);
        if (a == null || !a.getUserId().equals(uid)) {
            throw BizException.notFound("地址");
        }
        return a;
    }

    private void fill(Address a, AddressReq req) {
        a.setContactName(req.contactName().trim());
        a.setPhone(req.phone());
        a.setLat(req.lat());
        a.setLng(req.lng());
        a.setTown(req.town());
        a.setVillage(req.village());
        a.setDetail(req.detail());
    }

    private void clearDefault(Long uid) {
        addressMapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, uid).set(Address::getIsDefault, false));
    }

    // ---------- 订单 ----------

    @PostMapping("/api/orders/preview")
    public ApiResult<OrderPreview> preview(@Valid @RequestBody OrderReqs.Preview req) {
        return ApiResult.ok(orderService.preview(UserContext.require(), req));
    }

    @PostMapping("/api/orders")
    public ApiResult<OrderView> create(@Valid @RequestBody OrderReqs.Create req) {
        return ApiResult.ok(orderService.create(UserContext.require(), req));
    }

    @GetMapping("/api/orders")
    public ApiResult<List<OrderView>> orders(@RequestParam(required = false) String status) {
        return ApiResult.ok(orderService.buyerOrders(UserContext.require(), status));
    }

    @GetMapping("/api/orders/{id}")
    public ApiResult<OrderView> order(@PathVariable Long id) {
        return ApiResult.ok(orderService.buyerOrder(UserContext.require(), id));
    }

    /** 返回 wx.requestPayment 参数；开发环境 mock=true，前端改调模拟支付接口 */
    @PostMapping("/api/orders/{id}/pay")
    public ApiResult<PaymentService.PayParams> pay(@PathVariable Long id) {
        return ApiResult.ok(orderService.pay(UserContext.require(), id));
    }

    @PostMapping("/api/orders/{id}/cancel")
    public ApiResult<OrderView> cancel(@PathVariable Long id, @RequestBody(required = false) OrderReqs.Reason req) {
        return ApiResult.ok(orderService.buyerCancel(UserContext.require(), id, req == null ? null : req.reason()));
    }

    @PostMapping("/api/orders/{id}/confirm")
    public ApiResult<OrderView> confirm(@PathVariable Long id) {
        return ApiResult.ok(orderService.buyerConfirm(UserContext.require(), id));
    }

    @GetMapping("/api/orders/{id}/reorder")
    public ApiResult<OrderService.Reorder> reorder(@PathVariable Long id) {
        return ApiResult.ok(orderService.reorder(UserContext.require(), id));
    }

    @PostMapping("/api/orders/{id}/review")
    public ApiResult<ReviewView> review(@PathVariable Long id, @Valid @RequestBody ReviewService.CreateReq req) {
        return ApiResult.ok(reviewService.create(UserContext.require(), id, req));
    }

    // ---------- 求购 ----------

    @PostMapping("/api/wanted")
    public ApiResult<WantedView> createWanted(@Valid @RequestBody WantedService.CreateReq req) {
        return ApiResult.ok(wantedService.create(UserContext.require(), req));
    }

    @GetMapping("/api/wanted/mine")
    public ApiResult<List<WantedView>> myWanted() {
        return ApiResult.ok(wantedService.mine(UserContext.require()));
    }

    @PostMapping("/api/wanted/{id}/close")
    public ApiResult<Void> closeWanted(@PathVariable Long id) {
        wantedService.close(UserContext.require(), id);
        return ApiResult.ok();
    }

    // ---------- 拍照识别、分享 ----------

    @PostMapping("/api/ocr/product")
    public ApiResult<RecognizeService.Result> ocr(@RequestParam("file") MultipartFile file) throws IOException {
        UserContext.require();
        if (file.isEmpty()) {
            throw new BizException("没有收到照片");
        }
        return ApiResult.ok(recognizeService.recognize(file.getBytes()));
    }

    public record ShareVisitReq(@NotNull Long sharerId, @NotBlank String targetType, @NotNull Long targetId) {}

    @PostMapping("/api/share/visit")
    public ApiResult<Void> shareVisit(@Valid @RequestBody ShareVisitReq req) {
        miscService.shareVisit(UserContext.require(), req.sharerId(), req.targetType(), req.targetId());
        return ApiResult.ok();
    }
}
