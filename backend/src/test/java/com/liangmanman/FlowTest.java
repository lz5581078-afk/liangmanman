package com.liangmanman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liangmanman.service.OrderService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 端到端：开店 → 上架（本地 / 快递）→ 附近 → 搜索 → 三种履约方式下单 → 商家处理 → 完成 / 取消退款 → 超时任务。
 * 使用真实 schema.sql，在 H2（MySQL 兼容模式）上运行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowTest {

    // 店铺位置（某县城），买家在 1.4 公里外，远方买家在约 140 公里外
    static final double STORE_LAT = 34.0, STORE_LNG = 113.0;
    static final double NEAR_LAT = 34.01, NEAR_LNG = 113.01;
    static final double FAR_LAT = 35.0, FAR_LNG = 114.0;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orderService;

    String merchant, merchant2, buyer;
    static final String BARCODE = "6900000000014";
    long storeId, std复合肥;
    long pLocal, pBoth, pExpress;
    long nearAddr, farAddr;

    // ---------- 工具 ----------

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        String s = mvc.perform(req).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return om.readTree(s);
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

    String login(String code) throws Exception {
        return ok(post("/api/auth/wx-login"), null, Map.of("code", code)).get("token").asText();
    }

    Map<String, Object> product(String name, double price, boolean local, boolean express, Double expressFee, Integer stock, Long std) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("images", List.of("http://test.local/files/a.jpg"));
        m.put("price", price);
        if (name != null) m.put("name", name);
        m.put("localDelivery", local);
        m.put("expressDelivery", express);
        if (expressFee != null) m.put("expressFee", expressFee);
        if (stock != null) m.put("stock", stock);
        if (std != null) m.put("standardProductId", std);
        return m;
    }

    Map<String, Object> order(long productId, int qty, String fulfillment, String pay, Long addressId) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("storeId", storeId);
        m.put("items", List.of(Map.of("productId", productId, "quantity", qty)));
        m.put("fulfillment", fulfillment);
        m.put("payMethod", pay);
        if (addressId != null) m.put("addressId", addressId);
        return m;
    }

    long id(JsonNode n) {
        return n.get("id").asLong();
    }

    List<String> list(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(x -> out.add(x.asText()));
        return out;
    }

    String mAct(long orderId, String action, Object body) throws Exception {
        return ok(post("/api/merchant/orders/" + orderId + "/" + action), merchant, body).get("status").asText();
    }

    // ---------- 准备：开店、上架 ----------

    @BeforeAll
    void setUp() throws Exception {
        merchant = login("merchant-1");
        merchant2 = login("merchant-2");
        buyer = login("buyer-1");

        JsonNode store = ok(post("/api/merchant/store"), merchant, Map.of(
                "name", "老王农资店", "phone", "13800000001", "lat", STORE_LAT, "lng", STORE_LNG, "town", "城关镇"));
        storeId = id(store);
        assertEquals("APPROVED", store.get("status").asText());
        ok(put("/api/merchant/store"), merchant, Map.of(
                "deliveryRadiusKm", 5, "minOrderAmount", 50, "deliveryFee", 5, "codEnabled", true));
        ok(post("/api/merchant/store"), merchant2, Map.of(
                "name", "李家农资", "phone", "13800000002", "lat", STORE_LAT + 0.05, "lng", STORE_LNG));

        // 李家先上架一袋带条码的复合肥：商品库里没有这一款，自动建档（待核实）
        Map<String, Object> first = product("复合肥 15-15-15", 160, true, false, null, null, null);
        first.put("brand", "演示牌");
        first.put("spec", "50 千克");
        first.put("barcode", BARCODE);
        JsonNode p2 = ok(post("/api/merchant/products"), merchant2, first);
        assertEquals("50kg", p2.get("spec").asText());
        JsonNode sku = ok(get("/api/skus/lookup").param("barcode", BARCODE), null, null);
        assertEquals("PENDING", sku.get("status").asText());
        assertEquals("演示牌 复合肥 15-15-15 50kg", sku.get("displayName").asText());
        std复合肥 = sku.get("id").asLong();
        assertEquals(std复合肥, p2.get("standardProductId").asLong());

        Map<String, Object> local = product(null, 150, true, false, null, 10, std复合肥);
        local.put("categoryId", 1);
        pLocal = id(ok(post("/api/merchant/products"), merchant, local));
        pBoth = id(ok(post("/api/merchant/products"), merchant, product("有机肥特供", 80, true, true, 20.0, null, null)));
        pExpress = id(ok(post("/api/merchant/products"), merchant, product("电动喷雾器", 300, false, true, 0.0, null, null)));

        nearAddr = id(ok(post("/api/addresses"), buyer, Map.of(
                "contactName", "张大哥", "phone", "13900000001", "lat", NEAR_LAT, "lng", NEAR_LNG,
                "town", "城关镇", "village", "东王村", "detail", "村东头小卖部旁")));
        farAddr = id(ok(post("/api/addresses"), buyer, Map.of(
                "contactName", "张大哥", "phone", "13900000001", "lat", FAR_LAT, "lng", FAR_LNG,
                "village", "外省某村")));
    }

    // ---------- 上架 ----------

    @Test @Order(1)
    void publishRules() throws Exception {
        // 名称不填时从标准品带出
        JsonNode p = ok(get("/api/products/" + pLocal), null, null).get("product");
        assertTrue(p.get("name").asText().contains("复合肥"), p.toString());
        assertTrue(p.get("localDelivery").asBoolean());
        assertFalse(p.get("expressDelivery").asBoolean());

        // 两种配送方式都不选：拒绝
        String msg = fail(post("/api/merchant/products"), merchant, product("x", 10, false, false, null, null, null), 400);
        assertTrue(msg.contains("至少选一项"), msg);
        // 没照片：拒绝
        Map<String, Object> noImg = product("x", 10, true, false, null, null, null);
        noImg.put("images", List.of());
        fail(post("/api/merchant/products"), merchant, noImg, 400);
        // 没开店的人不能上架
        fail(post("/api/merchant/products"), buyer, product("x", 10, true, false, null, null, null), 404);

        // 编辑：库存改为不限量
        Map<String, Object> edit = product("有机肥特供", 85, true, true, 20.0, null, null);
        JsonNode edited = ok(put("/api/merchant/products/" + pBoth), merchant, edit);
        assertEquals(85.0, edited.get("price").asDouble());
        assertTrue(edited.get("stock") == null || edited.get("stock").isNull());
    }

    // ---------- 附近 / 店铺 / 搜索 ----------

    @Test @Order(2)
    void nearbyAndStorePage() throws Exception {
        JsonNode stores = ok(get("/api/stores/nearby").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(2, stores.size());
        JsonNode first = stores.get(0);
        assertEquals(storeId, first.get("id").asLong(), "最近的店排第一");
        assertTrue(first.get("canDeliver").asBoolean());
        assertTrue(first.get("distanceKm").asDouble() < 2);
        // 前端依赖的字段名（布尔字段容易被 Jackson 改名）
        // 注：值为 null 的字段（如未上传的 coverImage）按配置不输出，前端按“没有”处理
        for (String field : List.of("isOpen", "pickupEnabled", "deliveryEnabled", "codEnabled", "minOrderAmount", "deliveryFee")) {
            assertTrue(first.has(field), "附近店铺缺少字段 " + field + "：" + first);
        }
        assertTrue(first.get("isOpen").asBoolean());
        JsonNode mine = ok(get("/api/merchant/store"), merchant, null);
        for (String field : List.of("isOpen", "deliveryEnabled", "pickupEnabled", "codEnabled", "deliveryRadiusKm", "status")) {
            assertTrue(mine.has(field), "我的店铺缺少字段 " + field + "：" + mine);
        }
        JsonNode addrs = ok(get("/api/addresses"), buyer, null);
        assertTrue(addrs.get(0).has("isDefault"), addrs.toString());
        assertTrue(addrs.get(0).get("isDefault").asBoolean(), "第一个地址自动设为默认");

        // 按分类筛选：化肥（分类 1）只有老王的店有
        JsonNode fert = ok(get("/api/stores/nearby").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG)
                .param("categoryId", "1"), null, null);
        assertEquals(1, fert.size());

        JsonNode page = ok(get("/api/stores/" + storeId).param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        int count = 0;
        for (JsonNode g : page.get("groups")) count += g.get("products").size();
        assertEquals(3, count);
    }

    @Test @Order(3)
    void search() throws Exception {
        // 平铺：两家店的复合肥各一条
        JsonNode r = ok(get("/api/search").param("keyword", "复合肥").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(2, r.get("total").asInt(), r.toString());
        assertEquals(0, r.get("exact").size(), "普通搜索不给比价");
        JsonNode item = r.get("records").get(0);
        for (String field : List.of("productId", "name", "brand", "spec", "image", "price", "unit", "sales", "storeId", "storeName",
                "storeOpen", "distanceKm", "canDeliver", "canPickup", "express", "expressFee", "skuId", "sameCount")) {
            assertTrue(item.has(field), "搜索结果缺少字段 " + field + "：" + item);
        }
        assertEquals(storeId, item.get("storeId").asLong(), "综合排序：能送到的近店在前");
        assertTrue(item.get("canDeliver").asBoolean());
        assertFalse(item.get("express").asBoolean());
        assertEquals(std复合肥, item.get("skuId").asLong());
        assertEquals(2, item.get("sameCount").asInt());
        assertEquals("演示牌", item.get("brand").asText(), "挂到款上，品牌从款带出");

        // 排序、筛选、翻页
        JsonNode desc = ok(get("/api/search").param("keyword", "复合肥").param("sort", "PRICE_DESC")
                .param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(160.0, desc.get("records").get(0).get("price").asDouble());
        JsonNode exp = ok(get("/api/search").param("keyword", "复合肥").param("delivery", "EXPRESS")
                .param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(0, exp.get("total").asInt());
        JsonNode p2 = ok(get("/api/search").param("keyword", "复合肥").param("size", "1").param("page", "2")
                .param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(1, p2.get("records").size());
        assertEquals(2, p2.get("total").asInt());

        // 多个词：品牌 + 名称 + 规格
        JsonNode multi = ok(get("/api/search").param("keyword", "演示牌 复合肥 50kg").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(2, multi.get("total").asInt(), multi.toString());

        // 同义词 / 拼音首字母
        JsonNode r2 = ok(get("/api/search").param("keyword", "fhf").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(2, r2.get("total").asInt());

        // 精准找货：搜条码 → 给出这一款的比价
        JsonNode ex = ok(get("/api/search").param("keyword", BARCODE).param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(1, ex.get("exact").size());
        JsonNode cmp = ex.get("exact").get(0);
        assertEquals(std复合肥, cmp.get("sku").get("id").asLong());
        assertEquals(2, cmp.get("offers").size());
        assertEquals(150.0, cmp.get("minPrice").asDouble());

        // 同款比价接口
        JsonNode offers = ok(get("/api/skus/" + std复合肥 + "/offers").param("lat", "" + NEAR_LAT).param("lng", "" + NEAR_LNG), null, null);
        assertEquals(150.0, offers.get("offers").get(0).get("price").asDouble());
        assertEquals(160.0, offers.get("offers").get(1).get("price").asDouble());
        JsonNode detail = ok(get("/api/products/" + pLocal), null, null);
        assertEquals(std复合肥, detail.get("sku").get("id").asLong());
        assertEquals(1, detail.get("otherStoresCount").asInt());

        // 远方买家：只支持本地配送的看不到，支持快递的看得到
        JsonNode far1 = ok(get("/api/search").param("keyword", "复合肥").param("lat", "" + FAR_LAT).param("lng", "" + FAR_LNG), null, null);
        assertEquals(0, far1.get("total").asInt());
        JsonNode far2 = ok(get("/api/search").param("keyword", "喷雾器").param("lat", "" + FAR_LAT).param("lng", "" + FAR_LNG), null, null);
        assertEquals(1, far2.get("total").asInt());
        JsonNode farItem = far2.get("records").get(0);
        assertTrue(farItem.get("express").asBoolean());
        assertFalse(farItem.get("canDeliver").asBoolean());
        assertFalse(farItem.get("canPickup").asBoolean());

        fail(get("/api/search").param("keyword", " "), null, null, 400);
    }

    // ---------- 结算预览 ----------

    @Test @Order(4)
    void preview() throws Exception {
        JsonNode pv = ok(post("/api/orders/preview"), buyer, Map.of(
                "storeId", storeId, "addressId", nearAddr,
                "items", List.of(Map.of("productId", pLocal, "quantity", 1))));
        Map<String, JsonNode> opts = new java.util.HashMap<>();
        pv.get("options").forEach(o -> opts.put(o.get("fulfillment").asText(), o));
        assertTrue(opts.get("DELIVERY").get("available").asBoolean());
        assertEquals(5.0, opts.get("DELIVERY").get("fee").asDouble());
        assertEquals(155.0, opts.get("DELIVERY").get("total").asDouble());
        assertTrue(list(opts.get("DELIVERY").get("payMethods")).contains("COD"));
        assertTrue(opts.get("PICKUP").get("available").asBoolean());
        assertFalse(opts.get("EXPRESS").get("available").asBoolean());

        // 远方地址：送货上门不可用（超范围），快递可用且只能在线付
        JsonNode pv2 = ok(post("/api/orders/preview"), buyer, Map.of(
                "storeId", storeId, "addressId", farAddr,
                "items", List.of(Map.of("productId", pBoth, "quantity", 1))));
        pv2.get("options").forEach(o -> opts.put(o.get("fulfillment").asText(), o));
        assertFalse(opts.get("DELIVERY").get("available").asBoolean());
        assertTrue(opts.get("DELIVERY").get("reason").asText().contains("超出配送范围"));
        assertTrue(opts.get("EXPRESS").get("available").asBoolean());
        assertEquals(20.0, opts.get("EXPRESS").get("fee").asDouble());
        assertEquals(List.of("ONLINE"), list(opts.get("EXPRESS").get("payMethods")));
    }

    // ---------- 送货上门 + 货到付款 ----------

    @Test @Order(5)
    void deliveryCod() throws Exception {
        JsonNode o = ok(post("/api/orders"), buyer, order(pLocal, 2, "DELIVERY", "COD", nearAddr));
        long oid = id(o);
        assertEquals("PENDING_ACCEPT", o.get("status").asText());
        assertEquals(305.0, o.get("totalAmount").asDouble());
        assertEquals("东王村", o.get("village").asText());
        assertEquals(List.of("CANCEL"), list(o.get("buyerActions")));

        // 库存 10 → 8
        assertEquals(8, ok(get("/api/products/" + pLocal), null, null).get("product").get("stock").asInt());

        // 商家新订单列表能看到；别的商家看不到
        JsonNode news = ok(get("/api/merchant/orders").param("tab", "NEW"), merchant, null);
        assertTrue(news.toString().contains(o.get("orderNo").asText()));
        fail(post("/api/merchant/orders/" + oid + "/accept"), merchant2, null, 404);

        assertEquals("ACCEPTED", mAct(oid, "accept", null));
        fail(post("/api/merchant/orders/" + oid + "/accept"), merchant, null, 400); // 重复接单

        // 送货清单按村分组
        JsonNode sheet = ok(get("/api/merchant/delivery-sheet"), merchant, null);
        assertTrue(sheet.has("东王村"), sheet.toString());

        assertEquals("DELIVERING", mAct(oid, "dispatch", null));
        JsonNode delivered = ok(post("/api/merchant/orders/" + oid + "/delivered"), merchant, null);
        assertEquals("已送达·待收款", delivered.get("statusLabel").asText());
        assertEquals(List.of("COD_RECEIVED"), list(delivered.get("merchantActions")));

        // 货到付款：买家不能确认收货，要老板确认收款
        fail(post("/api/orders/" + oid + "/confirm"), buyer, null, 400);
        assertEquals("COMPLETED", mAct(oid, "cod_received", null));

        JsonNode detail = ok(get("/api/orders/" + oid), buyer, null);
        assertTrue(detail.get("codReceived").asBoolean());
        assertEquals(5, detail.get("logs").size()); // 下单、接单、出发、送达、收款
        assertEquals(2, ok(get("/api/products/" + pLocal), null, null).get("product").get("sales").asInt());
        // 货到付款不产生平台结算记录
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lm_settlement WHERE order_id = ?", Integer.class, oid));
    }

    @Test @Order(6)
    void deliveryRules() throws Exception {
        String far = fail(post("/api/orders"), buyer, order(pLocal, 1, "DELIVERY", "COD", farAddr), 400);
        assertTrue(far.contains("超出配送范围"), far);

        // 低于起送金额（快递商品 85 元 >= 50；这里改用 1 件有机肥但把起送调高）
        ok(put("/api/merchant/store"), merchant, Map.of("minOrderAmount", 100));
        String min = fail(post("/api/orders"), buyer, order(pBoth, 1, "DELIVERY", "COD", nearAddr), 400);
        assertTrue(min.contains("起送"), min);
        ok(put("/api/merchant/store"), merchant, Map.of("minOrderAmount", 50));

        String stock = fail(post("/api/orders"), buyer, order(pLocal, 100, "DELIVERY", "COD", nearAddr), 400);
        assertTrue(stock.contains("库存不足"), stock);

        String expressOnly = fail(post("/api/orders"), buyer, order(pExpress, 1, "DELIVERY", "COD", nearAddr), 400);
        assertTrue(expressOnly.contains("只支持快递"), expressOnly);

        String noAddr = fail(post("/api/orders"), buyer, order(pLocal, 1, "DELIVERY", "COD", null), 400);
        assertTrue(noAddr.contains("地址"), noAddr);

        // 打烊不接单
        ok(put("/api/merchant/store"), merchant, Map.of("isOpen", false));
        String closed = fail(post("/api/orders"), buyer, order(pLocal, 1, "DELIVERY", "COD", nearAddr), 400);
        assertTrue(closed.contains("打烊"), closed);
        ok(put("/api/merchant/store"), merchant, Map.of("isOpen", true));

        // 商家不能在自己店下单
        fail(post("/api/orders"), merchant, order(pLocal, 1, "PICKUP", "COD", null), 400);
    }

    // ---------- 快递 + 在线支付 ----------

    @Test @Order(7)
    void expressOnline() throws Exception {
        String cod = fail(post("/api/orders"), buyer, order(pExpress, 1, "EXPRESS", "COD", farAddr), 400);
        assertTrue(cod.contains("在线支付"), cod);

        JsonNode o = ok(post("/api/orders"), buyer, order(pExpress, 1, "EXPRESS", "ONLINE", farAddr));
        long oid = id(o);
        assertEquals("PENDING_PAY", o.get("status").asText());
        // 未支付的单商家看不到
        fail(get("/api/merchant/orders/" + oid), merchant, null, 404);

        JsonNode pay = ok(post("/api/orders/" + oid + "/pay"), buyer, null);
        assertTrue(pay.get("mock").asBoolean());
        String tradeNo = pay.get("outTradeNo").asText();
        ok(post("/api/pay/mock/" + tradeNo + "/success"), buyer, null);
        ok(post("/api/pay/mock/" + tradeNo + "/success"), buyer, null); // 重复回调无副作用

        JsonNode paid = ok(get("/api/orders/" + oid), buyer, null);
        assertEquals("PENDING_SHIP", paid.get("status").asText());
        assertTrue(paid.get("paid").asBoolean());

        fail(post("/api/merchant/orders/" + oid + "/ship"), merchant, Map.of("expressCompany", "中通"), 400);
        assertEquals("SHIPPED", mAct(oid, "ship", Map.of("expressCompany", "中通快递", "expressNo", "ZT1234567890")));
        JsonNode done = ok(post("/api/orders/" + oid + "/confirm"), buyer, null);
        assertEquals("COMPLETED", done.get("status").asText());
        assertEquals("ZT1234567890", done.get("expressNo").asText());

        // 在线支付完成 → 一条待解冻结算记录
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lm_settlement WHERE order_id = ? AND status = 'PENDING_RELEASE'", Integer.class, oid));
    }

    // ---------- 到店自提 ----------

    @Test @Order(8)
    void pickup() throws Exception {
        // 自提可以不选地址，但没有手机号时要求留电话
        String needPhone = fail(post("/api/orders"), buyer, order(pBoth, 1, "PICKUP", "COD", null), 400);
        assertTrue(needPhone.contains("电话"), needPhone);

        Map<String, Object> req = order(pBoth, 1, "PICKUP", "ONLINE", null);
        req.put("contactPhone", "13900000009");
        JsonNode o = ok(post("/api/orders"), buyer, req);
        long oid = id(o);
        assertEquals(0.0, o.get("deliveryFee").asDouble());
        String tradeNo = ok(post("/api/orders/" + oid + "/pay"), buyer, null).get("outTradeNo").asText();
        ok(post("/api/pay/mock/" + tradeNo + "/success"), buyer, null);

        assertEquals("READY_PICKUP", mAct(oid, "accept", null));
        assertEquals("COMPLETED", mAct(oid, "picked_up", null));
    }

    // ---------- 取消与退款 ----------

    @Test @Order(9)
    void cancelAndRefund() throws Exception {
        int stockBefore = ok(get("/api/products/" + pLocal), null, null).get("product").get("stock").asInt();

        JsonNode o = ok(post("/api/orders"), buyer, order(pLocal, 3, "DELIVERY", "ONLINE", nearAddr));
        long oid = id(o);
        String tradeNo = ok(post("/api/orders/" + oid + "/pay"), buyer, null).get("outTradeNo").asText();
        ok(post("/api/pay/mock/" + tradeNo + "/success"), buyer, null);
        assertEquals(stockBefore - 3, ok(get("/api/products/" + pLocal), null, null).get("product").get("stock").asInt());

        JsonNode c = ok(post("/api/orders/" + oid + "/cancel"), buyer, Map.of("reason", "买错了"));
        assertEquals("CANCELLED", c.get("status").asText());
        assertEquals(stockBefore, ok(get("/api/products/" + pLocal), null, null).get("product").get("stock").asInt());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lm_refund WHERE order_id = ?", Integer.class, oid));

        // 接单后买家不能自己取消，商家取消必须写原因
        JsonNode o2 = ok(post("/api/orders"), buyer, order(pLocal, 1, "DELIVERY", "COD", nearAddr));
        long oid2 = id(o2);
        mAct(oid2, "accept", null);
        String m = fail(post("/api/orders/" + oid2 + "/cancel"), buyer, null, 400);
        assertTrue(m.contains("联系老板"), m);
        fail(post("/api/merchant/orders/" + oid2 + "/cancel"), merchant, Map.of(), 400);
        assertEquals("CANCELLED", mAct(oid2, "cancel", Map.of("reason", "缺货")));
    }

    // ---------- 超时任务 ----------

    @Test @Order(10)
    void timeouts() throws Exception {
        long unpaid = id(ok(post("/api/orders"), buyer, order(pLocal, 1, "DELIVERY", "ONLINE", nearAddr)));
        long unaccepted = id(ok(post("/api/orders"), buyer, order(pLocal, 1, "DELIVERY", "COD", nearAddr)));
        long fresh = id(ok(post("/api/orders"), buyer, order(pLocal, 1, "DELIVERY", "COD", nearAddr)));

        jdbc.update("UPDATE lm_order SET created_at = DATEADD('MINUTE', -20, CURRENT_TIMESTAMP) WHERE id = ?", unpaid);
        jdbc.update("UPDATE lm_order SET created_at = DATEADD('MINUTE', -40, CURRENT_TIMESTAMP) WHERE id = ?", unaccepted);

        assertEquals(1, orderService.cancelUnpaid(15));
        assertEquals(1, orderService.cancelUnaccepted(30));
        assertEquals("CANCELLED", ok(get("/api/orders/" + unpaid), buyer, null).get("status").asText());
        assertEquals("CANCELLED", ok(get("/api/orders/" + unaccepted), buyer, null).get("status").asText());
        assertEquals("PENDING_ACCEPT", ok(get("/api/orders/" + fresh), buyer, null).get("status").asText());

        // 在线支付送货单：送达 24 小时后自动确认
        long online = id(ok(post("/api/orders"), buyer, order(pLocal, 1, "DELIVERY", "ONLINE", nearAddr)));
        String tradeNo = ok(post("/api/orders/" + online + "/pay"), buyer, null).get("outTradeNo").asText();
        ok(post("/api/pay/mock/" + tradeNo + "/success"), buyer, null);
        mAct(online, "accept", null);
        mAct(online, "dispatch", null);
        mAct(online, "delivered", null);
        jdbc.update("UPDATE lm_order SET delivered_at = DATEADD('HOUR', -25, CURRENT_TIMESTAMP) WHERE id = ?", online);
        assertEquals(1, orderService.autoConfirm(24, 10));
        assertEquals("COMPLETED", ok(get("/api/orders/" + online), buyer, null).get("status").asText());
    }

    // ---------- 登录与上传 ----------

    @Test @Order(11)
    void authAndUpload() throws Exception {
        fail(get("/api/orders"), null, null, 401);
        fail(get("/api/orders"), "bad-token", null, 401);
        // 公开接口不登录也能用
        ok(get("/api/categories"), null, null);

        JsonNode me = ok(get("/api/me"), merchant, null);
        assertEquals(storeId, me.get("storeId").asLong());
        JsonNode buyerMe = ok(get("/api/me"), buyer, null);
        assertTrue(buyerMe.get("storeId") == null || buyerMe.get("storeId").isNull());

        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        String body = mvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png))
                        .header("Authorization", "Bearer " + buyer))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode r = om.readTree(body);
        assertEquals(0, r.get("code").asInt(), body);
        String url = r.get("data").get("url").asText();
        assertTrue(url.startsWith("http://test.local/files/"));
        // 上传后的图片必须能访问（曾因上传目录启动时不存在导致全部 404/500）
        int status = mvc.perform(get(url.substring("http://test.local".length()))).andReturn().getResponse().getStatus();
        assertEquals(200, status, "上传的图片打不开：" + url);
        assertEquals(404, mvc.perform(get("/files/000000/none.png")).andReturn().getResponse().getStatus());

        String bad = mvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", "not an image".getBytes()))
                        .header("Authorization", "Bearer " + buyer))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(400, om.readTree(bad).get("code").asInt(), bad);
        assertNotNull(r);
    }
}
