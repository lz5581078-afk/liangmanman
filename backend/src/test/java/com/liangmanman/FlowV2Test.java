package com.liangmanman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liangmanman.common.IdCardUtil;
import com.liangmanman.integration.WxApi;
import com.liangmanman.service.FundService;
import com.liangmanman.service.GroupBuyService;
import com.liangmanman.service.OrderService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 第二批功能端到端：凑单、评价、再来一单、求购、赊账、证件、开户、解冻与提现、催单通知、
 * 拼音搜索、拍照识别、小程序码、运营后台。使用与第一批不同的地理位置，互不干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowV2Test {

    static final double LAT = 30.0, LNG = 110.0;
    static final double NEAR_LAT = 30.01, NEAR_LNG = 110.01;
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orderService;
    @Autowired GroupBuyService groupBuyService;
    @Autowired FundService fundService;

    String merchant, buyer, buyer2, adminToken;
    long storeId, pFert, pSeed, nearAddr, nearAddr2;

    // ---------- 工具 ----------

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return om.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    JsonNode ok(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        JsonNode r = call(req, token, body);
        assertEquals(0, r.get("code").asInt(), () -> "期望成功，实际：" + r);
        return r.get("data");
    }

    String fail(MockHttpServletRequestBuilder req, String token, Object body, int code) throws Exception {
        JsonNode r = call(req, token, body);
        assertEquals(code, r.get("code").asInt(), () -> "期望失败码 " + code + "，实际：" + r);
        return r.get("msg").asText();
    }

    JsonNode upload(String url, String token, byte[] bytes) throws Exception {
        String s = mvc.perform(multipart(url).file(new MockMultipartFile("file", "a.png", "image/png", bytes))
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode r = om.readTree(s);
        assertEquals(0, r.get("code").asInt(), s);
        return r.get("data");
    }

    String login(String code) throws Exception {
        return ok(post("/api/auth/wx-login"), null, Map.of("code", code)).get("token").asText();
    }

    long id(JsonNode n) {
        return n.get("id").asLong();
    }

    List<String> list(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(x -> out.add(x.asText()));
        return out;
    }

    Map<String, Object> order(long productId, int qty, String fulfillment, String pay, Long addressId, Long groupBuyId) {
        Map<String, Object> m = new HashMap<>();
        m.put("storeId", storeId);
        m.put("items", List.of(Map.of("productId", productId, "quantity", qty)));
        m.put("fulfillment", fulfillment);
        m.put("payMethod", pay);
        if (addressId != null) m.put("addressId", addressId);
        if (groupBuyId != null) m.put("groupBuyId", groupBuyId);
        m.put("contactName", "测试");
        m.put("contactPhone", "13600000009");
        return m;
    }

    /** 下单并（在线）付款，返回订单 id */
    long orderAndPay(String token, Map<String, Object> req) throws Exception {
        long oid = id(ok(post("/api/orders"), token, req));
        String no = ok(post("/api/orders/" + oid + "/pay"), token, null).get("outTradeNo").asText();
        ok(post("/api/pay/mock/" + no + "/success"), token, null);
        return oid;
    }

    String status(String token, long orderId) throws Exception {
        return ok(get("/api/orders/" + orderId), token, null).get("status").asText();
    }

    /** 送货上门在线支付订单走到完成 */
    long completedOnlineOrder(String token, long addressId) throws Exception {
        long oid = orderAndPay(token, order(pFert, 1, "DELIVERY", "ONLINE", addressId, null));
        for (String a : List.of("accept", "dispatch", "delivered")) {
            ok(post("/api/merchant/orders/" + oid + "/" + a), merchant, Map.of());
        }
        ok(post("/api/orders/" + oid + "/confirm"), token, null);
        return oid;
    }

    static String validIdCard() {
        String body = "41010119800101123";
        for (char c : "0123456789X".toCharArray()) {
            if (IdCardUtil.valid(body + c)) return body + c;
        }
        throw new IllegalStateException();
    }

    // ---------- 准备 ----------

    @BeforeAll
    void setUp() throws Exception {
        merchant = login("v2-merchant");
        buyer = login("v2-buyer");
        buyer2 = login("v2-buyer2");
        storeId = id(ok(post("/api/merchant/store"), merchant, Map.of(
                "name", "丰收农资", "phone", "13700000001", "lat", LAT, "lng", LNG)));
        ok(put("/api/merchant/store"), merchant, Map.of("deliveryRadiusKm", 10, "minOrderAmount", 0, "deliveryFee", 0));
        Map<String, Object> fert = new HashMap<>(Map.of("images", List.of("http://test.local/files/f.jpg"),
                "price", 100, "name", "复合肥特惠", "localDelivery", true, "expressDelivery", false));
        pFert = id(ok(post("/api/merchant/products"), merchant, fert));
        Map<String, Object> seed = new HashMap<>(Map.of("images", List.of("http://test.local/files/s.jpg"),
                "price", 50, "name", "玉米种子郑单", "localDelivery", true, "expressDelivery", true, "expressFee", 10));
        pSeed = id(ok(post("/api/merchant/products"), merchant, seed));
        nearAddr = id(ok(post("/api/addresses"), buyer, Map.of("contactName", "赵", "phone", "13600000001",
                "lat", NEAR_LAT, "lng", NEAR_LNG, "village", "前进村")));
        nearAddr2 = id(ok(post("/api/addresses"), buyer2, Map.of("contactName", "钱", "phone", "13600000002",
                "lat", NEAR_LAT, "lng", NEAR_LNG, "village", "后进村")));
        adminToken = ok(post("/api/admin/login"), null, Map.of("username", "admin", "password", "Admin12345")).get("token").asText();
    }

    @AfterAll
    void cleanUp() {
        // 开通区域会影响其他测试开店，测完删掉
        jdbc.update("DELETE FROM lm_region");
    }

    // ---------- 凑单 ----------

    @Test @Order(1)
    void groupBuySuccess() throws Exception {
        String high = fail(post("/api/merchant/group-buys"), merchant, Map.of(
                "productId", pFert, "groupPrice", 120, "targetQty", 5, "days", 3), 400);
        assertTrue(high.contains("比原价"), high);

        JsonNode g = ok(post("/api/merchant/group-buys"), merchant, Map.of(
                "productId", pFert, "groupPrice", 85, "targetQty", 5, "days", 3, "limitPerUser", 3, "endWhenReached", true));
        long gid = id(g);
        assertEquals("ONGOING", g.get("status").asText());
        fail(post("/api/merchant/group-buys"), merchant, Map.of("productId", pFert, "groupPrice", 80, "targetQty", 5, "days", 3), 400);

        // 店铺页和附近能看到
        JsonNode page = ok(get("/api/stores/" + storeId), null, null);
        assertEquals(gid, page.get("groupBuys").get(0).get("id").asLong());
        assertTrue(ok(get("/api/group-buys/nearby").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null).size() >= 1);

        // 预览：按凑单价，只能在线支付；凑单不能货到付款
        JsonNode pv = ok(post("/api/orders/preview"), buyer, Map.of("storeId", storeId, "addressId", nearAddr, "groupBuyId", gid,
                "items", List.of(Map.of("productId", pFert, "quantity", 3))));
        assertEquals(255.0, pv.get("goodsAmount").asDouble());
        pv.get("options").forEach(o -> { if (o.get("available").asBoolean()) assertEquals(List.of("ONLINE"), list(o.get("payMethods"))); });
        fail(post("/api/orders"), buyer, order(pFert, 1, "DELIVERY", "COD", nearAddr, gid), 400);
        String other = fail(post("/api/orders"), buyer, order(pSeed, 1, "DELIVERY", "ONLINE", nearAddr, gid), 400);
        assertTrue(other.contains("只能买这一个商品"), other);

        long o1 = orderAndPay(buyer, order(pFert, 3, "DELIVERY", "ONLINE", nearAddr, gid));
        assertEquals("GROUP_WAITING", status(buyer, o1));
        String limit = fail(post("/api/orders"), buyer, order(pFert, 1, "DELIVERY", "ONLINE", nearAddr, gid), 400);
        assertTrue(limit.contains("每人最多"), limit);
        // 待成团的订单商家订单列表里看不到
        assertFalse(ok(get("/api/merchant/orders").param("tab", "NEW"), merchant, null).toString().contains("\"id\":" + o1 + ","));

        long o2 = orderAndPay(buyer2, order(pFert, 2, "PICKUP", "ONLINE", null, gid));
        JsonNode detail = ok(get("/api/group-buys/" + gid), buyer, null);
        assertEquals(5, detail.get("currentQty").asInt());
        assertEquals(3, detail.get("myQuantity").asInt());
        assertEquals(2, detail.get("members").size());
        assertTrue(detail.get("members").get(0).get("nickname").asText().endsWith("*"));

        // 凑够即截止 → 定时任务成团
        assertTrue(groupBuyService.settleDue() >= 1);
        assertEquals("SUCCESS", ok(get("/api/group-buys/" + gid), null, null).get("status").asText());
        assertEquals("PENDING_ACCEPT", status(buyer, o1));
        assertEquals("PENDING_ACCEPT", status(buyer2, o2));
        assertEquals(85.0, ok(get("/api/orders/" + o1), buyer, null).get("items").get(0).get("price").asDouble());
        // 成团订单不受 30 分钟未接单限制
        jdbc.update("UPDATE lm_order SET paid_at = DATEADD('MINUTE', -60, CURRENT_TIMESTAMP) WHERE id = ?", o1);
        orderService.cancelUnaccepted(30);
        assertEquals("PENDING_ACCEPT", status(buyer, o1));
    }

    @Test @Order(2)
    void groupBuyNotEnough() throws Exception {
        long gid = id(ok(post("/api/merchant/group-buys"), merchant, Map.of(
                "productId", pSeed, "groupPrice", 40, "targetQty", 10, "days", 1)));
        long o1 = orderAndPay(buyer, order(pSeed, 2, "DELIVERY", "ONLINE", nearAddr, gid));
        // 退出凑单：退款并扣回数量
        JsonNode quit = ok(post("/api/orders/" + o1 + "/cancel"), buyer, Map.of());
        assertEquals("CANCELLED", quit.get("status").asText());
        assertEquals(0, ok(get("/api/group-buys/" + gid), null, null).get("currentQty").asInt());

        long o2 = orderAndPay(buyer, order(pSeed, 1, "EXPRESS", "ONLINE", nearAddr, gid));
        jdbc.update("UPDATE lm_group_buy SET end_at = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE id = ?", gid);
        groupBuyService.settleDue();
        JsonNode g = ok(get("/api/group-buys/" + gid), null, null);
        assertEquals("WAIT_DECISION", g.get("status").asText());
        assertNotNull(g.get("decisionDeadline"));

        fail(post("/api/merchant/group-buys/" + gid + "/decide"), merchant, Map.of("action", "MAYBE"), 400);
        JsonNode decided = ok(post("/api/merchant/group-buys/" + gid + "/decide"), merchant, Map.of("action", "SHIP"));
        assertEquals("SUCCESS", decided.get("status").asText());
        assertEquals("PENDING_SHIP", status(buyer, o2)); // 快递订单成团后待发货

        // 再开一个：没凑够、老板不决定 → 24 小时后自动退款
        long gid2 = id(ok(post("/api/merchant/group-buys"), merchant, Map.of(
                "productId", pSeed, "groupPrice", 45, "targetQty", 10, "days", 1)));
        long o3 = orderAndPay(buyer2, order(pSeed, 1, "PICKUP", "ONLINE", null, gid2));
        ok(post("/api/merchant/group-buys/" + gid2 + "/close"), merchant, null);
        jdbc.update("UPDATE lm_group_buy SET decision_deadline = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE id = ?", gid2);
        assertEquals(1, groupBuyService.decisionTimeout());
        assertEquals("FAILED", ok(get("/api/group-buys/" + gid2), null, null).get("status").asText());
        assertEquals("CANCELLED", status(buyer2, o3));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lm_refund WHERE order_id = ?", Integer.class, o3));
    }

    // ---------- 评价、再来一单 ----------

    @Test @Order(3)
    void reviewAndReorder() throws Exception {
        long oid = completedOnlineOrder(buyer, nearAddr);
        JsonNode done = ok(get("/api/orders/" + oid), buyer, null);
        assertTrue(list(done.get("buyerActions")).containsAll(List.of("REVIEW", "REORDER")));

        fail(post("/api/orders/" + oid + "/review"), buyer, Map.of("score", 6), 400);
        String bad = fail(post("/api/orders/" + oid + "/review"), buyer, Map.of("score", 4, "content", "这是" + WxApi.MOCK_BANNED_WORD), 400);
        assertTrue(bad.contains("不合适"), bad);
        JsonNode r = ok(post("/api/orders/" + oid + "/review"), buyer, Map.of("score", 4, "content", "送得快"));
        fail(post("/api/orders/" + oid + "/review"), buyer, Map.of("score", 5), 400);
        assertFalse(list(ok(get("/api/orders/" + oid), buyer, null).get("buyerActions")).contains("REVIEW"));

        JsonNode page = ok(get("/api/stores/" + storeId), null, null);
        assertEquals(1, page.get("reviewSummary").get("count").asInt());
        assertEquals(4.0, page.get("reviewSummary").get("rating").asDouble());

        JsonNode replied = ok(post("/api/merchant/reviews/" + id(r) + "/reply"), merchant, Map.of("reply", "谢谢支持"));
        assertEquals("谢谢支持", replied.get("reply").asText());
        JsonNode reviews = ok(get("/api/stores/" + storeId + "/reviews"), null, null);
        assertEquals(1, reviews.get("total").asInt());
        assertEquals("送得快", reviews.get("records").get(0).get("content").asText());

        JsonNode reorder = ok(get("/api/orders/" + oid + "/reorder"), buyer, null);
        assertEquals(storeId, reorder.get("storeId").asLong());
        assertEquals(1, reorder.get("quantities").get(String.valueOf(pFert)).asInt());
    }

    // ---------- 求购 ----------

    @Test @Order(4)
    void wanted() throws Exception {
        JsonNode w = ok(post("/api/wanted"), buyer, Map.of("keyword", "地膜 1.2 米", "note", "要 10 卷",
                "contactPhone", "13600000001", "lat", NEAR_LAT, "lng", NEAR_LNG));
        assertEquals("OPEN", w.get("status").asText());
        JsonNode nearby = ok(get("/api/merchant/wanted"), merchant, null);
        assertTrue(nearby.toString().contains("地膜 1.2 米"), nearby.toString());

        fail(post("/api/merchant/wanted/" + id(w) + "/reply"), merchant, Map.of("price", 30), 400);
        ok(post("/api/merchant/wanted/" + id(w) + "/reply"), merchant, Map.of("price", 30, "note", "有货，明天送", "productId", pSeed));
        ok(post("/api/merchant/wanted/" + id(w) + "/reply"), merchant, Map.of("price", 28, "note", "便宜点给你")); // 改报价不重复
        JsonNode mine = ok(get("/api/wanted/mine"), buyer, null);
        JsonNode replies = mine.get(0).get("replies");
        assertEquals(1, replies.size());
        assertEquals(28.0, replies.get(0).get("price").asDouble());
        assertEquals("丰收农资", replies.get(0).get("storeName").asText());

        ok(post("/api/wanted/" + id(w) + "/close"), buyer, null);
        assertEquals("CLOSED", ok(get("/api/wanted/mine"), buyer, null).get(0).get("status").asText());
    }

    // ---------- 赊账本、证件、经营数据 ----------

    @Test @Order(5)
    void creditLicensesStats() throws Exception {
        JsonNode c = ok(post("/api/merchant/credit/customers"), merchant, Map.of("name", "李大爷", "village", "前进村"));
        ok(post("/api/merchant/credit/customers/" + id(c) + "/records"), merchant, Map.of("type", "CREDIT", "amount", 300, "note", "复合肥 3 袋"));
        JsonNode after = ok(post("/api/merchant/credit/customers/" + id(c) + "/records"), merchant, Map.of("type", "REPAY", "amount", 100));
        assertEquals(200.0, after.get("balance").asDouble());
        JsonNode customers = ok(get("/api/merchant/credit/customers"), merchant, null);
        assertEquals(200.0, customers.get("totalOwed").asDouble());
        assertEquals(2, ok(get("/api/merchant/credit/customers/" + id(c) + "/records"), merchant, null).get("total").asInt());
        // 别的店看不到
        fail(get("/api/merchant/credit/customers/" + id(c) + "/records"), login("v2-other-merchant"), null, 404);

        JsonNode lic = ok(post("/api/merchant/licenses"), merchant, Map.of("type", "PESTICIDE", "image", "http://test.local/files/l.jpg"));
        assertEquals("农药经营许可证", lic.get("typeLabel").asText());
        assertEquals("PENDING", lic.get("status").asText());

        JsonNode stats = ok(get("/api/merchant/stats"), merchant, null);
        assertTrue(stats.get("monthOrders").asInt() >= 1);
        assertTrue(stats.get("monthAmount").asDouble() > 0);
        assertTrue(stats.get("topProducts").size() >= 1);
    }

    // ---------- 开户、解冻、提现 ----------

    @Test @Order(6)
    void onboardingAndWithdraw() throws Exception {
        assertEquals("NONE", ok(get("/api/merchant/account"), merchant, null).get("status").asText());
        String front = upload("/api/merchant/onboarding/image", merchant, PNG).get("imageId").asText();
        String back = upload("/api/merchant/onboarding/image", merchant, PNG).get("imageId").asText();
        String frontPath = jdbc.queryForObject("SELECT path FROM lm_private_image WHERE id = ?", String.class, front);
        assertTrue(Files.exists(Path.of(frontPath)));

        Map<String, Object> req = new HashMap<>(Map.of("idCardFrontImageId", front, "idCardBackImageId", back,
                "idName", "张三丰", "idNumber", "410101198001011230", "idValidFrom", "2015-01-01", "idValidTo", "长期",
                "bankName", "中国农业银行", "bankCityCode", "410100", "bankAccount", "6228480012345678", "mobile", "13700000001"));
        if (IdCardUtil.valid("410101198001011230")) req.put("idNumber", "410101198001011231");
        String badId = fail(post("/api/merchant/onboarding"), merchant, req, 400);
        assertTrue(badId.contains("身份证号"), badId);

        req.put("idNumber", validIdCard());
        JsonNode acc = ok(post("/api/merchant/onboarding"), merchant, req);
        assertEquals("APPROVED", acc.get("status").asText()); // 模拟开户立即成功
        assertEquals("**** 5678", acc.get("bankCardMasked").asText());
        assertEquals("张**", acc.get("idNameMasked").asText());
        // 证件照已删除，身份证号没有存
        assertFalse(Files.exists(Path.of(frontPath)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lm_private_image", Integer.class));
        String allAccount = jdbc.queryForList("SELECT * FROM lm_merchant_account").toString();
        assertFalse(allAccount.contains("6228480012345678"));
        assertFalse(allAccount.contains(validIdCard()));
        fail(post("/api/merchant/onboarding"), merchant, req, 400); // 不能重复提交

        // 完成订单 → 待解冻 → 解冻 → 可提现
        long oid = completedOnlineOrder(buyer, nearAddr);
        assertEquals("PENDING_RELEASE", jdbc.queryForObject("SELECT status FROM lm_settlement WHERE order_id = ?", String.class, oid));
        double pendingBefore = ok(get("/api/merchant/balance"), merchant, null).get("pending").asDouble();
        assertTrue(pendingBefore >= 100);
        fundService.releasePending();
        assertEquals("RELEASED", jdbc.queryForObject("SELECT status FROM lm_settlement WHERE order_id = ?", String.class, oid));
        double available = ok(get("/api/merchant/balance"), merchant, null).get("available").asDouble();
        assertTrue(available >= 100, "可提现 " + available);

        String tooMuch = fail(post("/api/merchant/withdraw"), merchant, Map.of("amount", available + 1), 400);
        assertTrue(tooMuch.contains("最多能提"), tooMuch);
        JsonNode w = ok(post("/api/merchant/withdraw"), merchant, Map.of("amount", 50));
        assertEquals("SUCCESS", w.get("status").asText());
        assertEquals(available - 50, ok(get("/api/merchant/balance"), merchant, null).get("available").asDouble(), 0.001);
        assertEquals(1, ok(get("/api/merchant/withdrawals"), merchant, null).get("total").asInt());
        JsonNode settles = ok(get("/api/merchant/settlements"), merchant, null);
        assertTrue(settles.get("records").get(0).has("orderNo"));
    }

    // ---------- 通知、催单 ----------

    @Test @Order(7)
    void notificationsAndRemind() throws Exception {
        long oid = id(ok(post("/api/orders"), buyer, order(pFert, 1, "DELIVERY", "COD", nearAddr, null)));
        // 通知在事务提交后异步发送
        int logs = 0;
        for (int i = 0; i < 50 && logs < 2; i++) {
            Thread.sleep(100);
            logs = jdbc.queryForObject("SELECT COUNT(*) FROM lm_notify_log WHERE order_id = ? AND channel IN ('VOICE','SMS')", Integer.class, oid);
        }
        assertEquals(2, logs, "新订单应给老板打电话 + 发短信");

        jdbc.update("UPDATE lm_order SET created_at = DATEADD('MINUTE', -11, CURRENT_TIMESTAMP) WHERE id = ?", oid);
        assertEquals(1, orderService.remindUnaccepted(10));
        assertEquals(0, orderService.remindUnaccepted(10)); // 只催一次
        int voice = 0;
        for (int i = 0; i < 50 && voice < 2; i++) {
            Thread.sleep(100);
            voice = jdbc.queryForObject("SELECT COUNT(*) FROM lm_notify_log WHERE order_id = ? AND channel = 'VOICE'", Integer.class, oid);
        }
        assertEquals(2, voice);
        ok(post("/api/merchant/orders/" + oid + "/accept"), merchant, Map.of());
    }

    // ---------- 拼音搜索、拍照识别、小程序码、轮播配置 ----------

    @Test @Order(8)
    void searchOcrQrcode() throws Exception {
        JsonNode r = ok(get("/api/search").param("keyword", "yumizhongzi").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(1, r.get("total").asInt(), r.toString());
        JsonNode r2 = ok(get("/api/search").param("keyword", "ymzz").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(1, r2.get("total").asInt());
        // 同义词：农户说“苞米”，包装上写“玉米”
        assertEquals(1, ok(get("/api/search").param("keyword", "苞米").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null)
                .get("total").asInt());
        ok(get("/api/search").param("keyword", "无人售卖的东西").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);

        // 拍包装识别（模拟 OCR 固定返回一袋“演示牌复合肥”的包装文字）
        JsonNode ocr = upload("/api/ocr/product", merchant, PNG);
        assertEquals("复合肥 15-15-15", ocr.get("keyword").asText());
        assertEquals("50kg", ocr.get("spec").asText());
        assertEquals("演示牌", ocr.get("brand").asText());
        assertEquals("6901234567892", ocr.get("barcode").asText());
        assertEquals("农肥(2024)准字0001号", ocr.get("regNo").asText());
        assertEquals("演示肥业有限公司", ocr.get("manufacturer").asText());
        // 按识别结果上架：没有这一款就自动建档，再识别就能认出来
        Map<String, Object> bag = new HashMap<>(Map.of("images", List.of("http://test.local/files/bag.jpg"), "price", 158,
                "name", ocr.get("keyword").asText(), "brand", "演示牌", "spec", "50kg",
                "barcode", ocr.get("barcode").asText(), "regNo", ocr.get("regNo").asText()));
        JsonNode listed = ok(post("/api/merchant/products"), merchant, bag);
        assertNotNull(listed.get("standardProductId"));
        JsonNode again = upload("/api/ocr/product", merchant, PNG);
        assertEquals(listed.get("standardProductId").asLong(), again.get("match").get("id").asLong());
        // 只有登记证号 + 规格（没条码）也能认出同一款
        JsonNode byReg = ok(get("/api/skus/lookup").param("regNo", "农肥（2024）准字 0001号").param("spec", "50千克"), null, null);
        assertEquals(listed.get("standardProductId").asLong(), byReg.get("id").asLong());
        // 老板说“不是这一款”：编辑时去掉原来挂的款
        Map<String, Object> notThis = new HashMap<>(bag);
        notThis.remove("barcode");
        notThis.remove("regNo");
        notThis.put("notThisSku", true);
        JsonNode unlinked = ok(put("/api/merchant/products/" + listed.get("id").asLong()), merchant, notThis);
        assertFalse(unlinked.has("standardProductId"), unlinked.toString());
        ok(put("/api/merchant/products/" + listed.get("id").asLong()), merchant, bag); // 挂回去，后面精准找货要用
        // 老板确认了一款（这款只有条码），包装上还认出了登记证号 → 补到这一款上
        String code = "6905000000040";
        long skuOnlyBarcode = ok(post("/api/merchant/products"), merchant, new HashMap<>(Map.of("images", List.of("http://test.local/files/x.jpg"),
                "price", 20, "name", "杀虫剂样品", "spec", "100g", "barcode", code))).get("standardProductId").asLong();
        ok(post("/api/merchant/products"), merchant, new HashMap<>(Map.of(
                "images", List.of("http://test.local/files/x.jpg"), "price", 21, "standardProductId", skuOnlyBarcode,
                "regNo", "PD20249999", "spec", "100g")));
        assertEquals(skuOnlyBarcode, ok(get("/api/skus/lookup").param("regNo", "PD20249999").param("spec", "100g"), null, null).get("id").asLong());
        // 精准找货：搜登记证号
        JsonNode exact = ok(get("/api/search").param("keyword", "农肥(2024)准字0001号").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(1, exact.get("exact").size());
        assertTrue(exact.get("exact").get(0).get("offers").size() >= 1);

        MockHttpServletResponse png = mvc.perform(get("/api/qrcode").param("type", "store").param("id", "" + storeId)).andReturn().getResponse();
        assertEquals("image/png", png.getContentType());
        byte[] b = png.getContentAsByteArray();
        assertEquals((byte) 0x89, b[0]);
        assertEquals('P', b[1]);
        assertEquals(404, call(get("/api/qrcode").param("type", "store").param("id", "999999"), null, null).get("code").asInt());

        assertTrue(ok(get("/api/app-config"), null, null).isObject());
    }

    // ---------- 运营后台 ----------

    @Test @Order(9)
    void admin() throws Exception {
        fail(post("/api/admin/login"), null, Map.of("username", "admin", "password", "wrong"), 400);
        // 用户 token 不能进后台，后台 token 不能当用户用
        fail(get("/api/admin/dashboard"), buyer, null, 401);
        fail(get("/api/orders"), adminToken, null, 401);

        JsonNode dash = ok(get("/api/admin/dashboard"), adminToken, null);
        assertEquals(14, dash.get("daily").size());
        assertTrue(dash.get("noResultKeywords").toString().contains("无人售卖的东西"), dash.toString());
        assertTrue(dash.get("pendingLicenses").asInt() >= 1);

        // 店铺：停用后附近看不到，恢复后又能看到
        JsonNode stores = ok(get("/api/admin/stores").param("keyword", "丰收"), adminToken, null);
        assertEquals(1, stores.get("total").asInt());
        assertTrue(stores.get("records").get(0).has("ownerNickname"));
        fail(post("/api/admin/stores/" + storeId + "/ban"), adminToken, Map.of(), 400);
        ok(post("/api/admin/stores/" + storeId + "/ban"), adminToken, Map.of("reason", "测试停用"));
        assertFalse(ok(get("/api/stores/nearby").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null)
                .toString().contains("丰收农资"));
        ok(post("/api/admin/stores/" + storeId + "/unban"), adminToken, null);
        JsonNode detail = ok(get("/api/admin/stores/" + storeId), adminToken, null);
        assertEquals("APPROVED", detail.get("account").get("status").asText());
        assertFalse(detail.get("owner").has("openId"));

        // 证件审核
        long licId = ok(get("/api/admin/licenses").param("status", "PENDING"), adminToken, null).get("records").get(0).get("id").asLong();
        ok(post("/api/admin/licenses/" + licId + "/approve"), adminToken, null);
        assertEquals("APPROVED", ok(get("/api/merchant/licenses"), merchant, null).get(0).get("status").asText());

        // 商品：下架后商家不能直接上架；归一后搜索合并
        ok(post("/api/admin/products/" + pSeed + "/off-shelf"), adminToken, Map.of("reason", "图片不清楚"));
        String relist = fail(post("/api/merchant/products/" + pSeed + "/on-shelf"), merchant, null, 400);
        assertTrue(relist.contains("图片不清楚"), relist);
        JsonNode pending = ok(get("/api/admin/products").param("normalize", "PENDING").param("storeId", "" + storeId), adminToken, null);
        assertTrue(pending.get("total").asInt() >= 1);
        JsonNode std = ok(post("/api/admin/standard-products"), adminToken, Map.of("categoryId", 1, "name", "复合肥特供", "spec", "40kg", "keywords", "特供肥"));
        assertEquals("VERIFIED", std.get("status").asText());
        ok(post("/api/admin/products/" + pFert + "/link"), adminToken, Map.of("standardProductId", id(std)));
        assertTrue(ok(get("/api/search").param("keyword", "特供肥").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null)
                .get("total").asInt() >= 1);
        fail(post("/api/admin/standard-products"), adminToken, Map.of("categoryId", 1, "name", "重复", "barcode", "6901234567892"), 400);
        // 待核实的款：核实；重复建的款：合并
        JsonNode pendingSkus = ok(get("/api/admin/standard-products").param("status", "PENDING"), adminToken, null);
        assertTrue(pendingSkus.get("total").asInt() >= 1);
        assertTrue(dash.has("pendingSkus"));
        long pendingId = pendingSkus.get("records").get(0).get("id").asLong();
        ok(post("/api/admin/standard-products/" + pendingId + "/verify"), adminToken, null);
        JsonNode dupSku = ok(post("/api/admin/standard-products"), adminToken, Map.of("categoryId", 1, "name", "复合肥特供（重复）", "regNo", "LS20240099", "spec", "40kg"));
        ok(post("/api/admin/products/" + pSeed + "/link"), adminToken, Map.of("standardProductId", id(dupSku)));
        ok(post("/api/admin/standard-products/" + id(dupSku) + "/merge"), adminToken, Map.of("intoId", id(std)));
        assertEquals(id(std), jdbc.queryForObject("SELECT standard_product_id FROM lm_product WHERE id = ?", Long.class, pSeed));
        assertEquals("LS20240099", jdbc.queryForObject("SELECT reg_no FROM lm_standard_product WHERE id = ?", String.class, id(std)));
        // 同义词
        JsonNode word = ok(post("/api/admin/search-words"), adminToken, Map.of("word", "生根粉", "synonyms", "生根剂，sgf"));
        assertEquals("生根剂 sgf", word.get("synonyms").asText());
        assertTrue(ok(get("/api/admin/search-words").param("keyword", "sgf"), adminToken, null).size() >= 1);
        String used = fail(delete("/api/admin/standard-products/" + id(std)), adminToken, null, 400);
        assertTrue(used.contains("不能删除"), used);

        // 仲裁取消（已付款 → 退款）
        long oid = orderAndPay(buyer, order(pFert, 1, "DELIVERY", "ONLINE", nearAddr, null));
        ok(post("/api/merchant/orders/" + oid + "/accept"), merchant, Map.of());
        ok(post("/api/merchant/orders/" + oid + "/dispatch"), merchant, Map.of());
        JsonNode cancelled = ok(post("/api/admin/orders/" + oid + "/force-cancel"), adminToken, Map.of("reason", "买家投诉货不对"));
        assertEquals("CANCELLED", cancelled.get("status").asText());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM lm_refund WHERE order_id = ?", Integer.class, oid) >= 1);
        assertEquals(1, ok(get("/api/admin/orders").param("keyword", cancelled.get("orderNo").asText()), adminToken, null).get("total").asInt());

        // 开通区域：区域外不能开店
        ok(post("/api/admin/regions"), adminToken, Map.of("name", "试点县", "lat", 34.0, "lng", 113.0, "radiusKm", 30, "enabled", true));
        String outside = fail(post("/api/merchant/store"), login("v2-outside-merchant"), Map.of(
                "name", "外地店", "phone", "13700000009", "lat", 40.0, "lng", 116.0), 400);
        assertTrue(outside.contains("还没开通"), outside);

        // 轮播图
        ok(post("/api/admin/banners"), adminToken, Map.of("image", "http://test.local/files/b.jpg", "title", "春耕备肥", "link", "/pages/index/index"));
        assertTrue(ok(get("/api/banners"), null, null).toString().contains("春耕备肥"));

        // 角色：运营人员不能管理管理员
        ok(post("/api/admin/admins"), adminToken, Map.of("username", "op1", "password", "Op123456", "name", "小王", "role", "OPERATOR"));
        String opToken = ok(post("/api/admin/login"), null, Map.of("username", "op1", "password", "Op123456")).get("token").asText();
        fail(get("/api/admin/admins"), opToken, null, 403);
        ok(get("/api/admin/stores"), opToken, null);
        fail(post("/api/admin/admins"), adminToken, Map.of("username", "op2", "password", "123", "name", "弱", "role", "OPERATOR"), 400);

        assertTrue(ok(get("/api/admin/logs"), adminToken, null).get("total").asInt() >= 5);
        assertTrue(ok(get("/api/admin/notify-logs"), adminToken, null).get("total").asInt() >= 1);
        ok(get("/api/admin/settlements"), adminToken, null);
        ok(get("/api/admin/withdrawals"), adminToken, null);
        ok(get("/api/admin/refunds"), adminToken, null);
        ok(get("/api/admin/group-buys"), adminToken, null);
        ok(get("/api/admin/reviews"), adminToken, null);
        ok(get("/api/admin/wanted").param("status", "CLOSED"), adminToken, null);
        ok(get("/api/admin/users").param("keyword", "农友"), adminToken, null);
        ok(get("/api/admin/categories"), adminToken, null);
        assertTrue(upload("/api/admin/files", adminToken, PNG).get("url").asText().startsWith("http://test.local/files/"));
    }
}
