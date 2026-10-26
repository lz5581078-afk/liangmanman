# 粮满满 · 第二批接口契约

沿用第一批约定：
- 返回 `{ code, msg, data }`，`code = 0` 成功；业务错误 HTTP 200 + code ≠ 0，`msg` 直接给用户看。
- 值为 null 的字段**不输出**（前端按“没有”处理）。
- 金额：元，数字（如 `12.5`）。时间：`LocalDateTime` 输出 `"2026-10-24T09:30:00"`，日期 `"2026-10-24"`。
- 小程序请求头 `Authorization: Bearer <用户token>`；运营后台请求头 `Authorization: Bearer <管理员token>`（两种 token 不通用）。
- 分页返回：`{ records: [...], total, page, size }`，请求参数 `page`（从 1 开始，默认 1）、`size`（默认 20，最大 100）。

第一批已有的接口见 README，本文件只列**新增和变化**的部分。

---

## 一、订单变化

### 新状态
`GROUP_WAITING` = “待成团”（凑单订单付款后、成团前）。

### OrderView 新增字段
| 字段 | 说明 |
| --- | --- |
| `groupBuyId` | 凑单订单才有 |
| `reviewed` | boolean，是否已评价 |

### buyerActions 新增
- `REVIEW`：已完成且未评价
- `REORDER`：已完成或已取消（再来一单）
- `CANCEL` 也出现在 `GROUP_WAITING`（退出凑单，自动退款）

### 下单 / 预览支持凑单
`POST /api/orders/preview` 与 `POST /api/orders` 请求体新增可选 `groupBuyId`。
带 `groupBuyId` 时：`items` 只能是该凑单的那一个商品；单价按凑单价；不受起送金额限制；`payMethods` 只有 `ONLINE`。

### 再来一单
`GET /api/orders/{id}/reorder` → `{ storeId, storeName, products: ProductView[], quantities: { [productId]: number } }`
只返回仍在售的商品。前端把它们放进该店购物车。

### 评价
`POST /api/orders/{id}/review`
```json
{ "score": 5, "content": "送得快", "images": ["https://..."] }
```
`score` 1–5 必填；`content` ≤ 500 可空；`images` ≤ 3 张可空。→ 返回 `Review`。

---

## 二、凑单

### GroupBuyView
```json
{
  "id": 1, "storeId": 1, "storeName": "老王农资店", "storePhone": "138...",
  "productId": 3, "productName": "复合肥 15-15-15 50kg", "productImage": "https://...",
  "unit": "袋", "originalPrice": 150, "groupPrice": 128,
  "targetQty": 50, "currentQty": 32, "limitPerUser": 10,
  "endAt": "2026-10-27T20:00:00", "endWhenReached": false,
  "status": "ONGOING",
  "statusLabel": "进行中",
  "decisionDeadline": "...",
  "members": [ { "nickname": "张*", "village": "东王村", "quantity": 5, "at": "..." } ],
  "myQuantity": 5,
  "distanceKm": 1.4,
  "localDelivery": true, "expressDelivery": false
}
```
`status`：`ONGOING` 进行中 | `SUCCESS` 已成团 | `WAIT_DECISION` 未凑够·等老板决定 | `FAILED` 未成团已退款。
`members` 最多 30 条，昵称打码；`myQuantity` 登录才有。

### 买家
- `GET /api/group-buys/{id}?lat=&lng=` → GroupBuyView（不登录可看）
- `GET /api/group-buys/nearby?lat=&lng=` → GroupBuyView[]（附近进行中的，不含 members）
- 参加：走下单接口，`groupBuyId` + 一个商品 + 数量，付款后进入“待成团”。

### 商家
- `GET /api/merchant/group-buys` → GroupBuyView[]（含 members）
- `POST /api/merchant/group-buys`
  ```json
  { "productId": 3, "groupPrice": 128, "targetQty": 50, "days": 3, "limitPerUser": 10, "endWhenReached": false }
  ```
  `days` 1–30；`limitPerUser` 可空（不限）。同一商品同时只能有一个进行中的凑单。
- `POST /api/merchant/group-buys/{id}/decide` `{ "action": "SHIP" | "REFUND" }`（仅 `WAIT_DECISION`）
- `POST /api/merchant/group-buys/{id}/close`：提前结束（立刻按是否达标处理）

### 店铺页
`GET /api/stores/{id}` 的 StoreDetail 新增 `groupBuys: GroupBuyView[]`（进行中，不含 members）、`reviewSummary: { count, rating }`。

---

## 三、评价

### Review
```json
{ "id": 1, "orderId": 9, "score": 5, "content": "...", "images": [], "nickname": "张*", "reply": "谢谢", "createdAt": "...", "replyAt": "..." }
```
- `GET /api/stores/{id}/reviews?page=` → 分页 Review（公开）
- `GET /api/merchant/reviews?page=` → 分页 Review
- `POST /api/merchant/reviews/{id}/reply` `{ "reply": "谢谢支持" }`

---

## 四、求购

### Wanted
```json
{ "id": 1, "keyword": "玉米种子 郑单958", "note": "要 20 袋", "contactPhone": "139...",
  "status": "OPEN", "createdAt": "...", "expireAt": "...", "distanceKm": 2.1,
  "replies": [ { "id": 1, "storeId": 1, "storeName": "老王农资店", "storePhone": "138...", "price": 45, "note": "有货", "productId": 3, "createdAt": "..." } ] }
```
`status`：`OPEN` | `CLOSED` | `EXPIRED`（发布 3 天后）。
- 买家：`POST /api/wanted` `{ "keyword", "note", "contactPhone", "lat", "lng" }`；`GET /api/wanted/mine` → Wanted[]（含 replies）；`POST /api/wanted/{id}/close`
- 商家：`GET /api/merchant/wanted` → 附近（店铺送货范围与 30 公里取大）未过期的 OPEN 求购 Wanted[]（replies 只含本店的回复）；`POST /api/merchant/wanted/{id}/reply` `{ "price": 45, "note": "有货", "productId": 3 }`（price、productId 可空，note 必填）

---

## 五、商家：经营数据、证件、店铺码

- `GET /api/merchant/stats` →
  ```json
  { "todayOrders": 3, "todayAmount": 560, "monthOrders": 42, "monthAmount": 8800,
    "newOrders": 2, "doingOrders": 4, "pendingReviews": 1, "openWanted": 5,
    "topProducts": [ { "productId": 1, "name": "...", "quantity": 30, "amount": 4500 } ] }
  ```
  “金额”只算已完成订单；`pendingReviews` = 未回复的评价数；`openWanted` = 附近未过期求购数。
- 证件：`GET /api/merchant/licenses` → License[]；`POST /api/merchant/licenses` `{ "type": "BUSINESS|PESTICIDE|SEED|VET", "image": "https://...", "validUntil": "2030-01-01" }`
  License：`{ id, type, typeLabel, image, validUntil, status: PENDING|APPROVED|REJECTED, rejectReason, createdAt }`
- 小程序码图片：`GET /api/qrcode?type=store|product|group&id=1` → **PNG 图片**（不是 JSON，公开）。扫码进入页面时 `onLoad` 的 `query.scene` 为 `s=1` / `p=1` / `g=1`（需 `decodeURIComponent`）。

---

## 六、收款开户（进件）、余额、提现

### 开户状态
`GET /api/merchant/account` →
```json
{ "status": "NONE|SUBMITTED|AUDITING|NEED_SIGN|APPROVED|REJECTED", "statusLabel": "...",
  "subMchId": "...", "bankCardMasked": "**** 1234", "idNameMasked": "王*", "signUrl": "https://...", "rejectReason": "...",
  "onlinePayEnabled": true }
```
`onlinePayEnabled`：该店能否收在线支付（开户成功后为 true；开发环境始终 true）。

### 证件照上传（私密，不对外公开访问）
`POST /api/merchant/onboarding/image`（multipart，字段 `file`）→ `{ "imageId": "a1b2..." }`

### 提交开户
`POST /api/merchant/onboarding`
```json
{ "idCardFrontImageId": "...", "idCardBackImageId": "...",
  "idName": "王大山", "idNumber": "410...", "idValidFrom": "2015-01-01", "idValidTo": "2035-01-01 或 长期",
  "bankName": "中国农业银行", "bankCityCode": "410100", "bankAccount": "6228...",
  "mobile": "138...", "shortName": "老王农资店" }
```
身份证号、银行卡号只用于提交微信，**平台只保存打码后的结果**。

### 余额与提现
- `GET /api/merchant/balance` → `{ "available": 1200.5, "pending": 300 }`（pending = 订单未完成、暂未解冻的钱）
- `POST /api/merchant/withdraw` `{ "amount": 1000 }` → Withdraw
- `GET /api/merchant/withdrawals?page=` → 分页 Withdraw：`{ id, amount, status: PROCESSING|SUCCESS|FAILED, statusLabel, reason, createdAt }`
- `GET /api/merchant/settlements?page=` → 分页收入明细：`{ id, orderId, orderNo, amount, status: PENDING_RELEASE|RELEASED|FAILED, statusLabel, createdAt }`

---

## 七、赊账记账（只记账，不经手资金）

- `GET /api/merchant/credit/customers?keyword=` → `{ totalOwed, customers: CreditCustomer[] }`
  CreditCustomer：`{ id, name, phone, village, balance, updatedAt }`（`balance` = 还欠多少，>0 表示欠款）
- `POST /api/merchant/credit/customers` `{ "name", "phone", "village" }`（phone、village 可空）
- `PUT /api/merchant/credit/customers/{id}` 同上
- `GET /api/merchant/credit/customers/{id}/records?page=` → 分页 `{ id, type: CREDIT|REPAY, typeLabel, amount, note, createdAt }`
- `POST /api/merchant/credit/customers/{id}/records` `{ "type": "CREDIT|REPAY", "amount": 300, "note": "复合肥 2 袋" }` → 更新后的 CreditCustomer

---

## 八、拍照识别、轮播、分享

- `POST /api/ocr/product`（multipart `file`，需登录）→
  ```json
  { "lines": ["复合肥 15-15-15", "净含量：50kg"], "keyword": "复合肥 15-15-15", "spec": "50kg", "regNo": "PD20...",
    "candidates": [ StandardProduct ] }
  ```
  识别不出时 `lines` 为空数组、无 `keyword`。
- `GET /api/banners?lat=&lng=` → `[{ id, image, title, link }]`（公开）；`link` 是小程序页面路径，可空。
- `POST /api/share/visit` `{ "sharerId": 12, "targetType": "STORE|PRODUCT|GROUP_BUY", "targetId": 3 }`（需登录，记录谁带来的访问）

---

## 九、运营后台 `/api/admin/**`

登录：`POST /api/admin/login` `{ username, password }` → `{ token, admin: { id, username, name, role } }`（role：`ADMIN` | `OPERATOR`）
其余接口都要管理员 token；token 无效返回 code 401。

| 功能 | 接口 |
| --- | --- |
| 当前管理员 | `GET /api/admin/me`；改密码 `POST /api/admin/me/password { oldPassword, newPassword }` |
| 看板 | `GET /api/admin/dashboard` → `{ users, storesApproved, storesPending, products, ordersToday, amountToday, orders7d, amount7d, daily: [{ date, orders, amount }]（近 14 天）, noResultKeywords: [{ keyword, count }]（近 7 天前 20）, pendingLicenses, pendingNormalize }` |
| 店铺 | `GET /api/admin/stores?status=&keyword=&page=&size=` → 分页 `AdminStore`；`GET /api/admin/stores/{id}` → `{ store: AdminStore, owner: User, licenses: License[], account, productCount, orderCount }`；`POST /api/admin/stores/{id}/approve`、`/reject {reason}`、`/ban {reason}`、`/unban` |
| 证件 | `GET /api/admin/licenses?status=PENDING&page=` → 分页 License（含 `storeId, storeName`）；`POST /api/admin/licenses/{id}/approve`、`/reject {reason}` |
| 商品 | `GET /api/admin/products?keyword=&storeId=&normalize=PENDING|LINKED&onShelf=&page=` → 分页 `AdminProduct`；`POST /api/admin/products/{id}/off-shelf {reason}`；`POST /api/admin/products/{id}/link {standardProductId}`；`POST /api/admin/products/{id}/unlink` |
| 标准品库 | `GET /api/admin/standard-products?keyword=&categoryId=&page=`；`POST`、`PUT /{id}`、`DELETE /{id}`（被引用时不能删）。字段：`categoryId, brand, name, spec, regNo, image, keywords` |
| 分类 | `GET /api/admin/categories`；`POST`、`PUT /{id}`。字段 `name, sort, icon` |
| 订单 | `GET /api/admin/orders?status=&keyword=（订单号/手机号）&storeId=&page=` → 分页 OrderView；`GET /api/admin/orders/{id}` → OrderView（含 logs）；`POST /api/admin/orders/{id}/force-cancel {reason}`（仲裁：取消并原路退款） |
| 凑单 | `GET /api/admin/group-buys?status=&page=` → 分页 GroupBuyView |
| 资金 | `GET /api/admin/settlements?status=&page=`；`POST /api/admin/settlements/{id}/retry`；`GET /api/admin/withdrawals?status=&page=`；`GET /api/admin/refunds?page=` |
| 评价 | `GET /api/admin/reviews?storeId=&page=`；`DELETE /api/admin/reviews/{id}` |
| 求购 | `GET /api/admin/wanted?status=&page=` |
| 用户 | `GET /api/admin/users?keyword=&page=` → 分页 `{ id, nickname, phone, createdAt, storeId }` |
| 开通区域 | `GET /api/admin/regions`；`POST`、`PUT /{id}`、`DELETE /{id}`。字段 `name, lat, lng, radiusKm, enabled`。有启用的区域时，只有区域内的位置能开店 |
| 轮播图 | `GET /api/admin/banners`；`POST`、`PUT /{id}`、`DELETE /{id}`。字段 `image, title, link, sort, enabled, regionId` |
| 管理员 | `GET /api/admin/admins`；`POST { username, password, name, role }`；`POST /api/admin/admins/{id}/reset-password { password }`；`POST /api/admin/admins/{id}/toggle`（启用/停用）——仅 ADMIN |
| 操作日志 | `GET /api/admin/logs?page=` → 分页 `{ id, adminName, action, target, detail, createdAt }` |
| 通知记录 | `GET /api/admin/notify-logs?orderId=&page=` → 分页 `{ id, channel, target, content, success, error, orderId, createdAt }` |
| 上传图片 | `POST /api/admin/files`（multipart `file`）→ `{ url }` |

AdminStore：Store 全部字段 + `ownerNickname, ownerPhone, productCount`。
AdminProduct：ProductView 全部字段 + `storeName, normalizeStatus, standardName`。
