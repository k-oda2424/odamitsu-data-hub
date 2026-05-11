# 受注一覧 入金グループ検索 テスト計画書

| 項目 | 内容 |
| --- | --- |
| 対象機能 | 受注一覧 入金グループ検索 (`partnerGroupId`) |
| 対象画面 | `/orders` |
| 設計書 | `claudedocs/design-order-list-partner-group-filter.md` |
| ブランチ | `refactor/code-review-fixes` |
| 作成日 | 2026-05-11 |

---

## 1. テスト戦略

### 1.1 レイヤー戦略

| レイヤー | 主目的 | フレームワーク | 件数目安 |
| --- | --- | --- | --- |
| Service Unit | partnerGroupId 解決ロジック、認可、空グループ short-circuit | JUnit5 + Mockito | 7 |
| Specification Unit | `partner_code IN (...)` Predicate 生成 / null 安全性 | JUnit5 | 3 |
| Repository Integration | SQL レベルの IN 句動作、NULL 列除外、AND 結合 | dev DB 手動 SQL 代替（CI 未組込） | 4 |
| Controller Slice | パラメータ受領、Service 引数伝播、403 変換、後方互換 | `@WebMvcTest` | 6 |
| E2E (Playwright) | UI ↔ API ↔ DB の通し疎通 | `@playwright/test` + `mockAllApis` | 7（モック） + 1（実バックエンド疎通） |
| 回帰 | 既存検索フォーム / `/finance/invoices` グループ機能 | 既存 spec を不変で走らせる | 既存 spec 全件 PASS |

### 1.2 モック PASS だけで OK と見なさない（CLAUDE.md feedback_incremental_review.md）

- モック E2E は CI でランブル PASS を担保するが、`m_partner_group.partner_codes` と `t_order.partner_code` の一致性は **dev DB に対する手動疎通** で 1 ケース必ず確認する。
- **CI 制約の明示 (テスト計画レビュー P1-1 対応)**: Repository Integration と E2E は現状 CI 未組込。マスタ正規化が将来 6→7 桁にズレた場合 CI 緑のまま回帰するリスクがあるため、マージ前の手動チェック M-8 (EXPLAIN ANALYZE + SQL 抽出証跡) を必須とする。CI への組込は次セッション以降の課題。

### 1.3 優先度ルール

| 優先度 | 定義 |
| --- | --- |
| P0 | データ漏洩 / 認可崩壊 / 既存機能の致命的回帰。マージ前に必ず PASS |
| P1 | 仕様遵守の核（AND 条件、短絡 empty Page、後方互換）。マージ前に PASS |
| P2 | 体験的に必要 (UI disabled, 店舗切替クリア)。マージ前 PASS 望ましい |
| P3 | 将来の保守用 (大量 partnerCodes 性能、tooltip 文言) — リリース後フォローアップ可 |

---

## 2. テストケース一覧

### 2.1 Service Unit (`TOrderDetailService.searchForListPaged`)

ファイル想定: `backend/src/test/java/jp/co/oda32/domain/service/order/TOrderDetailServicePartnerGroupTest.java`

| ID | シナリオ | 期待結果 | 優先度 |
| --- | --- | --- | --- |
| OG-SVC-001 | `partnerGroupId=null`、他条件あり | `MPartnerGroupService.findById` 呼ばれない／既存と同等の Spec が渡る | P1 |
| OG-SVC-002 | `partnerGroupId=999` でグループ未存在 (`findById` が null) | `Page.empty(pageable)` を返す／Repository 未呼出 | P1 |
| OG-SVC-003 | `partnerGroupId=10` で `partnerCodes` 空リスト | `Page.empty(pageable)` を返す／Repository 未呼出 | P1 |
| OG-SVC-004 | `partnerGroupId=10`、グループ shopNo=1、effectiveShopNo=1 | Spec に `IN ('720000',...)` が含まれる Repository 呼出 | P0 |
| OG-SVC-005 | `partnerGroupId=10`、グループ shopNo=2、effectiveShopNo=1 | `AccessDeniedException` を throw／Repository 未呼出 | P0 |
| OG-SVC-006 | `partnerGroupId=10`、effectiveShopNo=null (admin 店舗未選択) | `AccessDeniedException` を throw (設計 §8 R9) | P0 |
| OG-SVC-007 | `partnerGroupId=10` + `partnerNo=12345` 併用 | Spec に IN 句と partnerNo 条件の両方 AND で含まれる | P1 |

### 2.2 Specification Unit (`TOrderDetailSpecification.partnerCodeListContains`)

ファイル想定: `backend/src/test/java/jp/co/oda32/domain/specification/order/TOrderDetailSpecificationTest.java`

| ID | シナリオ | 期待結果 | 優先度 |
| --- | --- | --- | --- |
| OG-SPEC-001 | `partnerCodeListContains(null)` | `null` を返す | P1 |
| OG-SPEC-002 | `partnerCodeListContains(List.of())` | `null` を返す | P1 |
| OG-SPEC-003 | `partnerCodeListContains(List.of("000018","720000"))` | `root.get("tOrder").get("partnerCode").in(...)` Predicate を生成 | P1 |
| OG-SPEC-004 | **partnerCodes 未指定 + B-Cart NULL 行**: partnerGroupId 未指定で `partner_code IS NULL` 行が結果に含まれる（既存挙動の回帰保護 — テスト計画レビュー P1-3 対応） | NULL 行が結果に含まれる | P0 |

### 2.3 Repository Integration (代替: dev DB 手動 SQL)

| ID | シナリオ | 期待結果 | 優先度 |
| --- | --- | --- | --- |
| OG-REPO-001 | `partner_code IN ('000018')` SQL を実行 | 該当 t_order_detail のみ取得 | P1 |
| OG-REPO-002 | `partner_code IS NULL` 行を除外 | IN 句で NULL 行は除外される | P1 |
| OG-REPO-003 | `partnerNo + partnerCodes` AND 検索 | 共通集合のみ取得 | P1 |
| OG-REPO-004 | 500 件の partnerCodes IN 句 | クエリ完了し ≤ 1s | P3 |

### 2.4 Controller Slice (`OrderController#listDetails`)

ファイル想定: `backend/src/test/java/jp/co/oda32/api/order/OrderControllerListDetailsTest.java`（`@WebMvcTest`）

| ID | シナリオ | 期待結果 | 優先度 |
| --- | --- | --- | --- |
| OG-CTL-001 | GET `/orders/details?shopNo=1&partnerGroupId=10` | Service の `searchForListPaged(..., partnerGroupId=10, ...)` が引数で受領される | P1 |
| OG-CTL-002 | GET `/orders/details?shopNo=1` (partnerGroupId 省略) | Service への `partnerGroupId` 引数が `null`／HTTP 200 | P0 (後方互換) |
| OG-CTL-003 | GET `/orders/details?shopNo=1&partnerGroupId=` (空文字) | Spring の Integer バインドで `null` 扱い／200 | P2 |
| OG-CTL-004 | Service が `AccessDeniedException` を throw | HTTP 403 | P0 |
| OG-CTL-005 | Service が `Page.empty()` を返す | HTTP 200／`content=[]`、`totalElements=0` | P1 |
| OG-CTL-006 | `partnerGroupId=abc` (非数値) | HTTP 400 | P2 |

### 2.5 E2E (Playwright)

ファイル想定: `frontend/e2e/order-list-partner-group.spec.ts`

| ID | シナリオ | 期待結果 | 優先度 |
| --- | --- | --- | --- |
| OG-FE-001 | `/orders` 初期表示で「請求先グループ」Label と SearchableSelect が表示 | Label visible / placeholder「グループを選択（任意）」 | P1 |
| OG-FE-002 | admin (shopNo=0) + 店舗未選択でグループ Select が disabled | `disabled` 属性／placeholder「店舗を選択してください」 | P0 |
| OG-FE-003 | admin で店舗切替 (shop=1→shop=2) すると選択中の partnerGroupId がクリア | Select 表示値が空に戻る | P1 |
| OG-FE-004 | グループ選択 → 検索ボタン押下 | `GET /orders/details` に `partnerGroupId=10` が含まれる | P0 |
| OG-FE-005 | グループ未選択で検索 | URL に `partnerGroupId` パラメータが含まれない | P1 (後方互換) |
| OG-FE-006 | リセット押下で partnerGroupId が空に戻る | Select 表示値クリア | P2 |
| OG-FE-007 | 「手入力得意先は対象外」ヘルプテキスト表示 | 該当文言が visible | P3 |
| OG-FE-008 | **非 admin で URL に他店舗 partnerGroupId を直接渡す**（権限昇格 E2E — テスト計画レビュー P1-2 対応） | API が 403、Frontend で ErrorMessage 表示／グループメンバーが結果に出ない | P0 |

### 2.6 実バックエンド疎通

| ID | シナリオ | 期待結果 | 優先度 |
| --- | --- | --- | --- |
| OG-LIVE-001 | dev 環境で curl `partnerGroupId=10` → `shop_no=1` グループ | 返却 `partnerCode` がグループメンバーのみ／NULL 行除外 | P0 |
| OG-LIVE-002 | dev 環境で curl 非 admin 認証 + 他店舗 partnerGroupId | HTTP 403 + 認可エラー（テスト計画レビュー P1-2 対応） | P0 |

### 2.7 回帰テスト

| ID | シナリオ | 期待結果 | 優先度 |
| --- | --- | --- | --- |
| OG-REG-001 | 既存 `order-list.spec.ts` 全テスト | 変化なし | P0 |
| OG-REG-002 | 既存 `invoice.spec.ts` 全テスト | 変化なし | P0 |
| OG-REG-003 | 既存 backend テスト全件 | 変化なし | P0 |

---

## 3. テストデータ準備

### 3.1 Service Unit / Controller Slice

Mockito で `MPartnerGroup` をビルド。DB 不要。

### 3.2 Repository Integration (dev DB 手動代替)

```sql
INSERT INTO m_partner_group (group_name, shop_no, del_flg) VALUES
  ('TestGroupA', 1, '0'),
  ('TestGroupEmpty', 1, '0');
INSERT INTO m_partner_group_member VALUES
  (LASTVAL, '000018'), (LASTVAL, '000019');
-- 既存 t_order の partner_code='000018' 行と一致確認
```

### 3.3 E2E (Playwright)

`frontend/e2e/helpers/mock-api.ts` の `/api/v1/finance/partner-groups` レスポンスに追加:

```ts
{ partnerGroupId: 10, groupName: '受注テスト用グループA', shopNo: 1, partnerCodes: ['000018','000019'] },
{ partnerGroupId: 20, groupName: '空グループ', shopNo: 1, partnerCodes: [] },
{ partnerGroupId: 30, groupName: '他店舗グループ', shopNo: 2, partnerCodes: ['010044'] },
```

---

## 4. E2E ヘルパー / モック追加要否

| 項目 | 現状 | 追加要否 |
| --- | --- | --- |
| `mockAllApis(page)` | `/api/v1/finance/partner-groups` 既存定義あり | 「受注テスト用」グループ追加 |
| `loginAndGoto(page, '/orders')` | 既存ヘルパー対応可 | 不要 |
| 非 admin ユーザーモック | `MOCK_USER.shopNo=0` (admin) | OG-FE-003 で `shopNo=1` に上書き |

---

## 5. CI 影響 / 実行時間見積もり

| 追加テスト | 想定実行時間 | CI ジョブ | 影響 |
| --- | --- | --- | --- |
| Service Unit (7件) | < 1s | `backend-test` | 軽微 |
| Spec Unit (3件) | < 0.5s | `backend-test` | 軽微 |
| Controller Slice (6件) | 数秒 | `backend-test` | +5〜10s |
| Repository IT (4件) | CI 未組込 | dev DB 手動代替 | なし |
| E2E (7件) | 30〜60s | CI 未組込 | ローカル PASS + 手動疎通 |

---

## 6. 手動確認チェックリスト（マージ前必須）

| # | 項目 | 確認方法 |
| --- | --- | --- |
| M-1 | dev backend で `curl 'http://localhost:8090/api/v1/orders/details?shopNo=1&partnerGroupId=10'` が 200 + 該当 partnerCode の行のみ返す | curl + jq |
| M-2 | 同 endpoint で他店舗 group を投げて 403 | curl + ステータスコード |
| M-3 | dev frontend で admin ログイン → `/orders` → 店舗未選択時にグループ Select が disabled | 目視 + DevTools |
| M-4 | 店舗 1 を選択 → グループ Select が enable | 目視 |
| M-5 | グループ選択 → 検索 → Network タブで `partnerGroupId=` が送信 | DevTools Network |
| M-6 | 店舗切替で partnerGroupId が空に戻る | DevTools Network |
| M-7 | 既存 `/finance/invoices` のグループ機能が変化なし | 目視 |
| M-8 | EXPLAIN ANALYZE で `t_order.partner_code IN (...)` のクエリプラン確認 | psql |

---

## 7. マージブロッカー (P0)

- OG-SVC-004 / OG-SVC-005 / OG-SVC-006
- OG-SPEC-004 (B-Cart NULL 行回帰)
- OG-CTL-002 / OG-CTL-004
- OG-FE-002 / OG-FE-004 / OG-FE-008 (非 admin 権限昇格)
- OG-LIVE-001 / OG-LIVE-002 (他店舗 403)
- OG-REG-001 / OG-REG-002 / OG-REG-003
