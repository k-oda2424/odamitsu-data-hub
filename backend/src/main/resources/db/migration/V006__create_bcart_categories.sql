-- B-CARTカテゴリマスタ
CREATE TABLE IF NOT EXISTS b_cart_categories (
  id                  INTEGER      PRIMARY KEY,
  name                VARCHAR(255) NOT NULL,
  description         TEXT,
  rv_description      TEXT,
  parent_category_id  INTEGER,
  header_image        VARCHAR(255),
  banner_image        VARCHAR(255),
  menu_image          VARCHAR(255),
  meta_title          VARCHAR(255),
  meta_keywords       VARCHAR(500),
  meta_description    VARCHAR(500),
  priority            INTEGER      NOT NULL DEFAULT 0,
  flag                INTEGER      NOT NULL DEFAULT 1,
  b_cart_reflected    BOOLEAN      NOT NULL DEFAULT TRUE,
  version             INTEGER      NOT NULL DEFAULT 0,
  created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bcart_categories_parent ON b_cart_categories(parent_category_id);

COMMENT ON TABLE b_cart_categories IS 'B-CARTカテゴリマスタ';
COMMENT ON COLUMN b_cart_categories.flag IS '1=表示, 0=非表示（B-CART APIではINT型）';
COMMENT ON COLUMN b_cart_categories.b_cart_reflected IS 'TRUE=B-CARTと同期済み, FALSE=ローカル変更あり（未反映）';

-- B-CART変更履歴（バックアップ）
CREATE TABLE IF NOT EXISTS b_cart_change_history (
  id                  BIGSERIAL    PRIMARY KEY,
  target_type         VARCHAR(30)  NOT NULL,
  target_id           BIGINT       NOT NULL,
  change_type         VARCHAR(20)  NOT NULL,
  field_name          VARCHAR(100),
  before_value        TEXT,
  after_value         TEXT,
  before_snapshot     JSONB,
  change_reason       VARCHAR(500),
  changed_by          INTEGER      NOT NULL,
  changed_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  b_cart_reflected    BOOLEAN      NOT NULL DEFAULT FALSE,
  b_cart_reflected_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bcart_history_target ON b_cart_change_history(target_type, target_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_bcart_history_unreflected ON b_cart_change_history(b_cart_reflected) WHERE b_cart_reflected = FALSE;

COMMENT ON TABLE b_cart_change_history IS 'B-CART商品/カテゴリ変更履歴（バックアップ）';
COMMENT ON COLUMN b_cart_change_history.target_type IS 'PRODUCT, PRODUCT_SET, CATEGORY';
COMMENT ON COLUMN b_cart_change_history.change_type IS 'PRICE, DESCRIPTION, STATUS, CATEGORY, BULK';
