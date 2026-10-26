-- 粮满满 数据库表结构（MySQL 8）
-- 表名统一前缀 lm_，金额单位：元，DECIMAL(10,2)
-- 同一份脚本也用于自动化测试（H2 MySQL 兼容模式），因此索引单独 CREATE INDEX。
-- 从第一批升级的已有数据库请执行 migration-v2.sql，不要重新执行本文件。

-- ========== 用户 ==========
CREATE TABLE lm_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  union_id      VARCHAR(64)  NULL COMMENT '微信 unionid，小程序与 App 打通用',
  open_id       VARCHAR(64)  NULL COMMENT '小程序 openid',
  phone         VARCHAR(20)  NULL,
  nickname      VARCHAR(64)  NULL,
  avatar        VARCHAR(500) NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_user_open_id ON lm_user (open_id);
CREATE INDEX idx_user_union_id ON lm_user (union_id);
CREATE INDEX idx_user_phone ON lm_user (phone);

CREATE TABLE lm_address (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  contact_name  VARCHAR(32)  NOT NULL,
  phone         VARCHAR(20)  NOT NULL,
  lat           DECIMAL(10,6) NOT NULL,
  lng           DECIMAL(10,6) NOT NULL,
  town          VARCHAR(32)  NULL COMMENT '乡镇',
  village       VARCHAR(32)  NULL COMMENT '村，用于商家按村分组送货',
  detail        VARCHAR(200) NULL COMMENT '详细描述，如：村东头小卖部旁',
  is_default    TINYINT      NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_address_user ON lm_address (user_id);

-- ========== 店铺 ==========
CREATE TABLE lm_store (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_id           BIGINT        NOT NULL,
  name               VARCHAR(64)   NOT NULL,
  phone              VARCHAR(20)   NOT NULL,
  cover_image        VARCHAR(500)  NULL COMMENT '门头照',
  lat                DECIMAL(10,6) NOT NULL,
  lng                DECIMAL(10,6) NOT NULL,
  address_text       VARCHAR(200)  NULL,
  town               VARCHAR(32)   NULL,
  notice             VARCHAR(500)  NULL COMMENT '店铺公告',
  business_hours     VARCHAR(64)   NULL,
  is_open            TINYINT       NOT NULL DEFAULT 1 COMMENT '营业中 / 打烊',
  delivery_enabled   TINYINT       NOT NULL DEFAULT 1 COMMENT '本地：送货上门',
  pickup_enabled     TINYINT       NOT NULL DEFAULT 1 COMMENT '本地：到店自提',
  delivery_radius_km DECIMAL(5,1)  NOT NULL DEFAULT 10.0,
  min_order_amount   DECIMAL(10,2) NOT NULL DEFAULT 0,
  delivery_fee       DECIMAL(10,2) NOT NULL DEFAULT 0,
  cod_enabled        TINYINT       NOT NULL DEFAULT 1 COMMENT '是否接受货到付款',
  main_categories    VARCHAR(200)  NULL COMMENT '主营品类，逗号分隔',
  status             VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/BANNED',
  status_reason      VARCHAR(200)  NULL COMMENT '拒绝或停用原因',
  rating             DECIMAL(2,1)  NOT NULL DEFAULT 5.0,
  review_count       INT           NOT NULL DEFAULT 0,
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_store_owner ON lm_store (owner_id);
CREATE INDEX idx_store_geo ON lm_store (lat, lng);

CREATE TABLE lm_store_license (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id      BIGINT       NOT NULL,
  type          VARCHAR(16)  NOT NULL COMMENT 'BUSINESS/PESTICIDE/SEED/VET',
  image         VARCHAR(500) NOT NULL,
  valid_until   DATE         NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  reject_reason VARCHAR(200) NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_license_store ON lm_store_license (store_id);
CREATE INDEX idx_license_status ON lm_store_license (status);

-- 收款开户（微信支付收付通二级商户）
CREATE TABLE lm_merchant_account (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id         BIGINT       NOT NULL,
  applyment_no     VARCHAR(64)  NULL COMMENT '我方申请单号 out_request_no',
  applyment_id     VARCHAR(32)  NULL COMMENT '微信申请单号',
  sub_mch_id       VARCHAR(32)  NULL COMMENT '收付通二级商户号',
  status           VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/SUBMITTED/AUDITING/NEED_SIGN/APPROVED/REJECTED',
  bank_card_masked VARCHAR(32)  NULL COMMENT '仅存打码卡号，如 **** 1234',
  id_name_masked   VARCHAR(32)  NULL COMMENT '仅存打码姓名，如 王*',
  sign_url         VARCHAR(500) NULL,
  reject_reason    VARCHAR(500) NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_merchant_account_store ON lm_merchant_account (store_id);

-- 开户用的证件照（私密存储，不经 /files 对外公开，开户完成后删除）
CREATE TABLE lm_private_image (
  id          VARCHAR(32)  NOT NULL PRIMARY KEY,
  store_id    BIGINT       NOT NULL,
  path        VARCHAR(500) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========== 商品 ==========
CREATE TABLE lm_category (
  id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  name  VARCHAR(16) NOT NULL,
  icon  VARCHAR(500) NULL,
  sort  INT NOT NULL DEFAULT 0
);

CREATE TABLE lm_standard_product (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id      BIGINT       NULL,
  brand            VARCHAR(32)  NULL,
  name             VARCHAR(64)  NOT NULL,
  spec             VARCHAR(32)  NULL COMMENT '规格，如 50kg',
  reg_no           VARCHAR(64)  NULL COMMENT '登记证号 / 审定编号；同一证号不同规格是不同的款',
  barcode          VARCHAR(32)  NULL COMMENT '商品条码，有则唯一',
  manufacturer     VARCHAR(64)  NULL COMMENT '生产企业',
  image            VARCHAR(500) NULL,
  keywords         VARCHAR(300) NULL COMMENT '同义词、拼音，空格分隔，供搜索',
  status           VARCHAR(16)  NOT NULL DEFAULT 'VERIFIED' COMMENT 'VERIFIED 已核实 / PENDING 商家上架时自动建、待核实',
  source_store_id  BIGINT       NULL COMMENT '自动建档时来自哪家店',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_std_reg_no ON lm_standard_product (reg_no);
CREATE INDEX idx_std_barcode ON lm_standard_product (barcode);
CREATE INDEX idx_std_category ON lm_standard_product (category_id);

CREATE TABLE lm_product (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id             BIGINT        NOT NULL,
  standard_product_id  BIGINT        NULL COMMENT '归到的标准商品，可空',
  category_id          BIGINT        NULL,
  name                 VARCHAR(64)   NOT NULL,
  brand                VARCHAR(32)   NULL,
  spec                 VARCHAR(32)   NULL,
  price                DECIMAL(10,2) NOT NULL,
  unit                 VARCHAR(8)    NOT NULL DEFAULT '件',
  images               VARCHAR(3000) NOT NULL COMMENT 'JSON 数组',
  description          VARCHAR(1000) NULL,
  stock                INT           NULL COMMENT 'NULL = 不限量',
  sales                INT           NOT NULL DEFAULT 0,
  local_delivery       TINYINT       NOT NULL DEFAULT 1 COMMENT '支持本地配送（送货/自提）',
  express_delivery     TINYINT       NOT NULL DEFAULT 0 COMMENT '支持快递发货',
  express_fee          DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '快递运费，0 = 包邮',
  on_shelf             TINYINT       NOT NULL DEFAULT 1,
  off_reason           VARCHAR(200)  NULL COMMENT '平台下架原因',
  normalize_status     VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'LINKED/PENDING',
  search_text          VARCHAR(600)  NULL COMMENT '名称的拼音全拼与首字母、标准品同义词，供搜索',
  created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_product_store ON lm_product (store_id, on_shelf);
CREATE INDEX idx_product_std ON lm_product (standard_product_id);

-- ========== 交易 ==========
CREATE TABLE lm_order (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no        VARCHAR(32)   NOT NULL,
  buyer_id        BIGINT        NOT NULL,
  store_id        BIGINT        NOT NULL,
  group_buy_id    BIGINT        NULL COMMENT '凑单订单',
  status          VARCHAR(24)   NOT NULL,
  fulfillment     VARCHAR(16)   NOT NULL COMMENT 'DELIVERY 送货上门 / PICKUP 自提 / EXPRESS 快递',
  pay_method      VARCHAR(16)   NOT NULL COMMENT 'ONLINE / COD 货到付款',
  goods_amount    DECIMAL(10,2) NOT NULL,
  delivery_fee    DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_amount    DECIMAL(10,2) NOT NULL,
  paid            TINYINT       NOT NULL DEFAULT 0,
  paid_at         DATETIME      NULL,
  cod_received    TINYINT       NOT NULL DEFAULT 0,
  reviewed        TINYINT       NOT NULL DEFAULT 0,
  remind_count    INT           NOT NULL DEFAULT 0 COMMENT '已发出的“催接单”次数',
  contact_name    VARCHAR(32)   NULL,
  contact_phone   VARCHAR(20)   NULL,
  address_text    VARCHAR(300)  NULL COMMENT '地址快照',
  address_lat     DECIMAL(10,6) NULL,
  address_lng     DECIMAL(10,6) NULL,
  village         VARCHAR(32)   NULL,
  expected_date   DATE          NULL COMMENT '期望送达日期',
  remark          VARCHAR(200)  NULL,
  express_company VARCHAR(32)   NULL,
  express_no      VARCHAR(64)   NULL,
  cancel_reason   VARCHAR(200)  NULL,
  accepted_at     DATETIME      NULL,
  dispatched_at   DATETIME      NULL,
  delivered_at    DATETIME      NULL,
  shipped_at      DATETIME      NULL,
  completed_at    DATETIME      NULL,
  cancelled_at    DATETIME      NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_order_no ON lm_order (order_no);
CREATE INDEX idx_order_buyer ON lm_order (buyer_id, created_at);
CREATE INDEX idx_order_store ON lm_order (store_id, status);
CREATE INDEX idx_order_status_time ON lm_order (status, created_at);
CREATE INDEX idx_order_group ON lm_order (group_buy_id);

CREATE TABLE lm_order_item (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id       BIGINT        NOT NULL,
  product_id     BIGINT        NOT NULL,
  product_name   VARCHAR(64)   NOT NULL,
  product_image  VARCHAR(500)  NULL,
  unit           VARCHAR(8)    NULL,
  price          DECIMAL(10,2) NOT NULL,
  quantity       INT           NOT NULL,
  amount         DECIMAL(10,2) NOT NULL
);
CREATE INDEX idx_order_item_order ON lm_order_item (order_id);

CREATE TABLE lm_order_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id     BIGINT       NOT NULL,
  from_status  VARCHAR(24)  NULL,
  to_status    VARCHAR(24)  NOT NULL,
  operator     VARCHAR(16)  NOT NULL COMMENT 'BUYER/MERCHANT/SYSTEM/ADMIN',
  note         VARCHAR(200) NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_order_log_order ON lm_order_log (order_id);

-- ========== 资金 ==========
CREATE TABLE lm_payment (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id        BIGINT        NOT NULL,
  out_trade_no    VARCHAR(32)   NOT NULL,
  channel         VARCHAR(16)   NOT NULL COMMENT 'WECHAT / MOCK',
  sub_mch_id      VARCHAR(32)   NULL COMMENT '收款的二级商户号',
  amount          DECIMAL(10,2) NOT NULL,
  status          VARCHAR(16)   NOT NULL COMMENT 'PENDING/SUCCESS/CLOSED',
  transaction_id  VARCHAR(64)   NULL,
  paid_at         DATETIME      NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_payment_trade_no ON lm_payment (out_trade_no);
CREATE INDEX idx_payment_order ON lm_payment (order_id);

CREATE TABLE lm_refund (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id       BIGINT        NOT NULL,
  out_refund_no  VARCHAR(32)   NOT NULL,
  refund_id      VARCHAR(64)   NULL COMMENT '微信退款单号',
  amount         DECIMAL(10,2) NOT NULL,
  status         VARCHAR(16)   NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
  reason         VARCHAR(200)  NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_refund_no ON lm_refund (out_refund_no);
CREATE INDEX idx_refund_order ON lm_refund (order_id);

-- 订单完成后解冻给商家（收付通“完结分账”）
CREATE TABLE lm_settlement (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id       BIGINT        NOT NULL,
  store_id       BIGINT        NOT NULL,
  sub_mch_id     VARCHAR(32)   NULL,
  transaction_id VARCHAR(64)   NULL,
  amount         DECIMAL(10,2) NOT NULL COMMENT '解冻给商家的金额',
  platform_fee   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平台佣金，暂为 0',
  status         VARCHAR(16)   NOT NULL COMMENT 'PENDING_RELEASE/RELEASED/FAILED',
  error          VARCHAR(300)  NULL,
  released_at    DATETIME      NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_settlement_order ON lm_settlement (order_id);
CREATE INDEX idx_settlement_store ON lm_settlement (store_id);
CREATE INDEX idx_settlement_status ON lm_settlement (status);

CREATE TABLE lm_withdraw (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id     BIGINT        NOT NULL,
  sub_mch_id   VARCHAR(32)   NULL,
  out_no       VARCHAR(32)   NOT NULL,
  withdraw_id  VARCHAR(64)   NULL COMMENT '微信提现单号',
  amount       DECIMAL(10,2) NOT NULL,
  status       VARCHAR(16)   NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
  reason       VARCHAR(200)  NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_withdraw_no ON lm_withdraw (out_no);
CREATE INDEX idx_withdraw_store ON lm_withdraw (store_id);

-- ========== 营销 ==========
CREATE TABLE lm_group_buy (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id          BIGINT        NOT NULL,
  product_id        BIGINT        NOT NULL,
  group_price       DECIMAL(10,2) NOT NULL,
  target_qty        INT           NOT NULL,
  current_qty       INT           NOT NULL DEFAULT 0 COMMENT '已付款的数量',
  limit_per_user    INT           NULL,
  end_when_reached  TINYINT       NOT NULL DEFAULT 0 COMMENT '凑够立刻截止',
  end_at            DATETIME      NOT NULL,
  status            VARCHAR(16)   NOT NULL COMMENT 'ONGOING/SUCCESS/WAIT_DECISION/FAILED',
  decision_deadline DATETIME      NULL COMMENT '没凑够时商家决定的截止时间',
  finished_at       DATETIME      NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_group_buy_store ON lm_group_buy (store_id, status);
CREATE INDEX idx_group_buy_status ON lm_group_buy (status, end_at);

-- ========== 评价 / 求购 / 分享 ==========
CREATE TABLE lm_review (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id    BIGINT        NOT NULL,
  store_id    BIGINT        NOT NULL,
  user_id     BIGINT        NOT NULL,
  score       TINYINT       NOT NULL,
  content     VARCHAR(500)  NULL,
  images      VARCHAR(2000) NULL COMMENT 'JSON 数组',
  reply       VARCHAR(500)  NULL,
  reply_at    DATETIME      NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_review_order ON lm_review (order_id);
CREATE INDEX idx_review_store ON lm_review (store_id);

CREATE TABLE lm_favorite (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT   NOT NULL,
  store_id    BIGINT   NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_favorite ON lm_favorite (user_id, store_id);

CREATE TABLE lm_wanted (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id        BIGINT        NOT NULL,
  keyword        VARCHAR(64)   NOT NULL,
  note           VARCHAR(200)  NULL,
  contact_phone  VARCHAR(20)   NULL,
  lat            DECIMAL(10,6) NOT NULL,
  lng            DECIMAL(10,6) NOT NULL,
  status         VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  expire_at      DATETIME      NOT NULL,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wanted_geo ON lm_wanted (status, lat, lng);
CREATE INDEX idx_wanted_user ON lm_wanted (user_id);

CREATE TABLE lm_wanted_reply (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  wanted_id   BIGINT        NOT NULL,
  store_id    BIGINT        NOT NULL,
  price       DECIMAL(10,2) NULL,
  note        VARCHAR(200)  NOT NULL,
  product_id  BIGINT        NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_wanted_reply ON lm_wanted_reply (wanted_id, store_id);

CREATE TABLE lm_share_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  sharer_id    BIGINT       NOT NULL,
  visitor_id   BIGINT       NULL,
  target_type  VARCHAR(16)  NOT NULL COMMENT 'STORE/PRODUCT/GROUP_BUY',
  target_id    BIGINT       NOT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_share_sharer ON lm_share_log (sharer_id);

-- ========== 赊账记账（只记账，不经手资金） ==========
CREATE TABLE lm_credit_customer (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id    BIGINT        NOT NULL,
  name        VARCHAR(32)   NOT NULL,
  phone       VARCHAR(20)   NULL,
  village     VARCHAR(32)   NULL,
  balance     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '还欠多少，> 0 为欠款',
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_credit_customer_store ON lm_credit_customer (store_id);

CREATE TABLE lm_credit_record (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id  BIGINT        NOT NULL,
  store_id     BIGINT        NOT NULL,
  type         VARCHAR(8)    NOT NULL COMMENT 'CREDIT 赊账 / REPAY 还款',
  amount       DECIMAL(10,2) NOT NULL,
  note         VARCHAR(200)  NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_credit_record_customer ON lm_credit_record (customer_id);

-- ========== 运营 ==========
CREATE TABLE lm_admin (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  username       VARCHAR(32)  NOT NULL,
  password_hash  VARCHAR(100) NOT NULL,
  name           VARCHAR(32)  NOT NULL,
  role           VARCHAR(16)  NOT NULL COMMENT 'ADMIN/OPERATOR',
  enabled        TINYINT      NOT NULL DEFAULT 1,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_admin_username ON lm_admin (username);

CREATE TABLE lm_admin_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_id    BIGINT        NOT NULL,
  admin_name  VARCHAR(32)   NOT NULL,
  action      VARCHAR(64)   NOT NULL,
  target      VARCHAR(64)   NULL,
  detail      VARCHAR(500)  NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_admin_log_time ON lm_admin_log (created_at);

CREATE TABLE lm_region (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(32)   NOT NULL,
  lat         DECIMAL(10,6) NOT NULL,
  lng         DECIMAL(10,6) NOT NULL,
  radius_km   DECIMAL(6,1)  NOT NULL,
  enabled     TINYINT       NOT NULL DEFAULT 1,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lm_banner (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  image       VARCHAR(500)  NOT NULL,
  title       VARCHAR(64)   NULL,
  link        VARCHAR(200)  NULL COMMENT '小程序页面路径',
  sort        INT           NOT NULL DEFAULT 0,
  enabled     TINYINT       NOT NULL DEFAULT 1,
  region_id   BIGINT        NULL COMMENT '为空则所有区域显示',
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lm_search_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  keyword       VARCHAR(64)   NOT NULL,
  result_count  INT           NOT NULL,
  user_id       BIGINT        NULL,
  lat           DECIMAL(10,6) NULL,
  lng           DECIMAL(10,6) NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_search_log_time ON lm_search_log (created_at);

CREATE TABLE lm_notify_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel     VARCHAR(16)   NOT NULL COMMENT 'SMS/VOICE/SUBSCRIBE',
  target      VARCHAR(64)   NOT NULL COMMENT '手机号或 openid',
  content     VARCHAR(500)  NULL,
  success     TINYINT       NOT NULL,
  error       VARCHAR(300)  NULL,
  order_id    BIGINT        NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notify_log_order ON lm_notify_log (order_id);
CREATE INDEX idx_notify_log_time ON lm_notify_log (created_at);

-- ========== 搜索同义词 ==========
-- 一行是一组意思相同的词：搜其中任何一个，都会把整组词一起拿去找
CREATE TABLE lm_search_word (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  word        VARCHAR(32)  NOT NULL COMMENT '主词',
  synonyms    VARCHAR(300) NOT NULL COMMENT '同义词，空格分隔（可含拼音缩写）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
