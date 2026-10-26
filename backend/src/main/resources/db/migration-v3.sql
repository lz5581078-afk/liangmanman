-- 第三批：商品库具体到每一款（条码 / 登记证号）、搜索同义词表
-- 已经按 schema.sql 或 migration-v2.sql 建过库的执行这个；新库直接用 schema.sql。MySQL 8

ALTER TABLE lm_standard_product MODIFY COLUMN category_id BIGINT NULL;
ALTER TABLE lm_standard_product ADD COLUMN barcode VARCHAR(32) NULL COMMENT '商品条码' AFTER reg_no;
ALTER TABLE lm_standard_product ADD COLUMN manufacturer VARCHAR(64) NULL COMMENT '生产企业' AFTER barcode;
ALTER TABLE lm_standard_product ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'VERIFIED' AFTER keywords;
ALTER TABLE lm_standard_product ADD COLUMN source_store_id BIGINT NULL AFTER status;
CREATE INDEX idx_std_barcode ON lm_standard_product (barcode);

ALTER TABLE lm_product ADD COLUMN brand VARCHAR(32) NULL AFTER name;
ALTER TABLE lm_product ADD COLUMN spec VARCHAR(32) NULL AFTER brand;

CREATE TABLE lm_search_word (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  word        VARCHAR(32)  NOT NULL,
  synonyms    VARCHAR(300) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 旧版本预置的“通用标准品”（没有品牌、条码、登记证号）不能代表具体一款：
-- 把它们的同义词搬进同义词表，再解除商品关联、删除。若已有运营按具体款补录的数据，请先确认再执行下面三句。
INSERT INTO lm_search_word (word, synonyms)
  SELECT name, COALESCE(keywords, name) FROM lm_standard_product
  WHERE brand IS NULL AND reg_no IS NULL AND barcode IS NULL;
UPDATE lm_product SET standard_product_id = NULL, normalize_status = 'PENDING'
  WHERE standard_product_id IN (SELECT id FROM (SELECT id FROM lm_standard_product
        WHERE brand IS NULL AND reg_no IS NULL AND barcode IS NULL) t);
DELETE FROM lm_standard_product WHERE brand IS NULL AND reg_no IS NULL AND barcode IS NULL;
