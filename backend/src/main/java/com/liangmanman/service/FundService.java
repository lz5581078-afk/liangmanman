package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liangmanman.common.BizException;
import com.liangmanman.common.IdCardUtil;
import com.liangmanman.common.Mask;
import com.liangmanman.common.NoGenerator;
import com.liangmanman.common.PageResult;
import com.liangmanman.entity.MerchantAccount;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.PrivateImage;
import com.liangmanman.entity.Refund;
import com.liangmanman.entity.Settlement;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.Withdraw;
import com.liangmanman.mapper.MerchantAccountMapper;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.PrivateImageMapper;
import com.liangmanman.mapper.RefundMapper;
import com.liangmanman.mapper.SettlementMapper;
import com.liangmanman.mapper.StoreMapper;
import com.liangmanman.mapper.WithdrawMapper;
import com.liangmanman.pay.OnboardingGateway;
import com.liangmanman.pay.PaymentService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商家的钱：收款开户（进件）、余额、提现、收入明细；以及“订单完成 → 解冻货款”的结算任务。
 * 平台不经手货款：钱在微信支付里从买家到店铺的冻结账户，解冻后店铺自己提现。
 */
@Slf4j
@Service
public class FundService {

    private final StoreService storeService;
    private final StoreMapper storeMapper;
    private final MerchantAccountMapper accountMapper;
    private final PrivateImageMapper privateImageMapper;
    private final SettlementMapper settlementMapper;
    private final WithdrawMapper withdrawMapper;
    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final FileService fileService;
    private final OnboardingGateway onboarding;
    private final PaymentService paymentService;

    public FundService(StoreService storeService, StoreMapper storeMapper, MerchantAccountMapper accountMapper,
                       PrivateImageMapper privateImageMapper, SettlementMapper settlementMapper,
                       WithdrawMapper withdrawMapper, RefundMapper refundMapper, OrderMapper orderMapper,
                       FileService fileService, OnboardingGateway onboarding, PaymentService paymentService) {
        this.storeService = storeService;
        this.storeMapper = storeMapper;
        this.accountMapper = accountMapper;
        this.privateImageMapper = privateImageMapper;
        this.settlementMapper = settlementMapper;
        this.withdrawMapper = withdrawMapper;
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.fileService = fileService;
        this.onboarding = onboarding;
        this.paymentService = paymentService;
    }

    // =====================================================================
    // 收款开户
    // =====================================================================

    public record AccountView(String status, String statusLabel, String subMchId, String bankCardMasked,
                              String idNameMasked, String signUrl, String rejectReason, boolean onlinePayEnabled) {}

    public AccountView account(Long userId) {
        Store store = storeService.requireMyStore(userId);
        MerchantAccount a = accountOf(store.getId());
        String status = a == null ? MerchantAccount.NONE : a.getStatus();
        return new AccountView(status, MerchantAccount.statusLabel(status),
                a == null ? null : a.getSubMchId(), a == null ? null : a.getBankCardMasked(),
                a == null ? null : a.getIdNameMasked(), a == null ? null : a.getSignUrl(),
                a == null ? null : a.getRejectReason(), paymentService.available(store));
    }

    public MerchantAccount accountOf(Long storeId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<MerchantAccount>().eq(MerchantAccount::getStoreId, storeId));
    }

    /** 上传身份证照（私密存储），返回 imageId */
    public String uploadIdImage(Long userId, MultipartFile file) {
        Store store = storeService.requireMyStore(userId);
        String id = UUID.randomUUID().toString().replace("-", "");
        Path path = fileService.savePrivateImage(file, id);
        PrivateImage img = new PrivateImage();
        img.setId(id);
        img.setStoreId(store.getId());
        img.setPath(path.toString());
        privateImageMapper.insert(img);
        return id;
    }

    public record OnboardingReq(
            @NotBlank(message = "请拍身份证人像面") String idCardFrontImageId,
            @NotBlank(message = "请拍身份证国徽面") String idCardBackImageId,
            @NotBlank(message = "请填写姓名") @Size(max = 32) String idName,
            @NotBlank(message = "请填写身份证号") String idNumber,
            @NotBlank(message = "请填写身份证有效期开始日期") String idValidFrom,
            @NotBlank(message = "请填写身份证有效期结束日期") String idValidTo,
            @NotBlank(message = "请选择开户银行") @Size(max = 64) String bankName,
            @NotBlank(message = "请选择开户城市") @Pattern(regexp = "^\\d{6}$", message = "开户城市不对") String bankCityCode,
            @NotBlank(message = "请填写银行卡号") @Pattern(regexp = "^\\d{12,19}$", message = "银行卡号应为 12 到 19 位数字") String bankAccount,
            @NotBlank(message = "请填写手机号") @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不对") String mobile,
            @Size(max = 32, message = "简称太长了") String shortName) {}

    /**
     * 提交开户。身份证号、卡号只在本次请求中使用并加密发给微信，平台只保存打码后的结果。
     */
    public AccountView submitOnboarding(Long userId, OnboardingReq req) {
        Store store = storeService.requireMyStore(userId);
        MerchantAccount a = accountOf(store.getId());
        if (a != null && !MerchantAccount.NONE.equals(a.getStatus()) && !MerchantAccount.REJECTED.equals(a.getStatus())) {
            throw new BizException("收款账户" + MerchantAccount.statusLabel(a.getStatus()) + "，不用重复提交");
        }
        String idNumber = req.idNumber().trim().toUpperCase();
        if (!IdCardUtil.valid(idNumber)) {
            throw new BizException("身份证号不对，请核对一下");
        }
        LocalDate from = parseDate(req.idValidFrom(), "有效期开始日期");
        if (!"长期".equals(req.idValidTo())) {
            LocalDate to = parseDate(req.idValidTo(), "有效期结束日期");
            if (to.isBefore(LocalDate.now())) throw new BizException("身份证已过期");
            if (!to.isAfter(from)) throw new BizException("有效期结束日期要晚于开始日期");
        }
        PrivateImage front = requireImage(store.getId(), req.idCardFrontImageId());
        PrivateImage back = requireImage(store.getId(), req.idCardBackImageId());

        String outNo = NoGenerator.next("A");
        String shortName = req.shortName() == null || req.shortName().isBlank() ? store.getName() : req.shortName().trim();
        OnboardingGateway.Application app = new OnboardingGateway.Application(
                outNo, fileService.readPrivate(front.getPath()), fileService.readPrivate(back.getPath()),
                req.idName().trim(), idNumber, req.idValidFrom(), req.idValidTo(), req.bankName().trim(),
                req.bankCityCode(), req.bankAccount(), req.mobile(), shortName, store.getName());
        String applymentId = onboarding.submit(app);

        if (a == null) {
            a = new MerchantAccount();
            a.setStoreId(store.getId());
        }
        a.setApplymentNo(outNo);
        a.setApplymentId(applymentId);
        a.setStatus(MerchantAccount.SUBMITTED);
        a.setBankCardMasked(Mask.card(req.bankAccount()));
        a.setIdNameMasked(Mask.name(req.idName().trim()));
        a.setRejectReason(null);
        a.setSignUrl(null);
        if (a.getId() == null) accountMapper.insert(a);
        else {
            accountMapper.updateById(a);
            accountMapper.update(null, new LambdaUpdateWrapper<MerchantAccount>()
                    .eq(MerchantAccount::getId, a.getId())
                    .set(MerchantAccount::getRejectReason, null).set(MerchantAccount::getSignUrl, null));
        }
        // 证件照已经上传给微信，本地立即删除
        for (PrivateImage img : List.of(front, back)) {
            fileService.deletePrivate(img.getPath());
            privateImageMapper.deleteById(img.getId());
        }
        refreshApplyment(accountMapper.selectById(a.getId()));
        return account(userId);
    }

    private PrivateImage requireImage(Long storeId, String id) {
        PrivateImage img = privateImageMapper.selectById(id);
        if (img == null || !img.getStoreId().equals(storeId)) {
            throw new BizException("证件照已失效，请重新拍");
        }
        return img;
    }

    private static LocalDate parseDate(String s, String what) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new BizException(what + "格式不对");
        }
    }

    /** 查询进件进度（定时任务 + 提交后立即查一次） */
    public void refreshApplyment(MerchantAccount a) {
        try {
            OnboardingGateway.Result r = onboarding.query(a.getApplymentNo());
            if (r.state().equals(a.getStatus()) && r.signUrl() == null && r.subMchId() == null) return;
            accountMapper.update(null, new LambdaUpdateWrapper<MerchantAccount>()
                    .eq(MerchantAccount::getId, a.getId())
                    .set(MerchantAccount::getStatus, r.state())
                    .set(r.subMchId() != null, MerchantAccount::getSubMchId, r.subMchId())
                    .set(MerchantAccount::getSignUrl, r.signUrl())
                    .set(MerchantAccount::getRejectReason, r.rejectReason()));
        } catch (Exception e) {
            log.warn("查询进件 {} 失败：{}", a.getApplymentNo(), e.getMessage());
        }
    }

    public int pollApplyments() {
        List<MerchantAccount> list = accountMapper.selectList(new LambdaQueryWrapper<MerchantAccount>()
                .in(MerchantAccount::getStatus, MerchantAccount.SUBMITTED, MerchantAccount.AUDITING, MerchantAccount.NEED_SIGN)
                .last("LIMIT 200"));
        list.forEach(this::refreshApplyment);
        return list.size();
    }

    // =====================================================================
    // 余额、提现、收入明细
    // =====================================================================

    public PaymentService.Balance balance(Long userId) {
        Store store = storeService.requireMyStore(userId);
        return paymentService.balance(store);
    }

    public record WithdrawReq(@NotNull(message = "请填写金额") @DecimalMin(value = "1", message = "最少提现 1 元") BigDecimal amount) {}

    public record WithdrawView(Long id, Long storeId, String storeName, BigDecimal amount, String status,
                               String statusLabel, String reason, LocalDateTime createdAt) {}

    public WithdrawView withdraw(Long userId, WithdrawReq req) {
        Store store = storeService.requireMyStore(userId);
        MerchantAccount a = accountOf(store.getId());
        if (!paymentService.available(store)) {
            throw new BizException("还没开通收款账户");
        }
        long processing = withdrawMapper.selectCount(new LambdaQueryWrapper<Withdraw>()
                .eq(Withdraw::getStoreId, store.getId()).eq(Withdraw::getStatus, Withdraw.PROCESSING));
        if (processing > 0) {
            throw new BizException("上一笔提现还在处理，到账后再提");
        }
        BigDecimal amount = req.amount().setScale(2, java.math.RoundingMode.DOWN);
        PaymentService.Balance b = paymentService.balance(store);
        if (amount.compareTo(b.available()) > 0) {
            throw new BizException("最多能提 " + b.available().stripTrailingZeros().toPlainString() + " 元");
        }
        Withdraw w = new Withdraw();
        w.setStoreId(store.getId());
        w.setSubMchId(a == null ? "MOCK_" + store.getId() : a.getSubMchId());
        w.setOutNo(NoGenerator.next("W"));
        w.setAmount(amount);
        w.setStatus(Withdraw.PROCESSING);
        withdrawMapper.insert(w);
        applyWithdrawResult(w, paymentService.withdraw(w));
        return withdrawView(withdrawMapper.selectById(w.getId()), store.getName());
    }

    private void applyWithdrawResult(Withdraw w, PaymentService.WithdrawResult r) {
        withdrawMapper.update(null, new LambdaUpdateWrapper<Withdraw>()
                .eq(Withdraw::getId, w.getId())
                .set(Withdraw::getStatus, r.status())
                .set(r.withdrawId() != null, Withdraw::getWithdrawId, r.withdrawId())
                .set(Withdraw::getReason, r.reason())
                .set(Withdraw::getUpdatedAt, LocalDateTime.now()));
    }

    public int pollWithdrawals() {
        List<Withdraw> list = withdrawMapper.selectList(new LambdaQueryWrapper<Withdraw>()
                .eq(Withdraw::getStatus, Withdraw.PROCESSING).last("LIMIT 200"));
        for (Withdraw w : list) {
            try {
                PaymentService.WithdrawResult r = paymentService.queryWithdraw(w);
                if (!Withdraw.PROCESSING.equals(r.status())) applyWithdrawResult(w, r);
            } catch (Exception e) {
                log.warn("查询提现 {} 失败：{}", w.getOutNo(), e.getMessage());
            }
        }
        return list.size();
    }

    public PageResult<WithdrawView> withdrawals(Long userId, Integer page, Integer size) {
        Store store = storeService.requireMyStore(userId);
        Page<Withdraw> p = withdrawMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Withdraw>()
                .eq(Withdraw::getStoreId, store.getId()).orderByDesc(Withdraw::getId));
        return PageResult.of(p, w -> withdrawView(w, store.getName()));
    }

    private static WithdrawView withdrawView(Withdraw w, String storeName) {
        String label = switch (w.getStatus()) {
            case Withdraw.SUCCESS -> "已到账";
            case Withdraw.FAILED -> "失败";
            default -> "处理中";
        };
        return new WithdrawView(w.getId(), w.getStoreId(), storeName, w.getAmount(), w.getStatus(), label,
                w.getReason(), w.getCreatedAt());
    }

    public record SettlementView(Long id, Long orderId, String orderNo, Long storeId, String storeName,
                                 BigDecimal amount, BigDecimal platformFee, String status, String statusLabel,
                                 String reason, LocalDateTime createdAt) {}

    public PageResult<SettlementView> settlements(Long userId, Integer page, Integer size) {
        Store store = storeService.requireMyStore(userId);
        Page<Settlement> p = settlementMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getStoreId, store.getId()).orderByDesc(Settlement::getId));
        return PageResult.of(settlementViews(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    // =====================================================================
    // 结算：订单完成 → 解冻货款（定时任务）
    // =====================================================================

    public int releasePending() {
        List<Settlement> list = settlementMapper.selectList(new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getStatus, Settlement.PENDING_RELEASE).last("LIMIT 200"));
        list.forEach(this::release);
        return list.size();
    }

    public void release(Settlement s) {
        String error;
        try {
            error = paymentService.release(s);
        } catch (Exception e) {
            error = e.getMessage();
        }
        settlementMapper.update(null, new LambdaUpdateWrapper<Settlement>()
                .eq(Settlement::getId, s.getId())
                .set(Settlement::getStatus, error == null ? Settlement.RELEASED : Settlement.FAILED)
                .set(Settlement::getError, error == null ? null : (error.length() > 300 ? error.substring(0, 300) : error))
                .set(error == null, Settlement::getReleasedAt, LocalDateTime.now()));
        if (error != null) {
            log.error("订单 {} 解冻失败：{}", s.getOrderId(), error);
        }
    }

    // =====================================================================
    // 运营后台
    // =====================================================================

    public PageResult<SettlementView> adminSettlements(String status, Integer page, Integer size) {
        Page<Settlement> p = settlementMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Settlement>()
                .eq(status != null && !status.isBlank(), Settlement::getStatus, status).orderByDesc(Settlement::getId));
        return PageResult.of(settlementViews(p.getRecords()), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public Settlement retrySettlement(Long id) {
        Settlement s = settlementMapper.selectById(id);
        if (s == null) throw BizException.notFound("结算记录");
        if (!Settlement.FAILED.equals(s.getStatus())) throw new BizException("只有失败的记录可以重试");
        release(s);
        return settlementMapper.selectById(id);
    }

    public PageResult<WithdrawView> adminWithdrawals(String status, Integer page, Integer size) {
        Page<Withdraw> p = withdrawMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<Withdraw>()
                .eq(status != null && !status.isBlank(), Withdraw::getStatus, status).orderByDesc(Withdraw::getId));
        Map<Long, Store> stores = storesOf(p.getRecords().stream().map(Withdraw::getStoreId).collect(Collectors.toSet()));
        return PageResult.of(p, w -> withdrawView(w, stores.containsKey(w.getStoreId()) ? stores.get(w.getStoreId()).getName() : null));
    }

    public record RefundView(Long id, Long orderId, String orderNo, String outRefundNo, BigDecimal amount,
                             String status, String reason, LocalDateTime createdAt) {}

    public PageResult<RefundView> adminRefunds(Integer page, Integer size) {
        Page<Refund> p = refundMapper.selectPage(PageResult.page(page, size),
                new LambdaQueryWrapper<Refund>().orderByDesc(Refund::getId));
        Map<Long, Order> orders = ordersOf(p.getRecords().stream().map(Refund::getOrderId).collect(Collectors.toSet()));
        return PageResult.of(p, r -> new RefundView(r.getId(), r.getOrderId(),
                orders.containsKey(r.getOrderId()) ? orders.get(r.getOrderId()).getOrderNo() : null,
                r.getOutRefundNo(), r.getAmount(), r.getStatus(), r.getReason(), r.getCreatedAt()));
    }

    private List<SettlementView> settlementViews(List<Settlement> list) {
        Map<Long, Order> orders = ordersOf(list.stream().map(Settlement::getOrderId).collect(Collectors.toSet()));
        Map<Long, Store> stores = storesOf(list.stream().map(Settlement::getStoreId).collect(Collectors.toSet()));
        return list.stream().map(s -> new SettlementView(s.getId(), s.getOrderId(),
                orders.containsKey(s.getOrderId()) ? orders.get(s.getOrderId()).getOrderNo() : null,
                s.getStoreId(), stores.containsKey(s.getStoreId()) ? stores.get(s.getStoreId()).getName() : null,
                s.getAmount(), s.getPlatformFee(), s.getStatus(),
                switch (s.getStatus()) {
                    case Settlement.RELEASED -> "已到可提现";
                    case Settlement.FAILED -> "解冻失败";
                    default -> "待解冻";
                }, s.getError(), s.getCreatedAt())).toList();
    }

    private Map<Long, Order> ordersOf(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : orderMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
    }

    private Map<Long, Store> storesOf(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : storeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
    }
}
