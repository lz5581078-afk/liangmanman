# 粮满满 · 开发交接（给 Codex / Claude 等协作开发者）

> 这份文件是项目的协作说明，任何 AI 或人接手前先读完。改了架构、约定、命令时，同步更新这里。
> 产品说明、功能清单、上线需要的账号见 `README.md`；接口契约见 `docs/api-v2.md`、`docs/api-v3.md`；每次工作的记录写在 `docs/worklog.md`。

## 1. 这是什么

农资本地配送平台，定位“农资界的美团”：农户打开微信小程序，看到附近的农资店和店里的货，下单后老板送货上门，也可以到店自提或快递发货。多商家，平台目前不抽佣。

产品负责人（用户）定下的规则，改动前必须遵守：

- **先做微信小程序**，同一套 uni-app 代码以后编译 iOS / Android App。
- **本地配送为主**；每个商品上架时可选「本地配送」「快递发货」（至少一项）。
- **商家上架极简**：拍照 + 填价就能发布，其他都可不填。老板文化程度不高 → 大字、大按钮、少步骤。
- **凑单（分享链接拼单）只是前期推广工具**，不是核心。
- **搜索像淘宝**：所有结果平铺；只有精准找某一款（条码 / 登记证号 / 拍包装认出）时才出现同款比价。
- **在线支付 + 商家自己提现**，用微信支付「电商收付通」，钱进商家自己的二级商户账户。**平台代收再转账属于“二清”，违法，不要做。**
- 限用农药管理：用户说“暂时先不管，后期再说”。

## 2. 目录结构

```
liangmanman/
├── AGENTS.md            本文件（协作说明）
├── CLAUDE.md            指向本文件
├── README.md            产品功能、本地运行、上线配置
├── docs/
│   ├── api-v2.md        第二批接口契约（凑单、评价、求购、资金、后台…）
│   ├── api-v3.md        第三批：商品库具体到一款、搜索平铺、同款比价、上架识别
│   └── worklog.md       工作记录（每次交接追加）
├── scripts/
│   ├── check_routes.py  前端调用 ↔ 后端路由核对
│   └── demo_seed.py     演示数据
├── backend/             Java 17 · Spring Boot 3.3.5 · MyBatis-Plus 3.5.7 · MySQL 8
├── miniapp/             uni-app（Vue 3.4 <script setup>）：买家端 + 商家端
└── admin/               Vue 3.5 · Vite 5 · Element Plus 2：运营后台（部署在 /admin/）
```

### 后端包结构 `backend/src/main/java/com/liangmanman/`

| 包 | 内容 |
| --- | --- |
| `controller/` | `PublicController`（免登录：附近、店铺、商品、搜索、同款比价、凑单、轮播、小程序码）、`BuyerController`、`MerchantController`、`PayNotifyController`（微信支付回调）、`MockPayController`（开发用模拟支付） |
| `admin/` | 运营后台：`AdminController`（/api/admin/**）、`AdminService`、`AdminAccountService`（管理员、操作日志）、`AdminInterceptor` |
| `auth/` | 微信登录（`RealWxMiniClient` / `MockWxMiniClient`）、JWT（`TokenService`）、`AuthInterceptor`（公开路径白名单在这里） |
| `order/` | `OrderStatus`：**订单状态机，状态流转只在这里定义** |
| `service/` | 业务：`OrderService`、`ProductService`、`SearchService`、`SkuService`（“同一款”规则）、`RecognizeService`（拍包装识别）、`GroupBuyService`、`ReviewService`、`WantedService`、`FundService`（开户、解冻、提现）、`MerchantToolsService`（经营数据、证照、赊账本）、`StoreService`、`ContentGuard`（内容安全）… |
| `pay/` | `PaymentService` 接口 + `MockPaymentService` / `WechatEcommercePaymentService`（收付通）/ `DisabledPaymentService`；`OnboardingGateway`（商家开户）同样三种实现 |
| `integration/` | 腾讯云签名客户端、短信、语音、OCR；`WxApi`（小程序码、订阅消息、内容安全） |
| `notify/` | `ChannelNotificationService`：新订单电话 + 短信 + 订阅消息，事务提交后异步发 |
| `job/` | 定时任务：超时取消、自动确认收货、凑单结算、催单、货款解冻、提现 / 开户进度查询 |
| `entity/` `mapper/` `dto/` `common/` `config/` | 实体、MyBatis-Plus Mapper、出入参、工具、配置 |

## 3. 运行、测试、构建

```bash
# 后端（需要 JDK 17+、Maven、MySQL 8）
mysql -uroot -p -e "CREATE DATABASE liangmanman DEFAULT CHARACTER SET utf8mb4"
cd backend
DB_PASSWORD=你的密码 DB_INIT=always mvn spring-boot:run   # 首次建表 + 种子数据，之后去掉 DB_INIT
mvn test                                                  # 不需要 MySQL，H2 内存库跑真实建表脚本
mvn package -DskipTests                                   # 产物 target/liangmanman-server-0.1.0.jar

# 小程序
cd miniapp && npm install
npm run dev:mp-weixin      # 微信开发者工具导入 dist/dev/mp-weixin，勾选“不校验合法域名”
npm run build:mp-weixin    # 正式包
npm run build:h5           # 浏览器版，用来自动化截图 / 检查页面

# 运营后台
cd admin && npm install
npm run dev                # http://localhost:5174/admin/，/api 代理到 127.0.0.1:8080
npm run build

# 核对前端接口和后端路由（改了接口必跑）
python3 scripts/check_routes.py .
```

**不装 MySQL 也能完整跑起来（演示 / 自测用）**：用打包好的 jar + H2 内存库，dev 配置全部是模拟实现：

```bash
cd backend && mvn package -DskipTests
H2=$(find ~/.m2 -name "h2-*.jar" | head -1)
java -Dloader.path=$H2 -cp target/liangmanman-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  --spring.profiles.active=dev \
  "--spring.datasource.url=jdbc:h2:mem:demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1" \
  --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= \
  --spring.sql.init.mode=always --app.public-base-url=http://127.0.0.1:8080
python3 scripts/demo_seed.py   # 另开终端，灌演示数据（需要 pip install pillow）
```

开发环境账号：小程序是模拟登录（`miniapp/src/config.js` 的 `MOCK_LOGIN`，用本机 `lm_device_id` 当身份）；运营后台 `admin` / `admin123`（dev 配置首次启动自动创建）。

## 4. 必须遵守的约定

### 接口

- 响应统一 `ApiResult {code, msg, data}`，`code = 0` 成功；业务错误抛 `BizException`（400 / 401 / 403 / 404），文案是给农户、老板看的大白话中文。
- Jackson 配置了 **null 字段不返回**（`default-property-inclusion: non_null`），前端要按“字段可能不存在”处理。
- 分页统一 `PageResult {records, total, page, size}`，用 `PageResult.page(page, size)` 建分页参数。
- **订单能做什么操作由后端算好**（`buyerActions` / `merchantActions`），前端只按它显示按钮，不在前端写状态判断。
- 接口改动：先改 / 补 `docs/api-vN.md`，再改代码；前端所有请求走 `miniapp/src/api/index.js`、`admin/src/api/index.js` 里的封装，改完跑 `scripts/check_routes.py`。
- 新的免登录 GET 接口要加到 `AuthInterceptor.PUBLIC_GET`；后台接口都在 `/api/admin/**`，用独立的管理员 token（`admin:<id>`，12 小时），与用户 token 互不通用。

### 数据库

- `db/schema.sql` 始终是**新库的完整建表**；`db/seed.sql` 是新库初始数据（分类、搜索同义词）。
- **每次改表**：同时改 `schema.sql` 并新增 `db/migration-vN.sql`（下一个是 `migration-v4.sql`），写清楚从上一版怎么升级。已有的迁移文件不要改。
- 索引用单独的 `CREATE INDEX` 语句（H2 和 MySQL 都要能跑）；SQL 只用两边都支持的语法。
- 金额 `DECIMAL(10,2)` / `BigDecimal`，不用浮点；时间 `LocalDateTime`，时区固定 Asia/Shanghai（`main` 里设置了默认时区）。

### 业务规则的唯一出处（不要在别处重写一份）

| 规则 | 位置 |
| --- | --- |
| 订单状态流转 | `order/OrderStatus.java` |
| 订单能执行的操作、下单校验、库存、超时 | `service/OrderService.java` |
| “同一款”判断（条码相同，或登记证号 + 规格相同）、规格 / 登记证号的统一写法、自动建档 | `service/SkuService.java` |
| 搜索的召回、排序、筛选、可达范围、同款比价 | `service/SearchService.java` |
| 支付是否可用（收付通账户要已开通） | `PaymentService.available(store)` |

### 配置与模拟实现

每个外部服务都有“模拟 / 真实”两套实现，用配置切换，开发环境全部模拟：

| 配置 | 作用 |
| --- | --- |
| `app.wx.mock` | 微信登录、小程序码、订阅消息、内容安全（模拟时含“违禁测试”的文字会被拦） |
| `app.pay.mock` / `app.pay.wechat.sp-mch-id` | 模拟支付；填了服务商号就用收付通；都没有则在线支付关闭、只能货到付款 |
| `app.tencent.ocr-mock` | 拍包装识别（模拟时固定返回一袋“演示牌复合肥”的包装文字） |
| `app.notification.mock` | 短信、语音电话（模拟时只写 `lm_notify_log`） |

正式环境用 `application-prod.yml` + 环境变量（完整清单见 README「上线需要你准备的账号和配置」）。**密钥、证书、私钥不进代码库。**

### 隐私与资金

- 商家开户时上传的身份证照片存在私有目录（`app.private-dir`，不对外提供访问），提交开户后立即删除；数据库只存打码后的姓名和卡号，不存身份证号。
- 通知、外部接口调用都在**事务提交后**异步执行（`notifyExecutor`），失败不影响主流程，结果记 `lm_notify_log`。
- 定时任务里每个订单单独一个事务，一单出错不影响其他单。

### 前端

- 文案全部简体中文，口语化；商家端按钮要大、步骤要少，非必填项默认收起。
- 小程序样式用 `miniapp/src/uni.scss` 里的 `$lm-*` 变量（主色 `$lm-green`，强调色 `$lm-wheat`），不要硬编码新颜色。
- 微信订阅消息授权（`utils/subscribe.js`）必须在用户点击事件里调用。
- 运营后台时间用 `utils/format.js` 的 `time()`（显示到分钟）。

### 测试

- 后端测试在 `backend/src/test`：`FlowTest`（第一批主流程）、`FlowV2Test`（第二批 + 搜索 / 商品库）、`SkuRulesTest`、`OrderStatusTest`，目前 **25 个全部通过**。新功能要补端到端测试（MockMvc + H2）。
- 所有测试类共用同一个 Spring 上下文和同一个 H2 库：**不同测试类要用不同的经纬度、条码、账号**，不要假设库是空的或测试类的执行顺序。
- 读中文响应要用 `getContentAsString(StandardCharsets.UTF_8)`。
- 前端没有单元测试；改页面后至少跑 `npm run build:mp-weixin` 和 `build:h5`，最好用 Playwright 打开 H5 截图看一眼（H5 是 hash 路由，如 `#/pages/search/search?keyword=尿素`；用 localStorage `lm_device_id` 切换模拟账号）。

## 5. 当前进度

已完成（端到端测试覆盖）：开店、上架、附近、搜索（平铺 + 排序筛选 + 同义词 + 拼音）、同款比价、上架识别与商品库、三种拿货方式下单、在线支付 / 货到付款、商家接单送货全流程、超时与自动确认、凑单、评价、再来一单、求购、赊账本、证照、经营数据、收付通开户 / 解冻 / 提现（模拟）、新订单电话短信提醒和催单、内容安全、小程序码海报、运营后台全部页面。

**写好了但没在真实环境验证过**（拿到账号后要逐项联调，字段以官方文档为准）：

- 真实 MySQL 8 上建表和运行（只在 H2 MySQL 模式下跑过）；
- 微信支付电商收付通（下单、回调、退款、分账解冻、开户进件、余额、提现）；
- 腾讯云短信、语音电话、通用文字识别；
- 微信小程序码、订阅消息、内容安全接口；
- 真机上的小程序（只在开发者工具编译和 H5 浏览器里看过）。

**没做**（原因见 README「还没做」）：支付宝、以图搜图、拍病虫害找药、对接批发商、限用农药管理、图片存 COS（现在存服务器磁盘）、微信手机号快速验证（现在手填）。

**已知的小问题 / 可以改进的地方**：

- 搜索是数据库 LIKE + 内存排序（每次最多取 500 条候选），商品多了要换 Elasticsearch，接口不用变。
- 商品库开始是空的，靠商家上架积累；运营最好先把试点县常卖的几十款录进去。
- 运营后台「店铺管理」表格在 1440 宽度下申请时间列会被横向滚动挡住一点。
- 小程序 `config.js` 的 `BASE_URL`、`MOCK_LOGIN`、`DEFAULT_LOCATION` 和 `manifest.json` 的 AppID 上线前要改。

## 6. 协作规则（多个 AI / 人同时开发）

1. **用 git**。每个任务开一个分支（`feat/xxx`、`fix/xxx`），小步提交，合并前跑完测试和构建。不要直接在 main 上改。
2. **开工前**：读本文件 → 读 `docs/worklog.md` 最近几条 → 看当前分支和未提交的改动，确认没人在改同一块。
3. **一个任务只动它需要的文件**。同时进行的两个任务不要改同一个文件；`OrderService`、`SearchService`、`schema.sql`、两个 `api/index.js` 是高冲突文件，改之前在 worklog 里写一句“正在改 X”。
4. **改表**只能新增迁移文件，编号按顺序递增，开工前确认下一个编号没被占用。
5. **改接口**先改文档，前后端一起改完再合并，跑 `scripts/check_routes.py`。
6. **完成标准**：`mvn test` 全过；改了小程序就 `build:mp-weixin` + `build:h5` 通过；改了后台就 `npm run build` 通过；README / 本文件 / 接口文档跟着更新。
7. **收工时**在 `docs/worklog.md` 追加一条：日期、谁（Codex / Claude）、做了什么、改了哪些文件、测试结果、没做完的和要注意的。
8. 不确定产品规则时，问用户，不要自己定；第 1 节的规则是用户明确说过的。
