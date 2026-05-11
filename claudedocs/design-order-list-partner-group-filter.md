# 受注一覧 入金グループ検索 設計書

| 項目 | 内容 |
| --- | --- |
| 機能名 | 受注一覧 入金グループ検索 |
| 対象画面 | `/orders` (受注一覧) |
| 関連画面 | `/finance/invoices` (請求書一覧) |
| ブランチ | `refactor/code-review-fixes`（作業継続） |
| 作成日 | 2026-05-11 |
| 作成者 | Claude (Opus 4.7) |

## 1. Background / Problem

### 現状
- 請求書一覧 (`/finance/invoices`) では `m_partner_group` （入金グループ）を使い、複数の得意先をまとめて検索 / 一括処理できる。
- 受注一覧 (`/orders`) では、得意先は `SearchableSelect` による **単一選択** のみで、複数得意先 / 取引グループ単位での絞り込みができない。
- 結果として「Aグループ得意先の当月受注をまとめて確認したい」という運用ニーズに応えるには、得意先を1社ずつ切り替えるか、Excelで突き合わせる必要がある。

### 解決したいこと
- 受注一覧でも請求書一覧と同じ `m_partner_group` を使い、グループ単位で受注明細を絞り込む。
- マスタは追加せず、既存の入金グループ定義をそのまま流用する（一覧画面間で意味が一致する）。

## 2. Requirements

### 2.1 機能要件 (FR)

| ID | 要件 |
| --- | --- |
| FR-1 | 受注一覧の検索フォームに「グループ」SearchableSelect を追加する。 |
| FR-2 | グループの選択肢は `GET /api/v1/finance/partner-groups?shopNo={effectiveShopNo}` を流用する。 |
| FR-3 | 「グループ」を選択した状態で検索すると、当該グループに属する `partnerCode` の受注明細のみが返る。 |
| FR-4 | 既存の `得意先 (partnerNo)` 単独検索条件と併用された場合は **AND** で絞り込む。 |
| FR-5 | 店舗（shopNo）変更時に、選択中の `partnerGroupId` はクリアする（partnerNo と同じ挙動）。 |
| FR-6 | グループが0件、または選択グループが削除済の場合は「マッチなし」として空配列を返す（例外を返さない）。 |
| FR-7 | sessionStorage への保持は既存の `useSearchParamsStorage('order-list-search', ...)` に乗せる（URL クエリ反映は別タスク）。 |

### 2.2 非機能要件 (NFR)

| ID | 要件 |
| --- | --- |
| NFR-1 | パフォーマンス: `m_partner_group_member` の partner_code 列数は実運用で最大 **数百件** を想定。`IN ( ... )` 句で SQL 直接フィルタ。クライアント側ではフィルタしない。 |
| NFR-2 | 認可: `partnerGroupId` の shopNo と request の effective shopNo が一致しない場合は 403 (FinanceController `/partner-groups` と同じ方針)。 |
| NFR-3 | 既存 API (`/orders/details`) を後方互換で維持。`partnerGroupId` は **optional**。 |
| NFR-4 | 既存 partner_group マスタの正規化（6桁0埋め）に依存せず、`t_order.partner_code` 実値で一致比較する。 |
| NFR-5 | レスポンス時間: 既存検索 + 1〜数十 ms の `IN` 句 → 体感影響なし。 |

## 3. Constraints

### 3.1 技術制約

- **DB スキーマ変更なし** — `m_partner_group` / `m_partner_group_member` / `t_order` 既存テーブル流用。Flyway migration 不要。
- **既存正規化規則**: `MPartnerGroupService.normalizeAndDedup` は partner_code を `String.format("%06d", numericCode)` で 6桁0埋め保持。`t_order.partner_code` 側も **6桁0埋め文字列**（dev DB 実測で確認済 — §11参照）。両者の表現が一致するため、追加正規化なしで `IN` 句比較する。
- **JPA Specification 拡張**: 既存 `TOrderDetailSpecification` に partner_code リスト IN 句のメソッドを追加する。

### 3.2 業務制約

- 入金グループは「請求書を一括処理するための単位」だが、受注一覧でも同じ単位で見たいというニーズが多い → 流用に違和感なし（取引先まとまり）。
- グループのメンテナンスは引き続き請求書一覧の `PartnerGroupDialog` から行う（受注一覧側に追加メンテ UI は作らない）。
- **重要前提（Codex 指摘 P1→P2/P3 ダウングレード）**: SMILE 連携データには `tokuisaki_code`（注文者）と `seikyusaki_code`（請求先）の2系統が存在する。`TOrder.partnerCode` は **tokuisaki_code** 由来、`m_partner_group_member.partner_code` は **請求書由来 = seikyusaki_code** ベース。

  **実 DB 全件確認結果（2026-05-11）**:
  ```sql
  SELECT count(DISTINCT o.partner_code) AS distinct_order_codes,        -- 1331
         count(DISTINCT i.partner_code) AS distinct_invoice_codes,      -- 338
         count(DISTINCT o.partner_code) FILTER (WHERE i.partner_code IS NOT NULL) AS matched
                                                                         -- 338
  FROM t_order o LEFT JOIN t_invoice i ON i.partner_code = o.partner_code
  WHERE o.partner_code IS NOT NULL;
  ```
  - **t_invoice.partner_code (338) ⊆ t_order.partner_code (1331)** = **全件マッチ**（mismatch ゼロ）
  - t_order > t_invoice の差分 993 件は「請求書未発行の受注先」で、これらは `m_partner_group_member` にも未登録（グループ加入は請求書一覧から行うため）
  - したがって `m_partner_group_member.partner_code` を `t_order.partner_code` と IN 比較しても **表現の不一致による欠落は発生しない**

  本設計は **「現状運用では tokuisaki ≡ seikyusaki」を前提に reuse する**。将来「注文者 ≠ 請求先」（親子請求等）の運用が広がった場合は、本機能のセマンティクスが破綻するため再設計が必要（§8 R8 で追跡）。

### 3.3 タイムライン制約
- 単発機能のため当日完了想定。

## 4. Proposed Solution

### 4.1 アーキテクチャ概要

```
┌──────────────────────────────────────────────────────────────┐
│ frontend: OrderListPage                                      │
│  ├─ useGroups(effectiveShopNo) ── GET /partner-groups       │
│  ├─ SearchableSelect(groups) ─────► state.partnerGroupId     │
│  └─ listQuery ── GET /orders/details?partnerGroupId=...     │
└──────────────────────────────────────────────────────────────┘
                  │
                  ▼ (HTTP)
┌──────────────────────────────────────────────────────────────┐
│ backend: OrderController                                     │
│  └─ listDetails(partnerGroupId?)                             │
│      ├─ authorize: group.shopNo == effectiveShopNo           │
│      ├─ resolve: partnerCodes = group.partnerCodes           │
│      └─ TOrderDetailService.searchForListPaged(              │
│             partnerCodes, ...)                               │
└──────────────────────────────────────────────────────────────┘
                  │
                  ▼ (Spring Data JPA)
┌──────────────────────────────────────────────────────────────┐
│ TOrderDetailSpecification                                    │
│  └─ partnerCodeListContains(List<String> partnerCodes)       │
│      tOrder.partnerCode IN (...)                             │
└──────────────────────────────────────────────────────────────┘
```

### 4.1.1 Decision Record（代替案検討 — Codex P3-6 対応）

| 案 | 説明 | 採用判断 |
| --- | --- | --- |
| **A: `m_partner_group` 流用（採用）** | 既存マスタ + `partner_code IN` で受注を絞り込む | 採用。マスタ追加なし、運用学習コストなし、実 DB で「t_order.partner_code = t_invoice.partner_code」が確認済 |
| B: `partner_no IN` 検索 | 既存 `SearchableSelect` で複数選択化 | 不採用。500+ 得意先の中から都度複数選択は UX 劣化、再利用性なし |
| C: `m_order_partner_group` 新設 | 受注用に別マスタを新設 | 不採用。現状「請求先 = 注文者」運用のため重複マスタになる。将来 tokuisaki / seikyusaki 分離運用が広がったら再検討（§8 R8） |
| D: `t_order` に `partner_group_id` denormalize | 受注登録時にグループ ID を保存 | 不採用。バッチ取込時のグループ未確定問題、グループ変更時の整合性保守コスト過大 |

### 4.2 責務配分

| レイヤー | 責務 |
| --- | --- |
| Controller (`OrderController`) | パラメータ受領 / `LoginUserUtil.resolveEffectiveShopNo` / Service呼び出し |
| Service (`TOrderDetailService`) | `partnerGroupId` → group 取得 → 認可 (shopNo) → partnerCodes 抽出 / Specification 組み立て / 「グループ未存在」「空グループ」 → 空 Page short-circuit |
| Service (`MPartnerGroupService`) | グループ取得（既存 `findById` を流用） |
| Specification (`TOrderDetailSpecification`) | SQL `IN` 句生成 |

**設計判断（P2-3 対応）**: 業務ロジック (group 解決 / 認可 / shortcut) は **Service 層に集約**。Controller は薄く保つ (CLAUDE.md 「Controller は薄く」)。403 は Service が `AccessDeniedException` を throw し、`GlobalExceptionHandler` が HTTP 403 に変換（既存 `LoginUserUtil` と同じパターン）。

## 5. Data Model / DB Changes

**変更なし**。既存テーブルを流用する。

参考（既存スキーマ）:

```sql
m_partner_group
  partner_group_id  serial PK
  group_name        text not null
  shop_no           integer not null
  del_flg           char(1) default '0' not null

m_partner_group_member
  partner_group_id  integer  -- FK
  partner_code      varchar  -- 6桁0埋め (Service 層で正規化済)

t_order
  ...
  partner_no    integer
  partner_code  varchar  -- SMILE 連携値
  ...
```

## 6. API / UI Changes

### 6.1 Backend API 変更

#### `GET /api/v1/orders/details`

| パラメータ | 型 | 必須 | 説明 |
| --- | --- | --- | --- |
| **partnerGroupId** *(NEW)* | Integer | optional | 入金グループ ID。指定時、当該グループの `partnerCodes` で受注を絞り込む。 |
| 既存パラメータ | — | — | 変更なし |

**処理フロー（Controller — 薄い）:**

```java
@GetMapping("/details")
public ResponseEntity<Page<OrderDetailResponse>> listDetails(
        @RequestParam Integer shopNo,
        @RequestParam(required = false) Integer partnerGroupId,   // ← 追加
        @RequestParam(required = false) Integer partnerNo,
        ... (既存パラメータ：companyNo, slipNo, goodsName, goodsCode,
              orderDetailStatus, orderDateTimeFrom/To, slipDateFrom/To)
        @PageableDefault(...) Pageable pageable) {
    Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
    log.info("受注一覧検索: shopNo={}, partnerGroupId={}, partnerNo={}, ...", effectiveShopNo, partnerGroupId, partnerNo);
    String[] statusArray = orderDetailStatus != null ? new String[]{orderDetailStatus} : null;
    Page<TOrderDetail> page = tOrderDetailService.searchForListPaged(
            effectiveShopNo, companyNo, partnerNo, partnerGroupId,
            slipNo, goodsName, goodsCode, statusArray,
            orderDateTimeFrom, orderDateTimeTo, slipDateFrom, slipDateTo,
            Flag.NO, pageable);
    return ResponseEntity.ok(page.map(OrderDetailResponse::from));
}
```

**Service 修正（業務ロジックを集約 — P2-3 対応）:**

```java
@Service
public class TOrderDetailService extends CustomService {
    private final TOrderDetailRepository tOrderDetailRepository;
    private final MPartnerGroupService partnerGroupService;  // ← 追加注入

    @SkipShopCheck
    public Page<TOrderDetail> searchForListPaged(
            Integer shopNo, Integer companyNo, Integer partnerNo,
            Integer partnerGroupId,           // ← 追加
            String slipNo, String goodsName, String goodsCode,
            String[] orderDetailStatus,
            LocalDateTime orderDateTimeFrom, LocalDateTime orderDateTimeTo,
            LocalDate slipDateFrom, LocalDate slipDateTo,
            Flag delFlg, Pageable pageable) {

        // 1) partnerGroupId → partnerCodes 解決（認可含む）
        List<String> partnerCodes = resolvePartnerCodesByGroup(partnerGroupId, shopNo);
        if (partnerGroupId != null && (partnerCodes == null || partnerCodes.isEmpty())) {
            // グループ未存在・空グループ → 空 Page short-circuit
            return Page.empty(pageable);
        }

        return tOrderDetailRepository.findAll(
                buildListSpec(shopNo, companyNo, partnerNo, partnerCodes,
                        slipNo, goodsName, goodsCode, orderDetailStatus,
                        orderDateTimeFrom, orderDateTimeTo, slipDateFrom, slipDateTo, delFlg),
                pageable);
    }

    /**
     * グループ ID から partner_codes を取得し、shopNo 認可を行う。
     * @return partnerCodes（グループ未指定なら null）
     * @throws AccessDeniedException 他店舗グループへのアクセス時
     */
    private List<String> resolvePartnerCodesByGroup(Integer partnerGroupId, Integer effectiveShopNo) {
        if (partnerGroupId == null) return null;
        MPartnerGroup group = partnerGroupService.findById(partnerGroupId);
        if (group == null) {
            // 削除済 / 存在しない → 空グループ扱い（呼び出し元で empty Page）
            return List.of();
        }
        // 認可: effectiveShopNo == null（admin で全店舗）も他店舗 group は許容しない（明示の partnerGroup は所属店舗に限定）
        if (effectiveShopNo == null || !effectiveShopNo.equals(group.getShopNo())) {
            throw new AccessDeniedException("他店舗のグループにアクセスできません: partnerGroupId=" + partnerGroupId);
        }
        return group.getPartnerCodes();  // 空リストもそのまま返す → 呼び出し元で empty Page
    }
}
```

**Specification 追加（P3-2 対応: `buildListSpec` 内に統合）:**

```java
public Specification<TOrderDetail> partnerCodeListContains(List<String> partnerCodes) {
    return CollectionUtil.isEmpty(partnerCodes) ? null
        : (root, query, cb) -> root.get("tOrder").get("partnerCode").in(partnerCodes);
}
```

```java
// TOrderDetailService.buildListSpec に partnerCodes 引数を追加
private Specification<TOrderDetail> buildListSpec(
        Integer shopNo, Integer companyNo, Integer partnerNo,
        List<String> partnerCodes,        // ← 追加
        String slipNo, String goodsName, ...) {
    return Specification
            .where(spec.shopNoContains(shopNo))
            .and(spec.companyNoContains(companyNo))
            .and(spec.partnerNoContains(partnerNo))
            .and(spec.partnerCodeListContains(partnerCodes))  // ← 追加
            .and(spec.slipNoContains(slipNo))
            ...;
}
```

**既存呼び出し元の確認**:
`searchForListPaged` は `OrderController#listDetails` 1箇所からのみ呼ばれている（Grep 結果）。引数追加による破壊変更はない。

**設計上の選択（P2-2 検討結果）**: 引数 14個は確かに多いが、本機会で `OrderSearchCriteria` record 導入はスコープ拡大が大きい。本 PR は **既存パターン踏襲（引数追加）** で進め、`OrderSearchCriteria` 化は「Next Session Tasks」に追加候補とする。

### 6.2 Frontend UI 変更

#### `components/pages/order/index.tsx` 変更点

1. **state に `partnerGroupId` 追加:**

```ts
interface OrderSearchState {
  partnerNo: string
  partnerGroupId: string          // ← 追加
  slipNo: string
  ...
}
```

2. **検索フォームに SearchableSelect 追加（「得意先」の次）:**

```tsx
<div className="space-y-2">
  <Label>請求先グループ</Label>  {/* Codex P3-5 対応: 「入金グループ」より意味を明示 */}
  <SearchableSelect
    value={partnerGroupId}
    onValueChange={setPartnerGroupId}
    options={(groupsQuery.data ?? []).map((g) => ({
      value: String(g.partnerGroupId),
      label: `${g.groupName}（${g.partnerCodes.length}件）`,
    }))}
    searchPlaceholder="グループ名を検索..."
    placeholder={effectiveShopNo ? "グループを選択（任意）" : "店舗を選択してください"}
    emptyMessage="グループが見つかりません"
    disabled={!effectiveShopNo}  {/* §8 R9: admin で店舗未選択時は disabled */}
  />
  {/* Codex P3-4/P3-5: 仕様明示の補足テキスト */}
  <p className="text-xs text-muted-foreground">
    請求書一覧で登録した請求先グループで絞り込みます。手入力得意先（数値以外の partner_code）は対象外です。
  </p>
</div>
```

3. **groups 取得 hook（既存 invoices.tsx と同パターン）:**

```ts
const groupsQuery = useQuery({
  queryKey: ['partner-groups', effectiveShopNo],
  queryFn: () => api.get<PartnerGroup[]>(
    `/finance/partner-groups?shopNo=${effectiveShopNo}`
  ),
  enabled: !!effectiveShopNo,
})
```

4. **store 切替時のクリア（既存 `setSelectedShopNo` 同様）:**

```ts
<Select value={selectedShopNo} onValueChange={(v) =>
  setState({ ...state, selectedShopNo: v, partnerNo: '', partnerGroupId: '' })
}>
```

5. **listQuery で API パラメータに追加:**

```ts
if (searchParams?.partnerGroupId) params.append('partnerGroupId', searchParams.partnerGroupId)
```

6. **handleSearch / handleReset / defaultState に追加:**

```ts
const defaultState: OrderSearchState = {
  partnerNo: '',
  partnerGroupId: '',   // ← 追加
  ...
}

const handleSearch = () => {
  setState({
    ...state,
    searchParams: {
      partnerNo,
      partnerGroupId,    // ← 追加
      ...
    },
  })
}
```

### 6.3 UI 配置イメージ

```
┌─ 検索フォーム ─────────────────────────────┐
│  店舗: [Select]      (admin のみ)            │
│  得意先: [SearchableSelect]                  │
│  入金グループ: [SearchableSelect]  ← NEW     │
│  伝票番号: [Input]                           │
│  商品名: [Input]                             │
│  ...                                          │
└──────────────────────────────────────────────┘
```

**UI 補足（P3-4 対応）**:
- 請求書一覧のグループ選択は「グループ内 partnerCode 行を一括チェック」が主目的（フィルタはクライアント側 filter で副次的）。
- 受注一覧のグループ選択は「**サーバサイドでグループ内に絞り込む検索条件**」が目的。意味が異なるため、UI 上で混同しないよう Label を「**入金グループで絞り込む**」とし、placeholder に「グループを選択（任意）」を設定する。
- 「得意先」と「入金グループ」両方指定時は AND（グループ内かつ単独得意先一致）になる旨を Label の `description`（または tooltip）で明示。

### 6.4 sessionStorage への永続化

- 既存 `useSearchParamsStorage('order-list-search', ...)` は **sessionStorage** に state を保存する（URL クエリ反映は行わない）。`partnerGroupId` も同 storage に乗るため、タブ内遷移で復元される。
- URL クエリ反映による「再訪 URL からの復元」「シェア URL」機能は別タスク（MEMORY 内 Next Session Tasks F-4 `useSearchParamsStorage統一` で扱う想定）。

## 7. Edge Cases

| ケース | 挙動 |
| --- | --- |
| `partnerGroupId` が存在しない ID | 200 OK + 空 Page（403/404 を返さないことで、削除直後の SearchableSelect 再フェッチ前を許容） |
| `partnerGroupId` の shopNo が effective shopNo と不一致 | **403 Forbidden** （他店舗データ覗き見防止） |
| `partnerGroupId` 指定 + `partnerNo` 指定 | AND 条件（グループ内かつ単独得意先一致）→ 通常空 or 1件のみ |
| `partnerGroupId` 指定 + グループ partnerCodes 空 | 200 OK + 空 Page |
| `t_order.partner_code` が NULL の行 | `IN` 句で自動的に除外（PostgreSQL の NULL 仕様）。**仕様明示**: B-Cart 直販等で partner_code 未付与の受注は、グループ検索結果から除外される（グループは SMILE 連携 partnerCode 前提）。グループ未指定時は従来通り全件表示。 |
| グループ partnerCodes が `m_partner_group_member` の生値で 6桁0埋め、`t_order.partner_code` が異なる表現の場合 | dev DB で同じ 6桁0埋めであることを確認済（§11 参照）。将来 `m_partner_group_member.partner_code` が 7桁以上に変わると IN マッチ破綻するため、§8 R6 で追跡 |
| 非 admin が他店舗グループにアクセス試行 | `resolveEffectiveShopNo` で店舗強制 → group の shopNo 不一致 → 403 |
| `partnerGroupId` が空文字 / "0" | Controller では `Integer` バインドで `null` → グループフィルタ無効 |
| **admin が店舗未選択** (`selectedShopNo=''`) で group Select を操作 | Frontend 側で group Select を **disabled** にし、tooltip 「店舗を選択してください」（§8 R9） |
| **手入力得意先** (`999999` → MD5 ハッシュ化 partner_code)（Codex 指摘 P3-4） | `MPartnerGroupService.normalizePartnerCode` で非数値値は dedup スキップされグループ非加入 → 該当受注はグループ検索結果から **除外される** 仕様。tooltip で「手入力得意先はグループ検索対象外」を明示 |

## 8. Risks and Mitigations

| リスク | 影響度 | 対策 |
| --- | --- | --- |
| **R1**: `t_order.partner_code` の表現が `m_partner_group_member.partner_code` と不一致（パディング差） | 中（マッチ0件） | 設計レビュー 2-a で実DB データ確認。不一致なら Service 層で同じ正規化 (`String.format("%06d", ...)`) を適用 |
| **R2**: パフォーマンス劣化（`t_order.partner_code` にインデックスなし） | 低 | 既存検索で `shop_no` / `del_flg` 等の条件で十分絞り込み済。`partner_code` の IN 句は数百件以下を想定 |
| **R3**: 削除済グループへの過去クエリで 404 を出すと UX 悪化 | 低 | エラーではなく **空結果** を返す方針（FR-6） |
| **R4**: 既存 `partnerNo` 単独検索ロジックの回帰 | 中 | partnerCodes が null/空のときは Specification が null を返す（既存挙動を維持）。回帰テストで担保 |
| **R5**: フロント検索フォームが煩雑化（フィールド過多） | 低 | グループは「得意先」の直下に配置し、視覚的に関連性を示す |
| **R6**: `m_partner_group_member.partner_code` の桁数が将来 7 桁以上に変わると IN 句マッチが破綻 | 低 | `MPartnerGroupService.normalizeAndDedup` は `%06d` で 6桁固定（Long.parseLong で 999999 を超えると 7 桁になる懸念は Open Question §11 で追跡）。Phase 3 実装時に `m_partner_group_member.partner_code` の現実値 max length を確認 |
| **R7**: `t_order.partner_code` インデックス不在で全件 join スキャン | 低〜中 | 既存検索で `shop_no` / `order_date_time` 等で十分に行が絞られているため EXPLAIN 上は問題化しない見込み。実装時に `EXPLAIN ANALYZE` 1回確認、必要なら部分インデックス検討。候補: `CREATE INDEX CONCURRENTLY idx_t_order_shop_partner_code ON t_order(shop_no, partner_code) WHERE partner_code IS NOT NULL AND del_flg = '0';` |
| **R8** (Codex 指摘): tokuisaki / seikyusaki 分離運用への移行で reuse 前提が崩れる | 低（現状） | §3.2 で実 DB 確認済。将来分離運用に変わった場合、本機能のセマンティクスを「注文者グループ vs 請求先グループ」のどちらにするか業務側と再合意 |
| **R9** (Codex 指摘): admin が `shopNo=0` 状態（自店舗未選択）で grouping した場合、partner-groups API は `shop_no=0` の groups を返すため 0 件 or 認可エラー | 低 | 設計上 `partnerGroupId` 指定時は **`shopNo > 0` を要求**。Frontend で admin が店舗未選択時はグループ Select を **disabled** にし、placeholder で「店舗を選択してください」と表示 |

## 9. Rollout Plan

### 9.1 デプロイ戦略
- 単一 PR で backend + frontend をマージ。
- Feature flag **不使用** — 新規 optional パラメータのため後方互換性あり。

### 9.2 検証
1. **Local**: `./gradlew test` + `npx tsc --noEmit` + `npx playwright test` (新規 E2E)
2. **Manual**: dev 環境で以下を確認
   - admin / 非 admin それぞれでグループ Select が正しく表示される
   - グループ選択 → 検索で該当 partner_code の受注のみが返る
   - 店舗切替で partnerGroupId がクリアされる
   - グループなし状態で従来通り検索できる（後方互換）

### 9.3 ロールバック
- 単純なフィルタ追加なので、リバートで安全に戻せる。
- DB 変更なしのため migration ロールバック不要。

## 10. テスト方針 (概略)

| レイヤー | 内容 |
| --- | --- |
| Service Unit (backend) | `TOrderDetailService.searchForListPaged` の partnerGroupId 解決ロジック: null / 削除済 / 空グループ / 通常 / 他店舗グループ (AccessDeniedException) |
| Repository Integration (backend) | partnerCodes IN 句が SQL レベルで効くこと、B-Cart partner_code NULL 行が除外されること、partnerNo + partnerGroupId AND が効くこと |
| Controller (`@WebMvcTest`) | `partnerGroupId` を含む API パラメータ受領、Service 層 (MockBean) への引数伝播、AccessDeniedException → 403 変換、既存パスの後方互換 |
| Frontend Component | SearchableSelect 表示 / 店舗切替時の partnerGroupId クリア / handleSearch の API 呼び出しパラメータ確認 |
| E2E (Playwright) | グループ選択 → 検索 → 結果検証、admin で店舗切替 → グループリスト切り替わり、非 admin で他店舗 group が見えない |

詳細は Phase 3 の `s-test-plan` で具体化。

## 11. Open Questions

### 解消済み

| Q | 結論 |
| --- | --- |
| `t_order.partner_code` の実値表現は 6桁0埋め文字列か？ | **YES**: 実 DB 確認 (例: `000018`, `010044`) すべて `length=6`。 |
| 既存 partner_group の partner_codes が `t_order.partner_code` と直接 IN マッチするか？ | **YES**: `m_partner_group_member.partner_code` も 6桁0埋め (例: `720000`)。追加正規化不要。 |
| 受注一覧の「得意先」プルダウン (`partnerNo` ベース) は残すか？ | 残す（個別検索ニーズは残るため、AND で併用可能）。 |
| `searchForListPaged` の他の呼び出し元はあるか？ | **No** (Grep 確認)。OrderController からのみ呼ばれているため引数追加は安全。 |

### Phase 3 で確認

| Q | 確認方法 |
| --- | --- |
| `t_order.partner_code` の検索性能（インデックス不在の影響） | 実装後 `EXPLAIN ANALYZE` を 1 回回す。必要なら `CREATE INDEX ON t_order(partner_code) WHERE del_flg='0'` を Migration で検討 |
| `m_partner_group_member.partner_code` の現実 max length（将来 7 桁拡張への備え） | `SELECT max(length(partner_code)) FROM m_partner_group_member` で確認、6 桁固定なら本機能スコープ外。7 桁以上があれば §3.1 の前提が崩れるため、`%06d` 正規化を IN 句側でも適用するか検討 |

### 確認 SQL（dev環境 2026-05-11 実行）

```sql
-- t_order.partner_code: 6桁0埋め文字列
SELECT partner_no, partner_code, length(partner_code) FROM t_order
WHERE partner_code IS NOT NULL ORDER BY order_no DESC LIMIT 5;
-- → 110 | 000018 | 6, 2002 | 010044 | 6, ...

-- m_partner_group_member.partner_code: 同じく6桁0埋め
SELECT pg.partner_group_id, pg.group_name, m.partner_code, length(m.partner_code)
FROM m_partner_group pg JOIN m_partner_group_member m
  ON pg.partner_group_id = m.partner_group_id WHERE pg.del_flg = '0' LIMIT 5;
-- → 1 | 広島YMCA | 720000 | 6, ...
```


