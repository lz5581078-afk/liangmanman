# 接口变更 v3：商品库具体到“一款”、搜索平铺、同款比价、上架识别

在 `api-v2.md` 基础上的改动。响应外层仍是 `{code, msg, data}`，null 字段不返回。

## 概念

- **一款（SKU）**：商品库 `lm_standard_product` 里的一条，具体到品牌 + 名称 + 规格 + 登记证号 / 条码。
  各店卖的同一款挂到这一条上，才叫“同款”，才能比价。
- 判断同一款：**条码相同**，或 **登记证号 + 规格都相同**。只有登记证号没有规格不算确定。
- 商家上架时如果带了条码或“登记证号 + 规格”，后端自动找这一款，找不到就自动建档（`status=PENDING` 待核实），由运营核实 / 合并。
- 旧的“常用品库”（通用名，无品牌）已删除，改成**搜索同义词表**（农户叫法 → 包装叫法）。

## SkuView（一款）

```json
{ "id": 3, "categoryId": 1, "brand": "演示牌", "name": "复合肥 15-15-15", "spec": "50kg",
  "regNo": "农肥(2024)准字0001号", "barcode": "6901234567892", "manufacturer": "演示肥业有限公司",
  "image": "http://.../a.jpg", "status": "VERIFIED|PENDING", "displayName": "演示牌 复合肥 15-15-15 50kg" }
```

## ProductView 新增字段

`brand`、`spec`（可空）。商品页、店铺页、商家商品列表里都有。

## 1. 搜索（改）`GET /api/search`

参数：`keyword`（必填）、`lat`、`lng`、
`sort` = `DEFAULT` 综合（默认）| `DISTANCE` 离我近 | `PRICE_ASC` 价格低到高 | `PRICE_DESC` 价格高到低 | `SALES` 销量，
`delivery` = `DELIVER` 能送货上门 | `PICKUP` 能到店自提 | `EXPRESS` 能发快递（可空 = 不限），
`categoryId`（可空），`page`（默认 1）、`size`（默认 20，最大 50）。

```json
{
  "keyword": "复合肥", "total": 23, "page": 1, "size": 20,
  "records": [ {
    "productId": 1, "name": "复合肥 15-15-15", "brand": "演示牌", "spec": "50kg",
    "image": "...", "price": 165.00, "unit": "袋", "sales": 12,
    "storeId": 1, "storeName": "丰收农资", "storeOpen": true, "distanceKm": 0.8,
    "canDeliver": true, "canPickup": true, "express": false, "expressFee": 0, "deliveryFee": 0,
    "skuId": 3, "sameCount": 3
  } ],
  "exact": [ { "sku": SkuView, "minPrice": 160.00, "offers": [Offer...] } ]
}
```

- 关键词可以是多个词（如“演示牌 复合肥 50kg”），每个词都出现在名称、品牌、规格里的商品也会搜出来。
- `records`：平铺，每家店的每个商品一条（像淘宝），按 `sort` 排好，分页。
- `sameCount`：附近（含能发快递的）卖**同一款**的店数，含本店；`> 1` 时在这条上显示“N 家有同款”，点了进同款比价页（`skuId`）。`skuId` 为空表示这件商品还没认出是哪一款。
- `exact`：**只有精准找货**（关键词是条码或登记证号）时才有内容，否则是空数组。前端在列表上方显示“找到这一款，N 家在卖”的比价块。
- Offer 字段：`productId, productName, price, unit, image, storeId, storeName, storeOpen, distanceKm, canDeliver, canPickup, express, expressFee, deliveryFee`。

## 2. 同款比价（新）`GET /api/skus/{id}/offers`

参数：`lat`、`lng`、`sort`（`DEFAULT`/`PRICE_ASC` 价格低到高，`PRICE_DESC`，`DISTANCE`）。不需要登录。
返回 `{ sku: SkuView, minPrice, offers: [Offer] }`，营业中的店在前。

## 3. 按条码 / 登记证号找一款（新）`GET /api/skus/lookup`

参数：`barcode`，或 `regNo` + `spec`。不需要登录。`data` 为 SkuView；商品库里没有时 **data 不返回（null）**。
用于：商家上架扫条码、买家扫条码。

## 4. 商品页（改）`GET /api/products/{id}`

`{ product: ProductView, store: StoreBrief, sku: SkuView|null, otherStoresCount }`
——原来的 `standardName` 去掉了，改成 `sku`。`otherStoresCount > 0` 且有 `sku` 时显示“还有 N 家店卖同一款，比一比”，进同款比价页。

## 5. 拍包装识别（改）`POST /api/ocr/product`（multipart `file`，需登录）

```json
{ "lines": ["演示牌", "复合肥 15-15-15", "..."], "keyword": "复合肥 15-15-15",
  "brand": "演示牌", "spec": "50kg", "regNo": "农肥(2024)准字0001号", "barcode": "6901234567892",
  "manufacturer": "演示肥业有限公司",
  "match": SkuView | null, "candidates": [SkuView] }
```

- `match`：商品库里**精确认出**的这一款（条码，或登记证号 + 规格）。
- `candidates`：可能是的款（不含 match），最多 10 个。
- 买家拍照搜索：有 `match` → 直接进同款比价页；否则用 `keyword` 搜索。
- 商家上架：见第 6 节。

## 6. 上架 / 编辑商品（改）`POST /api/merchant/products`、`PUT /api/merchant/products/{id}`

请求在原有字段上新增（都可空）：`brand`、`spec`、`barcode`、`regNo`、`manufacturer`、`notThisSku`（老板点了“不是这一款”时传 true，编辑时会去掉原来挂的款）。
`standardProductId` 仍可传：老板点了“是这一款”时传它。

后端规则：传了 `standardProductId` → 挂这一款（包装上认出的条码 / 登记证号若这一款还没有，会补上）；否则用 `barcode` / `regNo + spec` 找，找不到且信息够 → 自动建档（待核实）并挂上；都没有 → 不挂。
名称、品牌、规格没填时，从这一款带出。编辑时不传身份信息则保留原来挂的款。返回 ProductView（含 `standardProductId`、`brand`、`spec`）。

## 7. 商品库搜索 `GET /api/standard-products?keyword=&categoryId=`

返回 `[SkuView]`（原来返回实体，现在是 SkuView，多了 `displayName`）。关键词匹配名称、品牌、同义词、登记证号、条码。商家上架时“找这一款”用。

## 8. 运营后台（改 / 新）

- `GET /api/admin/standard-products?keyword=&categoryId=&status=&page=&size=` → PageResult，记录是 SkuView 字段 + `productCount`（挂了几个商品）、`keywords`、`sourceStoreName`（自动建档来自哪家店）、`createdAt`。`status=PENDING` 看待核实。
- `POST /api/admin/standard-products`、`PUT /api/admin/standard-products/{id}`：body `{categoryId, brand, name, spec, regNo, barcode, manufacturer, image, keywords}`，返回 SkuView。条码重复、“登记证号 + 规格”重复会报错提示用合并。运营保存后 status 变 VERIFIED。
- `POST /api/admin/standard-products/{id}/verify`：核实（status → VERIFIED）。
- `POST /api/admin/standard-products/{id}/merge` body `{intoId}`：把这一款并入 intoId（商品改挂、缺的条码 / 登记证号补到 intoId，这一条删除）。
- `DELETE /api/admin/standard-products/{id}`：有商品挂着时报错。
- 搜索同义词：`GET /api/admin/search-words?keyword=` → `[{id, word, synonyms, createdAt}]`；`POST /api/admin/search-words`、`PUT /api/admin/search-words/{id}` body `{word, synonyms}`（同义词空格分隔，中文逗号也行）；`DELETE /api/admin/search-words/{id}`。
- 看板 `GET /api/admin/dashboard` 新增 `pendingSkus`（待核实的款数）。
- 商品管理 `GET /api/admin/products` 记录里新增 `brand`、`spec`；`standardName` 仍在。
