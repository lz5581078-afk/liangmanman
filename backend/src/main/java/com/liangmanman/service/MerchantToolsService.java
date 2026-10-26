package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liangmanman.common.BizException;
import com.liangmanman.common.PageResult;
import com.liangmanman.entity.CreditCustomer;
import com.liangmanman.entity.CreditRecord;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.OrderItem;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.StoreLicense;
import com.liangmanman.mapper.CreditCustomerMapper;
import com.liangmanman.mapper.CreditRecordMapper;
import com.liangmanman.mapper.OrderItemMapper;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.StoreLicenseMapper;
import com.liangmanman.order.OrderStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 商家工具：经营数据、证件、赊账本 */
@Service
public class MerchantToolsService {

    private final StoreService storeService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final StoreLicenseMapper licenseMapper;
    private final CreditCustomerMapper customerMapper;
    private final CreditRecordMapper recordMapper;
    private final ReviewService reviewService;
    private final WantedService wantedService;

    public MerchantToolsService(StoreService storeService, OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                StoreLicenseMapper licenseMapper, CreditCustomerMapper customerMapper,
                                CreditRecordMapper recordMapper, ReviewService reviewService, WantedService wantedService) {
        this.storeService = storeService;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.licenseMapper = licenseMapper;
        this.customerMapper = customerMapper;
        this.recordMapper = recordMapper;
        this.reviewService = reviewService;
        this.wantedService = wantedService;
    }

    // =====================================================================
    // 经营数据
    // =====================================================================

    public record TopProduct(Long productId, String name, int quantity, BigDecimal amount) {}

    public record Stats(int todayOrders, BigDecimal todayAmount, int monthOrders, BigDecimal monthAmount,
                        int newOrders, int doingOrders, long pendingReviews, long openWanted,
                        List<TopProduct> topProducts) {}

    /** 订单数、金额只算已完成的订单（按完成时间）；新订单、进行中按当前状态 */
    public Stats stats(Long userId) {
        Store store = storeService.requireMyStore(userId);
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime month = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<Order> monthDone = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStoreId, store.getId())
                .eq(Order::getStatus, OrderStatus.COMPLETED.name())
                .ge(Order::getCompletedAt, month));
        List<Order> todayDone = monthDone.stream().filter(o -> !o.getCompletedAt().isBefore(today)).toList();
        long newOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStoreId, store.getId())
                .in(Order::getStatus, OrderStatus.PENDING_ACCEPT.name(), OrderStatus.PENDING_SHIP.name()));
        long doing = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStoreId, store.getId())
                .in(Order::getStatus, OrderStatus.ACCEPTED.name(), OrderStatus.DELIVERING.name(),
                        OrderStatus.DELIVERED.name(), OrderStatus.READY_PICKUP.name(), OrderStatus.SHIPPED.name()));

        // 近 30 天热销
        List<Order> recent = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .select(Order::getId)
                .eq(Order::getStoreId, store.getId())
                .eq(Order::getStatus, OrderStatus.COMPLETED.name())
                .ge(Order::getCompletedAt, LocalDateTime.now().minusDays(30)));
        List<TopProduct> top = new ArrayList<>();
        if (!recent.isEmpty()) {
            Map<Long, TopProduct> agg = new LinkedHashMap<>();
            for (OrderItem i : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                    .in(OrderItem::getOrderId, recent.stream().map(Order::getId).toList()))) {
                agg.merge(i.getProductId(), new TopProduct(i.getProductId(), i.getProductName(), i.getQuantity(), i.getAmount()),
                        (a, b) -> new TopProduct(a.productId(), a.name(), a.quantity() + b.quantity(), a.amount().add(b.amount())));
            }
            top = agg.values().stream().sorted(Comparator.comparing(TopProduct::quantity).reversed()).limit(5).toList();
        }
        return new Stats(todayDone.size(), sum(todayDone), monthDone.size(), sum(monthDone),
                (int) newOrders, (int) doing, reviewService.unrepliedCount(store.getId()),
                wantedService.nearbyCount(userId), top);
    }

    private static BigDecimal sum(List<Order> orders) {
        return orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // =====================================================================
    // 证件
    // =====================================================================

    public record LicenseReq(
            @NotBlank(message = "请选择证件类型") @Pattern(regexp = "BUSINESS|PESTICIDE|SEED|VET", message = "证件类型不对") String type,
            @NotBlank(message = "请拍证件照片") @Size(max = 500) String image,
            LocalDate validUntil) {}

    public record LicenseView(Long id, Long storeId, String storeName, String type, String typeLabel, String image,
                              LocalDate validUntil, String status, String rejectReason, LocalDateTime createdAt) {
        public static LicenseView of(StoreLicense l, String storeName) {
            return new LicenseView(l.getId(), l.getStoreId(), storeName, l.getType(), StoreLicense.typeLabel(l.getType()),
                    l.getImage(), l.getValidUntil(), l.getStatus(), l.getRejectReason(), l.getCreatedAt());
        }
    }

    public List<LicenseView> licenses(Long userId) {
        Store store = storeService.requireMyStore(userId);
        return licenseMapper.selectList(new LambdaQueryWrapper<StoreLicense>()
                        .eq(StoreLicense::getStoreId, store.getId()).orderByDesc(StoreLicense::getId))
                .stream().map(l -> LicenseView.of(l, store.getName())).toList();
    }

    public LicenseView addLicense(Long userId, LicenseReq req) {
        Store store = storeService.requireMyStore(userId);
        if (req.validUntil() != null && req.validUntil().isBefore(LocalDate.now())) {
            throw new BizException("证件已经过期了");
        }
        StoreLicense l = new StoreLicense();
        l.setStoreId(store.getId());
        l.setType(req.type());
        l.setImage(req.image());
        l.setValidUntil(req.validUntil());
        l.setStatus(StoreLicense.PENDING);
        licenseMapper.insert(l);
        return LicenseView.of(l, store.getName());
    }

    // =====================================================================
    // 赊账本（只记账，钱不经过平台）
    // =====================================================================

    public record CustomerReq(
            @NotBlank(message = "请填写名字") @Size(max = 32) String name,
            @Pattern(regexp = "^[0-9\\-]{7,20}$", message = "电话格式不对") String phone,
            @Size(max = 32) String village) {}

    public record RecordReq(
            @NotBlank @Pattern(regexp = "CREDIT|REPAY", message = "类型不对") String type,
            @NotNull(message = "请填写金额") @DecimalMin(value = "0.01", message = "金额要大于 0") @DecimalMax(value = "9999999", message = "金额太大了") BigDecimal amount,
            @Size(max = 200) String note) {}

    public record CustomerList(BigDecimal totalOwed, List<CreditCustomer> customers) {}

    public record RecordView(Long id, String type, String typeLabel, BigDecimal amount, String note, LocalDateTime createdAt) {}

    public CustomerList customers(Long userId, String keyword) {
        Store store = storeService.requireMyStore(userId);
        String kw = keyword == null ? null : keyword.trim();
        List<CreditCustomer> list = customerMapper.selectList(new LambdaQueryWrapper<CreditCustomer>()
                .eq(CreditCustomer::getStoreId, store.getId())
                .and(kw != null && !kw.isEmpty(), w -> w.like(CreditCustomer::getName, kw)
                        .or().like(CreditCustomer::getPhone, kw).or().like(CreditCustomer::getVillage, kw))
                .orderByDesc(CreditCustomer::getBalance)
                .orderByDesc(CreditCustomer::getUpdatedAt));
        BigDecimal owed = customerMapper.selectList(new LambdaQueryWrapper<CreditCustomer>()
                        .select(CreditCustomer::getBalance)
                        .eq(CreditCustomer::getStoreId, store.getId())
                        .gt(CreditCustomer::getBalance, 0))
                .stream().map(CreditCustomer::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CustomerList(owed, list);
    }

    public CreditCustomer addCustomer(Long userId, CustomerReq req) {
        Store store = storeService.requireMyStore(userId);
        CreditCustomer c = new CreditCustomer();
        c.setStoreId(store.getId());
        c.setName(req.name().trim());
        c.setPhone(req.phone());
        c.setVillage(req.village());
        c.setBalance(BigDecimal.ZERO);
        customerMapper.insert(c);
        return customerMapper.selectById(c.getId());
    }

    public CreditCustomer updateCustomer(Long userId, Long id, CustomerReq req) {
        CreditCustomer c = requireCustomer(userId, id);
        c.setName(req.name().trim());
        c.setPhone(req.phone());
        c.setVillage(req.village());
        customerMapper.updateById(c);
        return customerMapper.selectById(id);
    }

    public PageResult<RecordView> records(Long userId, Long customerId, Integer page, Integer size) {
        requireCustomer(userId, customerId);
        Page<CreditRecord> p = recordMapper.selectPage(PageResult.page(page, size), new LambdaQueryWrapper<CreditRecord>()
                .eq(CreditRecord::getCustomerId, customerId).orderByDesc(CreditRecord::getId));
        return PageResult.of(p, r -> new RecordView(r.getId(), r.getType(),
                CreditRecord.CREDIT.equals(r.getType()) ? "赊账" : "还款", r.getAmount(), r.getNote(), r.getCreatedAt()));
    }

    @Transactional
    public CreditCustomer addRecord(Long userId, Long customerId, RecordReq req) {
        CreditCustomer c = requireCustomer(userId, customerId);
        CreditRecord r = new CreditRecord();
        r.setCustomerId(c.getId());
        r.setStoreId(c.getStoreId());
        r.setType(req.type());
        r.setAmount(req.amount());
        r.setNote(req.note());
        recordMapper.insert(r);
        BigDecimal delta = CreditRecord.CREDIT.equals(req.type()) ? req.amount() : req.amount().negate();
        customerMapper.addBalance(c.getId(), delta);
        return customerMapper.selectById(c.getId());
    }

    private CreditCustomer requireCustomer(Long userId, Long id) {
        Store store = storeService.requireMyStore(userId);
        CreditCustomer c = customerMapper.selectById(id);
        if (c == null || !c.getStoreId().equals(store.getId())) {
            throw BizException.notFound("客户");
        }
        return c;
    }
}
