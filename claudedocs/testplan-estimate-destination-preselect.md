# テスト計画書: 見積編集画面 納品先プリセレクト修正

**日付**: 2026-05-01
**ブランチ**: refactor/code-review-fixes
**関連設計書**: `claudedocs/design-estimate-destination-preselect.md`
**対象機能**: 見積編集画面で保存済み納品先 (`destinationNo`) を SearchableSelect に選択状態で表示する

---

## 1. 概要

### 目的
見積修正画面 (`/estimates/{no}/edit`) において、見積に保存済みの `destinationNo` が SearchableSelect の選択状態として表示されることを保証する。特に、削除済みや partner 紐づけ変更などで `destinationsQuery.data` から外れているケースでも label が表示されることを検証する。

### スコープ
- **対象**:
  - Backend: `EstimateResponse.destinationCode` フィールド追加と `from()` / `fromWithDetails()` での値伝播
  - Frontend: `EstimateHeaderForm` の `destinationFallback` prop と dedup 付き prepend ロジック
  - Frontend: `form.tsx` から edit mode 限定で fallback を渡す制御
- **対象外**:
  - 作成画面 (`/estimates/create`) における `sessionStorage('estimate-prefill')` 経由の prefill flow (v1 方針により対象外)
  - 「削除済み」ラベル表示などの v2 機能

---

## 2. テスト対象範囲

| レイヤー | 対象ファイル |
|---|---|
| Backend DTO | `backend/src/main/java/jp/co/oda32/dto/estimate/EstimateResponse.java` |
| Frontend Type | `frontend/types/estimate.ts` |
| Frontend Component | `frontend/components/pages/estimate/EstimateHeaderForm.tsx` |
| Frontend Page | `frontend/components/pages/estimate/form.tsx` |
| E2E モック | `frontend/e2e/helpers/mock-api.ts` (`MOCK_ESTIMATES`, `MOCK_DESTINATIONS`) |
| E2E スペック | `frontend/e2e/estimate-form.spec.ts` (F-09 / F-10 / F-11 / F-12 / F-13 を追加) |

---

## 3. 前提条件

### 共通
- Playwright Chromium headless
- `mockAllApis(page)` + `loginAndGoto(page, '/path')` パターン
- TypeScript strict、`tsc --noEmit` を含めて全 PASS

### モックデータ (E2E)
新規エントリを `MOCK_ESTIMATES` に追加 (既存は `destinationNo: 0` のため不足):

| キー | 値 | 用途 |
|---|---|---|
| `MOCK_ESTIMATE_WITH_VALID_DEST` | `destinationNo: 1`, `destinationName: '本社'`, `destinationCode: 'DEST001'` | ケース (a)(c)(g) |
| `MOCK_ESTIMATE_WITH_DELETED_DEST` | `destinationNo: 999`, `destinationName: '旧倉庫(削除済)'`, `destinationCode: 'DEST999'` | ケース (b) |
| `MOCK_ESTIMATE_WITHOUT_DEST` | `destinationNo: null` または `0`, `destinationName: null`, `destinationCode: null` | ケース (d) |

`MOCK_DESTINATIONS` (現状 `destinationNo: 1, 2` のみ) は変更不要。`destinationNo: 999` は含めないことで「削除済み」状況を再現する。

### ユーザー
- 既存の `loginAndGoto` ヘルパーが使う dev ユーザー (admin)。`shopNo === 0` 想定だが本機能には影響なし。

### Java Unit
- 既存 JUnit/Spring Test 構成下に `EstimateResponseTest`(新規) を作成。`MEstimate` / `MDeliveryDestination` はビルダーまたは直接 `new` でフィクスチャを組む。

---

## 4. テストケース一覧

| ID | 種別 | 前提 | 操作 | 期待結果 | 重要度 |
|---|---|---|---|---|---|
| T-01 | Unit (Java) | `MEstimate` に `mDeliveryDestination` をセット (`destinationCode='DEST001'`, `destinationName='本社'`) | `EstimateResponse.from(estimate)` を呼ぶ | 戻り値の `destinationCode == 'DEST001'`, `destinationName == '本社'`, `destinationNo == 1` | Critical |
| T-02 | Unit (Java) | `MEstimate.mDeliveryDestination = null` (destination 未設定) | `EstimateResponse.from(estimate)` を呼ぶ | `destinationCode == null`, `destinationName == null`, `destinationNo == null` (NPE が発生しない) | Critical |
| T-03 | Unit (Java) | `MEstimate` に details + `mDeliveryDestination` + `isIncludeTaxDisplay=true` をセット | `EstimateResponse.fromWithDetails(estimate)` を呼ぶ | `destinationCode` が `from()` ケースと同値で伝播。`details` が空でないこと。`isIncludeTaxDisplay == true`（PDF 制御フラグの伝播保証） | Major |
| T-04 | E2E (a) | `GET /estimates/{no}` レスポンスで `destinationNo: 1`, `destinationCode: 'DEST001'`, `destinationName: '本社'`。`GET /masters/destinations?partnerNo=X` が `MOCK_DESTINATIONS` (1, 2 含む) を返す | `/estimates/{no}/edit` を開く | SearchableSelect のトリガーボタンに `DEST001 本社` がラベル表示される | Critical |
| T-05 | E2E (b) | `GET /estimates/{no}` レスポンスで `destinationNo: 999`, `destinationCode: 'DEST999'`, `destinationName: '旧倉庫(削除済)'`。`GET /masters/destinations?partnerNo=X` レスポンスは 999 を含まない（**設計書 §2 シナリオ A=削除済 と B=partner 紐づけ変更 はバックエンド API の挙動が同一— 保存済み destinationNo が destinations API の戻り値に含まれない— であり、本ケースで両シナリオを同時カバー**） | `/estimates/{no}/edit` を開く | fallback option により `DEST999 旧倉庫(削除済)` がラベル表示される。プルダウンを開くと先頭に同 entry が出現し、後続に `DEST001 本社`, `DEST002 第二倉庫` が並ぶ | Critical |
| T-05b | E2E (b 明示版) | `GET /estimates/{no}` で `destinationNo: 1`, `partnerNo: 108`, `destinationCode: 'DEST001'`。`GET /masters/destinations?partnerNo=108` 専用ハンドラで `[]` を返す (例: partner 紐づけ変更で当該 partner には 0 件) | `/estimates/{no}/edit` を開く | T-05 と同様 fallback option `DEST001 本社` がトリガーボタンに表示。プルダウンには fallback のみ表示 | Major |
| T-06 | E2E (c) | `GET /estimates/{no}` で `destinationNo: 1`。`GET /masters/destinations?partnerNo=X` も `destinationNo: 1` を含む | `/estimates/{no}/edit` を開きプルダウンを開く | `DEST001 本社` の option が **重複しない** (出現回数 = 1)。dedup ロジックが機能 | Major |
| T-07 | E2E (d) | `page.route` で `GET /api/v1/estimates/{no}` を **個別オーバーライド**し `destinationNo: null`, `destinationName: null`, `destinationCode: null` を返す（`MOCK_ESTIMATES[0]` への偶発依存を断つため） | `/estimates/{no}/edit` を開きプルダウンを開く | fallback option が注入されず、placeholder (例: 「納品先を選択」) が表示される。option リストは `MOCK_DESTINATIONS` のみ。これにより `(estimateQuery.data.destinationNo ?? 0) > 0` ガードの動作を保証 | Major |
| T-08 | E2E (e) | T-04 の状態 (destinationNo=1 で edit) | プルダウンから `DEST002 第二倉庫` を選択し保存ボタン押下 | `PUT /estimates/{no}` リクエスト body に `destinationNo: 2` が含まれる | Critical |
| T-09 | E2E (e) | T-05 の状態 (destinationNo=999 削除済 で edit) | プルダウンから `DEST002 第二倉庫` を選択し保存 | `PUT /estimates/{no}` の body に `destinationNo: 2` が含まれる (fallback option に戻らない) | Major |
| T-10 | E2E (f) | `/estimates/create` を開く。`GET /masters/destinations` レスポンス空 `[]`、prefill なし | partner を選択 → 納品先プルダウンを開く | placeholder のみ表示。fallback option は注入されない (既存動作維持) | Major |
| T-11 | E2E 既存 | F-08 (既存テスト) | F-08 を実行 | F-08 が引き続き PASS。**回帰目的のみ** — fallback 非注入の積極保証は T-07 が担う | Major |
| T-12 | 手動疎通 | バックエンド再起動済み、DB 上に `destination_no` 設定済みの実見積が存在 | 5 章手順に従って実画面を確認 | label がプリセレクト表示され、ネットワークタブで `destinationCode` が JSON に含まれる | Critical |

### 補足: Selector メモ
- SearchableSelect トリガー: `button:has-text("納品先を選択")` または `getByRole('combobox', { name: /納品先/ })`
- 選択中 label の表示確認: トリガーボタンの内側に `${code} ${name}` (例: `DEST001 本社`) が見える
- option 重複カウント: `await page.getByRole('option', { name: 'DEST001 本社' }).count()` が `1` であること

---

## 5. 実バックエンド疎通確認手順

CLAUDE.md ルール「実バックエンド疎通を最低 1 パス」に従い、モック PASS のみで完了させない。

### 手順 1: テスト用見積の特定
```sql
-- destination_no が設定されている見積を1件選ぶ
SELECT
  e.shop_no, e.estimate_no, e.partner_no, e.destination_no,
  d.destination_code, d.destination_name, d.del_flg
FROM t_estimate e
LEFT JOIN m_delivery_destination d
  ON e.shop_no = d.shop_no AND e.destination_no = d.destination_no
WHERE e.destination_no IS NOT NULL AND e.destination_no <> 0
  AND e.del_flg = '0'
ORDER BY e.add_date_time DESC
LIMIT 5;
```

### 手順 2: バックエンド再起動
- DTO 変更のためバックエンド再起動が必須。`./gradlew bootRun --args='--spring.profiles.active=web,dev'`

### 手順 3: curl で API レスポンスを目視確認
```bash
# JWT ログイン後 (Cookie or Bearer 取得済み前提)
curl -s -H "Cookie: <auth>" http://localhost:8090/api/v1/estimates/{shop_no}/{estimate_no} \
  | jq '{destinationNo, destinationCode, destinationName}'
```
**期待**: `destinationCode` フィールドが含まれており、DB の `m_delivery_destination.destination_code` と一致

### 手順 4: ブラウザで edit 画面を目視確認
1. `http://localhost:3000/estimates/{shop_no}/{estimate_no}/edit` を開く
2. 「納品先」プルダウンに `${destination_code} ${destination_name}` が表示されている
3. プルダウンを開いて先頭に同一 entry が重複していないこと (dedup 動作)
4. 別の納品先に切り替え → 保存 → 詳細画面に戻り、選択した納品先が反映されていること

### 手順 5: 削除済みケースの再現 (任意)
```sql
-- 一時的に該当 destination を論理削除
UPDATE m_delivery_destination SET del_flg = '1'
WHERE shop_no = ? AND destination_no = ?;
```
画面再読込で fallback option による label 表示を確認後、必ず元に戻す:
```sql
UPDATE m_delivery_destination SET del_flg = '0' WHERE shop_no = ? AND destination_no = ?;
```

---

## 6. リスクと対策

| リスク | 影響 | 対策 |
|---|---|---|
| バックエンド再起動忘れによるレスポンス不整合 | フロントが `destinationCode` を `undefined` 扱いし fallback label が崩れる (` 本社`) | 設計書通りユーザーに再起動依頼を明示。手順 3 で curl 疎通必須 |
| `m_delivery_destination` の物理 join が `e.destination_no = 0` で誤った行を引く | 既存仕様で発生しないが追加した destinationCode が誤値になる懸念 | `MEstimate.mDeliveryDestination` の relation 定義 (PK + del_flg) を変更しない。T-02 で null 安全性を保証 |
| dedup 比較が型不一致で失敗 (`String(1) !== '1'` etc.) | 重複 option が表示される | T-06 で重複 1 件のみを assert。実装で `String(d.destinationNo)` と `String(destinationFallback.destinationNo)` を統一 |
| MOCK の `destinationNo: null` (T-07) で TypeScript 型エラー | テスト追加が即コンパイル失敗 | `EstimateResponse` 型は既に `destinationNo: number \| null` として宣言されているため OK。新規 mock 追加時に確認 |
| prefill 経由 (`/estimates/create?prefill=...`) で `destinationCode` 不在 | 作成画面で fallback が動かない | v1 方針通り対象外 (設計書 §3 補足)。F-10 相当のテストは作らない |

---

## 7. 完了条件

以下をすべて満たした時点で本機能の検証完了とする。

1. **Backend Unit (T-01〜T-03)** すべて PASS
   - `./gradlew test --tests EstimateResponseTest`
2. **Frontend tsc** エラーなし
   - `cd frontend && npx tsc --noEmit`
3. **Frontend compile (Java 側)** エラーなし
   - `cd backend && ./gradlew compileJava`
4. **E2E (T-04〜T-11)** すべて PASS
   - `cd frontend && npx playwright test estimate-form.spec.ts`
5. **既存 E2E** 回帰なし (F-01〜F-08)
6. **手動疎通 (T-12)** で実 DB 由来の見積で label プリセレクト表示を確認。curl レスポンスに `destinationCode` 含有を確認
7. ブランチ `refactor/code-review-fixes` に commit 済み、ユーザーへ JVM 再起動依頼を明示
