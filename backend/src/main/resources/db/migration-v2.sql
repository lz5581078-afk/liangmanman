-- 从第一批升级到第二批（已经用第一批 schema.sql 建过库的才需要执行；新库直接用 schema.sql）
-- MySQL 8

ALTER TABLE lm_store ADD COLUMN status_reason VARCHAR(200) NULL AFTER status;
ALTER TABLE lm_store ADD COLUMN review_count INT NOT NULL DEFAULT 0 AFTER rating;

ALTER TABLE lm_store_license ADD COLUMN reject_reason VARCHAR(200) NULL;
CREATE INDEX idx_license_status ON lm_store_license (status);

ALTER TABLE lm_merchant_account ADD COLUMN applyment_id VARCHAR(32) NULL AFTER applyment_no;
ALTER TABLE lm_merchant_account ADD COLUMN id_name_masked VARCHAR(32) NULL AFTER bank_card_masked;
ALTER TABLE lm_merchant_account ADD COLUMN sign_url VARCHAR(500) NULL AFTER id_name_masked;
ALTER TABLE lm_merchant_account MODIFY COLUMN reject_reason VARCHAR(500) NULL;

CREATE TABLE lm_private_image (
  id          VARCHAR(32)  NOT NULL PRIMARY KEY,
  store_id    BIGINT       NOT NULL,
  path        VARCHAR(500) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE lm_product ADD COLUMN off_reason VARCHAR(200) NULL AFTER on_shelf;
ALTER TABLE lm_product ADD COLUMN search_text VARCHAR(600) NULL AFTER normalize_status;

ALTER TABLE lm_order ADD COLUMN group_buy_id BIGINT NULL AFTER store_id;
ALTER TABLE lm_order ADD COLUMN reviewed TINYINT NOT NULL DEFAULT 0 AFTER cod_received;
ALTER TABLE lm_order ADD COLUMN remind_count INT NOT NULL DEFAULT 0 AFTER reviewed;
CREATE INDEX idx_order_group ON lm_order (group_buy_id);

ALTER TABLE lm_payment ADD COLUMN sub_mch_id VARCHAR(32) NULL AFTER channel;
ALTER TABLE lm_refund ADD COLUMN refund_id VARCHAR(64) NULL AFTER out_refund_no;
CREATE INDEX idx_refund_order ON lm_refund (order_id);

ALTER TABLE lm_settlement ADD COLUMN sub_mch_id VARCHAR(32) NULL AFTER store_id;
ALTER TABLE lm_settlement ADD COLUMN transaction_id VARCHAR(64) NULL AFTER sub_mch_id;
ALTER TABLE lm_settlement ADD COLUMN error VARCHAR(300) NULL AFTER status;
ALTER TABLE lm_settlement ADD COLUMN released_at DATETIME NULL AFTER error;
CREATE UNIQUE INDEX uk_settlement_order ON lm_settlement (order_id);
CREATE INDEX idx_settlement_status ON lm_settlement (status);

ALTER TABLE lm_withdraw ADD COLUMN sub_mch_id VARCHAR(32) NULL AFTER store_id;
ALTER TABLE lm_withdraw ADD COLUMN withdraw_id VARCHAR(64) NULL AFTER out_no;
ALTER TABLE lm_withdraw ADD COLUMN reason VARCHAR(200) NULL AFTER status;
ALTER TABLE lm_withdraw ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE UNIQUE INDEX uk_withdraw_no ON lm_withdraw (out_no);

ALTER TABLE lm_group_buy ADD COLUMN end_when_reached TINYINT NOT NULL DEFAULT 0 AFTER limit_per_user;
ALTER TABLE lm_group_buy ADD COLUMN decision_deadline DATETIME NULL AFTER status;
ALTER TABLE lm_group_buy ADD COLUMN finished_at DATETIME NULL AFTER decision_deadline;
CREATE INDEX idx_group_buy_status ON lm_group_buy (status, end_at);
DROP TABLE IF EXISTS lm_group_buy_member;

ALTER TABLE lm_review ADD COLUMN images VARCHAR(2000) NULL AFTER content;
ALTER TABLE lm_review ADD COLUMN reply_at DATETIME NULL AFTER reply;
CREATE UNIQUE INDEX uk_review_order ON lm_review (order_id);

ALTER TABLE lm_wanted ADD COLUMN note VARCHAR(200) NULL AFTER keyword;
ALTER TABLE lm_wanted ADD COLUMN contact_phone VARCHAR(20) NULL AFTER note;
ALTER TABLE lm_wanted ADD COLUMN expire_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER status;
ALTER TABLE lm_wanted MODIFY COLUMN lat DECIMAL(10,6) NOT NULL;
ALTER TABLE lm_wanted MODIFY COLUMN lng DECIMAL(10,6) NOT NULL;
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

CREATE INDEX idx_share_sharer ON lm_share_log (sharer_id);

CREATE TABLE lm_credit_customer (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  store_id    BIGINT        NOT NULL,
  name        VARCHAR(32)   NOT NULL,
  phone       VARCHAR(20)   NULL,
  village     VARCHAR(32)   NULL,
  balance     DECIMAL(12,2) NOT NULL DEFAULT 0,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_credit_customer_store ON lm_credit_customer (store_id);

CREATE TABLE lm_credit_record (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id  BIGINT        NOT NULL,
  store_id     BIGINT        NOT NULL,
  type         VARCHAR(8)    NOT NULL,
  amount       DECIMAL(10,2) NOT NULL,
  note         VARCHAR(200)  NULL,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_credit_record_customer ON lm_credit_record (customer_id);

CREATE TABLE lm_admin (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  username       VARCHAR(32)  NOT NULL,
  password_hash  VARCHAR(100) NOT NULL,
  name           VARCHAR(32)  NOT NULL,
  role           VARCHAR(16)  NOT NULL,
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
  link        VARCHAR(200)  NULL,
  sort        INT           NOT NULL DEFAULT 0,
  enabled     TINYINT       NOT NULL DEFAULT 1,
  region_id   BIGINT        NULL,
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
  channel     VARCHAR(16)   NOT NULL,
  target      VARCHAR(64)   NOT NULL,
  content     VARCHAR(500)  NULL,
  success     TINYINT       NOT NULL,
  error       VARCHAR(300)  NULL,
  order_id    BIGINT        NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notify_log_order ON lm_notify_log (order_id);
CREATE INDEX idx_notify_log_time ON lm_notify_log (created_at);
