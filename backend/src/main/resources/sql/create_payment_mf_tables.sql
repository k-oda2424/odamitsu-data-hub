-- 振込み明細Excel → MoneyForward買掛仕入CSV 変換用マスタ/履歴テーブル

-- ルールマスタ（送り先名 or 支払先コードから仕訳属性を決定）
CREATE TABLE IF NOT EXISTS m_payment_mf_rule (
    id                      SERIAL PRIMARY KEY,
    source_name             VARCHAR(200) NOT NULL,   -- 振込明細 B列(送り先名)
    payment_supplier_code   VARCHAR(20),             -- m_payment_supplier.payment_supplier_code（NULLなら固定費行）
    rule_kind               VARCHAR(20)  NOT NULL,   -- 'PAYABLE' | 'EXPENSE' | 'DIRECT_PURCHASE'

    debit_account           VARCHAR(50)  NOT NULL,
    debit_sub_account       VARCHAR(100),
    debit_department        VARCHAR(50),
    debit_tax_category      VARCHAR(30)  NOT NULL,

    credit_account          VARCHAR(50)  NOT NULL DEFAULT '資金複合',
    credit_sub_account      VARCHAR(100),
    credit_department       VARCHAR(50),
    credit_tax_category     VARCHAR(30)  NOT NULL DEFAULT '対象外',

    summary_template        VARCHAR(200) NOT NULL,   -- {sub_account} / {source_name} プレースホルダ対応
    tag                     VARCHAR(100),
    priority                INTEGER      NOT NULL DEFAULT 100,

    del_flg          VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no      INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no   INTEGER
);
CREATE INDEX IF NOT EXISTS idx_payment_mf_rule_source ON m_payment_mf_rule(source_name) WHERE del_flg = '0';
CREATE INDEX IF NOT EXISTS idx_payment_mf_rule_code ON m_payment_mf_rule(payment_supplier_code) WHERE del_flg = '0';

-- 変換履歴
CREATE TABLE IF NOT EXISTS t_payment_mf_import_history (
    id                SERIAL PRIMARY KEY,
    shop_no           INTEGER      NOT NULL,
    transfer_date     DATE         NOT NULL,
    source_filename   VARCHAR(255) NOT NULL,
    csv_filename      VARCHAR(255) NOT NULL,
    row_count         INTEGER      NOT NULL,
    total_amount      BIGINT       NOT NULL,
    matched_count     INTEGER      NOT NULL DEFAULT 0,
    diff_count        INTEGER      NOT NULL DEFAULT 0,
    unmatched_count   INTEGER      NOT NULL DEFAULT 0,
    csv_body          BYTEA,
    del_flg           VARCHAR(1)   NOT NULL DEFAULT '0',
    add_date_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    add_user_no       INTEGER,
    modify_date_time  TIMESTAMP,
    modify_user_no    INTEGER
);
CREATE INDEX IF NOT EXISTS idx_payment_mf_history_date ON t_payment_mf_import_history(transfer_date DESC) WHERE del_flg = '0';

-- ======================================================================
-- Seed: PAYABLE ルール（変換MAPシートから93件）
-- ======================================================================
INSERT INTO m_payment_mf_rule
    (source_name, payment_supplier_code, rule_kind, debit_account, debit_sub_account, debit_department, debit_tax_category,
     credit_account, credit_sub_account, credit_department, credit_tax_category, summary_template, tag, priority)
VALUES
    ('ぬまご', NULL, 'PAYABLE', '買掛金', '（株）ぬまご', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('イトマン ㈱', NULL, 'PAYABLE', '買掛金', 'イトマン（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('オルディ 株式会社', NULL, 'PAYABLE', '買掛金', 'オルディ株式会社', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('カネカ食品販売 ㈱', NULL, 'PAYABLE', '買掛金', 'カネカ食品販売 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('カミイソ産商 ㈱', NULL, 'PAYABLE', '買掛金', 'カミイソ産商（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('カミ商事', NULL, 'PAYABLE', '買掛金', 'カミ商事（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('キンセイ', NULL, 'PAYABLE', '買掛金', '（株）キンセイ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ケイティケイ ㈱', NULL, 'PAYABLE', '買掛金', 'ｋｔｋ ケイティケイ（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('コトブキ製紙', NULL, 'PAYABLE', '買掛金', 'コトブキ製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ゴークラ', NULL, 'PAYABLE', '買掛金', 'ゴークラ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シモジマ', NULL, 'PAYABLE', '買掛金', '（株）シモジマ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シャディ', NULL, 'PAYABLE', '買掛金', 'シャディ（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シルバー化成', NULL, 'PAYABLE', '買掛金', '（有）シルバー化成工業所', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シーバイエス ㈱', NULL, 'PAYABLE', '買掛金', 'シーバイエス（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シーピー化成 ㈱', NULL, 'PAYABLE', '買掛金', 'シービー化成（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ツバメ工業', NULL, 'PAYABLE', '買掛金', 'ツバメ工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ツボイ', NULL, 'PAYABLE', '買掛金', 'ツボイ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('トーヨ', NULL, 'PAYABLE', '買掛金', '（株）トーヨ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ナカパック産業 ㈱', NULL, 'PAYABLE', '買掛金', 'ナカパック産業 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('ハヤシ商事', NULL, 'PAYABLE', '買掛金', 'ハヤシ商事（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ヒノマル', NULL, 'PAYABLE', '買掛金', 'ヒノマル（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('フクヤ', NULL, 'PAYABLE', '買掛金', '（株）フクヤ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('フレンド', NULL, 'PAYABLE', '買掛金', '（株）フレンド', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ホリアキ 株式会社', NULL, 'PAYABLE', '買掛金', 'ホリアキ（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ユニ・チャーム ㈱', NULL, 'PAYABLE', '買掛金', 'ユニ・チャーム(株)', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ララ', NULL, 'PAYABLE', '買掛金', '（株）ララ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ ちどり産業', NULL, 'PAYABLE', '買掛金', '㈱ ちどり産業', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ やしき', NULL, 'PAYABLE', '買掛金', '（株）やしき', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ よし与工房', NULL, 'PAYABLE', '買掛金', '（株）よし与工房', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ アルファ', NULL, 'PAYABLE', '買掛金', '㈱ アルファ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ ササガワ', NULL, 'PAYABLE', '買掛金', '㈱ ササガワ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ スズカ未来', NULL, 'PAYABLE', '買掛金', '（株）スズカ未来', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ セイコー', NULL, 'PAYABLE', '買掛金', '（株）セイコー', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ ニューパック住友', NULL, 'PAYABLE', '買掛金', '㈱ ニューパック住友', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ パックタケヤマ', NULL, 'PAYABLE', '買掛金', '（株）パックタケヤマ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ フォーデック', NULL, 'PAYABLE', '買掛金', '㈱ フォーデック', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ プライム.ハラ', NULL, 'PAYABLE', '買掛金', '㈱ プライム.ハラ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ ベルベ', NULL, 'PAYABLE', '買掛金', '㈱ ベルベ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ ユタカフードパック', NULL, 'PAYABLE', '買掛金', '㈱ ユタカフードパック', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 中国リス食品販売', NULL, 'PAYABLE', '買掛金', '㈱ 中国リス食品販売', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 中村製紙所', NULL, 'PAYABLE', '買掛金', '㈱ 中村製紙所', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 丸三', NULL, 'PAYABLE', '買掛金', '（株）丸三', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ 四国フソウ', NULL, 'PAYABLE', '買掛金', '㈱ 四国フソウ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 大阪包装社', NULL, 'PAYABLE', '買掛金', '（株）大阪包装社', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ 太幸', NULL, 'PAYABLE', '買掛金', '（株）太幸', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ 奈良半商店', NULL, 'PAYABLE', '買掛金', '㈱ 奈良半商店', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 尚美堂', NULL, 'PAYABLE', '買掛金', '（株）尚美堂', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ 福重', NULL, 'PAYABLE', '買掛金', '㈱ 福重', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 藤井包材', NULL, 'PAYABLE', '買掛金', '㈱ 藤井包材', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ ﾊﾗﾌﾟﾚｯｸｽ松山支店', NULL, 'PAYABLE', '買掛金', '（株）ハラプレックス', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈲ タイヨー', NULL, 'PAYABLE', '買掛金', '㈲ タイヨー', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈲ トックブランシュ', NULL, 'PAYABLE', '買掛金', '㈲ トックブランシュ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈲ 東予割箸', NULL, 'PAYABLE', '買掛金', '㈲ 東予割箸', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('三友商事', NULL, 'PAYABLE', '買掛金', '三友商事（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('三昭紙業', NULL, 'PAYABLE', '買掛金', '三昭紙業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('三木商事', NULL, 'PAYABLE', '買掛金', '三木商事（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('三興化学工業', NULL, 'PAYABLE', '買掛金', '三興化学工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('中国鉄管継手㈱', NULL, 'PAYABLE', '買掛金', '中国鉄管継手㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('中川製袋化工 ㈱', NULL, 'PAYABLE', '買掛金', '中川製袋化工（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('中日綿業', NULL, 'PAYABLE', '買掛金', '中日綿業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('信和 ㈱', NULL, 'PAYABLE', '買掛金', '信和 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('信越ファインテック ㈱', NULL, 'PAYABLE', '買掛金', '信越ファインテック（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('八家化学工業 ㈱', NULL, 'PAYABLE', '買掛金', '八家化学工業 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('八幡浜紙業', NULL, 'PAYABLE', '買掛金', '八幡浜紙業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('前谷紙工業', NULL, 'PAYABLE', '買掛金', '前谷紙工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('大高製紙', NULL, 'PAYABLE', '買掛金', '大高製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('大黒工業㈱', NULL, 'PAYABLE', '買掛金', '大黒工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('天満紙器 株式会社', NULL, 'PAYABLE', '買掛金', '天満紙器（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('広島共和物産㈱', NULL, 'PAYABLE', '買掛金', '広島共和物産（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('広川 ㈱', NULL, 'PAYABLE', '買掛金', '広川 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('日本製紙クレシア', NULL, 'PAYABLE', '買掛金', '日本製紙クレシア（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('旭創業 ㈱', NULL, 'PAYABLE', '買掛金', '（株）旭創業', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('木村アルミ箔 ㈱', NULL, 'PAYABLE', '買掛金', '木村アルミ箔（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('木野川紙業 株式会社', NULL, 'PAYABLE', '買掛金', '木野川紙業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('東光 株式会社', NULL, 'PAYABLE', '買掛金', '（株）東光', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('東芝エルイーソリューション㈱', NULL, 'PAYABLE', '買掛金', '東芝エルイーソリューション㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('松田製袋', NULL, 'PAYABLE', '買掛金', '（株）松田製袋', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('柳井紙工', NULL, 'PAYABLE', '買掛金', '柳井紙工（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('森川 ㈱', NULL, 'PAYABLE', '買掛金', '森川（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('河野製紙', NULL, 'PAYABLE', '買掛金', '河野製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('泉 製紙 ㈱', NULL, 'PAYABLE', '買掛金', '泉製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('王子ネピア 広島', NULL, 'PAYABLE', '買掛金', '王子ネピア（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('王子ネピア 直需', NULL, 'PAYABLE', '買掛金', '王子ネピア（直需）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('王子製袋', NULL, 'PAYABLE', '買掛金', '王子製袋（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('田宮事務機', NULL, 'PAYABLE', '買掛金', '（株）田宮事務器', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('福助工業㈱', NULL, 'PAYABLE', '買掛金', '福助工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('立石春洋堂', NULL, 'PAYABLE', '買掛金', '（株）立石春洋堂', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('花王 業務品 KPS', NULL, 'PAYABLE', '買掛金', '花王プロフェショナルｻｰﾋﾞｽ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('花王 業務品 KPS施設', NULL, 'PAYABLE', '買掛金', '花王 業務品 KPS施設', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('西日本衛材', NULL, 'PAYABLE', '買掛金', '西日本衛材（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('酒井美化工業 ㈱', NULL, 'PAYABLE', '買掛金', '酒井美化工業 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('金星製紙', NULL, 'PAYABLE', '買掛金', '金星製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ＡＲＣ', NULL, 'PAYABLE', '買掛金', 'ＡＲＣ（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100);

-- ======================================================================
-- Seed: 固定費ルール（EXPENSE / DIRECT_PURCHASE, 過去CSVから抽出）
-- ======================================================================
INSERT INTO m_payment_mf_rule
    (source_name, payment_supplier_code, rule_kind, debit_account, debit_sub_account, debit_department, debit_tax_category,
     credit_account, credit_sub_account, credit_department, credit_tax_category, summary_template, tag, priority)
VALUES
    -- 運賃（課税仕入10%）
    ('福山通運',                   NULL, 'EXPENSE', '荷造運賃', NULL, '物販事業部', '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 運賃', 100),
    ('福山通運 第３事業部',        NULL, 'EXPENSE', '荷造運賃', NULL, '物販事業部', '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('サクマ運輸㈱',               NULL, 'EXPENSE', '荷造運賃', NULL, '物販事業部', '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 運賃', 100),
    ('ティックトランスポート㈲',   NULL, 'EXPENSE', '荷造運賃', NULL, NULL,         '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 運賃', 100),
    -- 仕入高（その他セクション）
    ('ヨハネ印刷㈱',               NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 仕入 ', 100),
    ('㈱ ナカガワ',                NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 仕入 ', 100),
    ('中国エンゼル㈱',             NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 仕入 ', 100),
    ('㈱ ビバ',                    NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    -- 消耗品費 / 車両費
    ('リコージャパン㈱',           NULL, 'EXPENSE', '消耗品費', NULL, NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 事務用品費', 100),
    ('広島トヨペット㈱',           NULL, 'EXPENSE', '車両費',   NULL, NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', '車両費', 100),
    -- 20日払いセクション（仕入高・課税仕入10%）
    ('ワタキューセイモア㈱',       NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('ハウスホールドジャパン㈱',   NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('シンワ ㈱',                  NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈲ シルバー化成工業所',      NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('アスト株式会社',             NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100);
