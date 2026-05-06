# テスト計画書: B-CART商品 変更点一覧・一括反映機能

作成日: 2026-04-30
対象機能: B-CART商品マスタ管理 Phase 3-A（価格・配送サイズの編集 → B-CART反映）
対象設計書: `claudedocs/design-bcart-pending-changes.md`
ブランチ: `refactor/code-review-fixes` 派生想定（実装ブランチ未作成）

---

## 0. 前提と本計画のスコープ

本計画は、設計書 §4–§7 に定義された以下のコンポーネントをテスト対象とする。

- **新規 Service**: `BCartProductSetsPricingService`, `BCartProductSetsReflectService`, `BCartReflectTransactionService`
- **新規 Controller**: `BCartProductSetsController`, `BCartPendingChangesController`
- **新規 DTO**: `BCartProductSetPricingRequest`, `BCartPendingChangeResponse`, `BCartReflectRequest`, `BCartReflectResult`
- **既存拡張**: `BCartChangeHistoryRepository`（集約クエリ + `markReflectedByIds`）, `BCartProductSetsRepository`
- **新規 Frontend**: `/bcart/pending-changes` 画面、`/bcart/products/{id}` セット一覧タブのインライン編集
- **削除**: 空スタブ `BCartGoodsPriceTableUpdateTasklet`

設計書のレビュー指摘 (Critical/Major) に対応した検証は **§3–§5 のテストケースに明示的にマーキング** する（`[C-1]`, `[M-1]` 等のタグで参照）。

---

## 1. テスト戦略

### 1.1 テストレイヤ分担

| レイヤ | 対象 | ツール | 目的 |
|--------|------|--------|------|
| **Unit** | Service ロジック（編集トランザクション、aggregate、PATCH ハンドリング） | JUnit 5 + Mockito | 内部ロジック・分岐・例外処理の網羅 |
| **Integration (Controller)** | REST エンドポイント疎通、Bean Validation、認証 | `@SpringBootTest` + MockMvc + Testcontainers (PostgreSQL) | HTTP 層 → DB 反映までの結合 |
| **Integration (Repository)** | ネイティブクエリ・aggregate SQL | `@DataJpaTest` (PG17 Testcontainers) | SQL 集約結果の正当性 |
| **E2E (mock)** | UI フロー全般 | Playwright + `mockAllApis` | ユーザ操作シナリオの回帰防止 |
| **E2E (smoke 実バックエンド)** | 1件編集→反映までの実 DB 疎通 | Playwright + ローカル backend 起動 | フロント・バック結合の最終確認 |
| **Manual** | B-CART 実 API への PATCH 疎通 | curl + 開発環境 | 仕様確認・レート制限挙動観察 |

### 1.2 方針

- **TDD 優先**: Service → Controller → Frontend の順で各レイヤ Test-First。
- **モック / 実バックエンド両輪**: E2E は mock で網羅性、smoke で実疎通（MEMORY.md `feedback_incremental_review.md` 準拠）。
- **設計書レビュー Critical の必須カバー**: §3.2 / §3.3 / §3.4 で C-1, C-2, M-1, M-3 を最優先テストケースとして記述。
- **B-CART 実 API は最小回数のみ叩く**: レート制限 (300req/5min) を消費しないため自動テストはモックで完結し、実 API は手動スモークで 1 セットのみ。

---

## 2. テスト対象範囲

### 2.1 バックエンド

| ファイル | 主要メソッド | テスト分類 |
|---|---|---|
| `BCartProductSetsPricingService.update()` | 編集 + 履歴記録 | Unit, Integration |
| `BCartProductSetsReflectService.reflect()` | aggregate + PATCH + DB 更新呼出 | Unit |
| `BCartProductSetsReflectService.aggregateUnreflectedFields()` | 履歴行集約 (最古 before / 最新 after, id 配列保持) | Unit |
| `BCartReflectTransactionService.markReflected()` | history id 限定 UPDATE + 本テーブル更新 | Unit, Integration |
| `BCartProductSetsController.updatePricing()` | PUT エンドポイント、Bean Validation | Integration |
| `BCartPendingChangesController.list()` | GET 一覧、`LIMIT 500` | Integration |
| `BCartPendingChangesController.reflect()` | POST 反映、件数 200 ガード | Integration |
| `BCartPendingChangesController.count()` | GET 件数 | Integration |
| `BCartChangeHistoryRepository.findUnreflectedForProductSet()` | aggregate native query | Integration (`@DataJpaTest`) |
| `BCartChangeHistoryRepository.markReflectedByIds()` | `WHERE id IN (...)` UPDATE | Integration |

### 2.2 フロントエンド

| ファイル | テスト分類 |
|---|---|
| `components/pages/bcart/product-detail.tsx`（セット一覧タブ拡張） | E2E (mock) |
| `components/pages/bcart/pending-changes.tsx`（新規） | E2E (mock + smoke) |
| `app/(authenticated)/bcart/pending-changes/page.tsx` | E2E (mock) |
| `components/layout/Sidebar.tsx`（リンク追加） | E2E (sidebar-navigation.spec.ts に追加) |

### 2.3 スコープ外

- B-CART API の 422 / 5xx 詳細レスポンス再現（B-CART 側仕様変動領域、E2E では汎用エラー文言で確認）
- 楽観ロック（設計書 §6 で Phase 3-B 送り）
- レート制限到達時の自動リトライ（同期 API のため呼び出し側責任）
- `group_price` / `special_price` 編集（Phase 3-B 以降）

---

## 3. Unit テスト（バックエンド Service 層）

実行: `./gradlew test --tests "jp.co.oda32.domain.service.bcart.*"`

### 3.1 `BCartProductSetsPricingServiceTest`

`@ExtendWith(MockitoExtension.class)` + `@Mock BCartProductSetsRepository / BCartChangeHistoryRepository`。

| ID | テスト名 | 期待挙動 | 優先度 |
|---|---|---|---|
| PS-U01 | `update_unitPriceOnly_savesHistoryAndUpdatesSet` | `unitPrice` のみ変更 → history 1 行 INSERT、`b_cart_price_reflected=false` セット | P0 |
| PS-U02 | `update_shippingSizeOnly_savesHistoryAndUpdatesSet` | `shippingSize` のみ変更 → history 1 行 INSERT | P0 |
| PS-U03 | `update_bothFieldsChanged_savesTwoHistoryRowsAndOneSetUpdate` | 両フィールド同時 → history 2 行 INSERT、`set.save` は 1 回 | P0 |
| PS-U04 | `update_noChange_skipsHistoryAndSetSave` | リクエスト値が DB 値と同一 → `historyRepo.save` 呼ばれない、`setRepo.save` も呼ばれない | P0 |
| PS-U05 | `update_partialChange_unitPriceUnchanged_shippingSizeChanged` | 1 フィールドのみ変化 → history 1 行のみ | P1 |
| PS-U06 | `update_setNotFound_throwsEntityNotFoundException` | `findById` が empty → `EntityNotFoundException` | P1 |
| PS-U07 | `update_recordsLoginUserNoAsChangedBy` | history 行の `changed_by` がパラメタの `userNo` と一致 | P1 |
| PS-U08 | `update_beforeValueIsCurrentDbValueAsString` | history `before_value` = `BigDecimal#toPlainString()` 化された DB 値（設計書 §5 表記揺れ防止） | P0 |
| PS-U09 | `update_afterValueIsRequestValueAsString` | history `after_value` = リクエスト値の `toPlainString` | P1 |
| PS-U10 | `update_targetTypeIsPRODUCT_SETAndFieldNamesMatch` | history `target_type='PRODUCT_SET'`, `field_name ∈ {unit_price, shipping_size}` | P0 |

**実装例（PS-U03）:**
```java
@Test
void update_bothFieldsChanged_savesTwoHistoryRowsAndOneSetUpdate() {
    BCartProductSets set = new BCartProductSets();
    set.setId(100L);
    set.setUnitPrice(new BigDecimal("1000.00"));
    set.setShippingSize(new BigDecimal("0"));
    when(setRepo.findById(100L)).thenReturn(Optional.of(set));

    BCartProductSetPricingRequest req = new BCartProductSetPricingRequest(
        new BigDecimal("1100.00"), new BigDecimal("0.5"));

    service.update(100L, req, 1);

    ArgumentCaptor<BCartChangeHistory> captor = ArgumentCaptor.forClass(BCartChangeHistory.class);
    verify(historyRepo, times(2)).save(captor.capture());
    List<BCartChangeHistory> saved = captor.getAllValues();
    assertThat(saved).extracting(BCartChangeHistory::getFieldName)
        .containsExactlyInAnyOrder("unit_price", "shipping_size");
    assertThat(set.getBCartPriceReflected()).isFalse();
    verify(setRepo, times(1)).save(set);
}
```

### 3.2 `BCartProductSetsReflectServiceTest`

`@Mock` で `BCartChangeHistoryRepository`, `BCartReflectTransactionService`, `OkHttpClient`。

| ID | テスト名 | 期待挙動 | レビュー対応 | 優先度 |
|---|---|---|---|---|
| RS-U01 | `reflect_singleSet_success_callsPatchAndMarkReflected` | aggregate → PATCH 200 → `txService.markReflected(setId, historyIds)` | — | P0 |
| RS-U02 | `reflect_emptyList_returnsZeroResult` | 入力リスト空 → `succeeded=0, failed=0`、PATCH 呼ばれない | — | P0 |
| RS-U03 | `reflect_noUnreflectedHistory_skipsAndCounts` | 履歴 0 件のセット → result に skip 記録、PATCH 呼ばれない | — | P0 |
| RS-U04 | `reflect_patchFails_doesNotCallMarkReflected` | PATCH IOException → `markReflected` 呼ばれない、failed カウント | C-2 | P0 |
| RS-U05 | `reflect_partialFailure_continuesProcessingRemaining` | 3 件中 1 件 PATCH 失敗 → 残り 2 件は処理継続、result は `succeeded=2, failed=1` | — | P0 |
| RS-U06 | `reflect_aggregateUsesOldestBeforeAndLatestAfter` | 同一セット同一フィールドの履歴 3 行（changed_at 昇順） → before=最古行の値、after=最新行の値 | 設計書 §5 | P0 |
| RS-U07 | `reflect_aggregatePreservesHistoryIdList` | aggregate 結果の `historyIds` が DB 取得時の全 history id を含む | M-1 | P0 |
| RS-U08 | `reflect_markReflectedCalledWithIdListNotPredicate` | `txService.markReflected(setId, historyIds)` 呼出時に id list が空でない | M-1 | P0 |
| RS-U09 | `reflect_patchBuildsFormBodyWithChangedFieldsOnly` | aggregate に `unit_price` のみあれば FormBody に `unit_price` のみ含む（`shipping_size` は含まない） | — | P1 |
| RS-U10 | `reflect_responseBodyNullSafe` | `res.body() == null` 時も NPE せず IOException メッセージに空文字 | M-3 | P1 |
| RS-U11 | `reflect_no_sleep_betweenRequests` | 100 件処理を 1 秒以内で完了することで sleep 未挿入を確認（mock client は即時返却） | 設計書 §2 非機能 | P1 |
| RS-U12 | `reflect_429ResponseCountsAsFailure` | B-CART が 429 → IOException → failed カウント、後続処理は継続 | 設計書 §7 | P1 |
| RS-U13 | `reflect_largeIdList_aggregatePerformance` | 200 件のセットに対し aggregate と PATCH 呼出が線形（時間 < 数秒、mock 即時応答前提） | — | P2 |

**実装例（RS-U06 集約ロジック）:**
```java
@Test
void reflect_aggregateUsesOldestBeforeAndLatestAfter() {
    Long setId = 100L;
    Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
    List<BCartChangeHistory> rows = List.of(
        history(1L, setId, "unit_price", "1000", "1100", t0),
        history(2L, setId, "unit_price", "1100", "1200", t0.plusSeconds(60)),
        history(3L, setId, "unit_price", "1200", "1250", t0.plusSeconds(120))
    );
    when(historyRepo.findUnreflectedForProductSet(eq(setId), anyList())).thenReturn(rows);
    when(httpClient.newCall(any())).thenReturn(stubCall(200, "{}"));

    service.reflect(List.of(setId));

    // PATCH 呼出時の form body は最新 after 値「1250」を送るはず
    Request captured = captureRequest();
    String body = bodyAsString(captured);
    assertThat(body).contains("unit_price=1250");
    // markReflected は 3 件全ての id を渡す
    verify(txService).markReflected(eq(setId), eq(List.of(1L, 2L, 3L)));
}
```

### 3.3 `BCartReflectTransactionServiceTest`

| ID | テスト名 | 期待挙動 | レビュー対応 | 優先度 |
|---|---|---|---|---|
| TX-U01 | `markReflected_callsRepoMarkByIdsAndSetUpdate` | history `markReflectedByIds(ids)` → `setRepo.save(set)` の順 | M-1 | P0 |
| TX-U02 | `markReflected_emptyIds_doesNotCallRepo` | `historyIds` 空 → repo 呼ばれない（防御的） | — | P1 |
| TX-U03 | `markReflected_setNotFound_logsButDoesNotThrow` | set が削除済 → history のみ更新、例外を投げない | — | P2 |
| TX-U04 | `markReflected_isPublicAndAnnotatedTransactional` | リフレクションで `@Transactional` + `public` を確認 | C-1 | P0 |

**実装例（TX-U04 アノテーション検証、CGLIB プロキシが効くことを実装段階で保証する単体テスト）:**
```java
@Test
void markReflected_isPublicAndAnnotatedTransactional() throws NoSuchMethodException {
    Method m = BCartReflectTransactionService.class.getMethod("markReflected", Long.class, List.class);
    assertThat(Modifier.isPublic(m.getModifiers())).isTrue();
    assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
}
```

### 3.4 不正入力 / Bean Validation

`BCartProductSetPricingRequest` の `@Valid` 制約を Hibernate Validator で単体検証。

| ID | テスト名 | 入力 | 期待 |
|---|---|---|---|
| VL-U01 | `unitPriceNegative_violatesMin` | `unitPrice = -1` | violation: `unitPrice >= 0` |
| VL-U02 | `shippingSizeOver1_violatesMax` | `shippingSize = 1.5` | violation: `<= 1` |
| VL-U03 | `shippingSizeNegative_violatesMin` | `shippingSize = -0.1` | violation: `>= 0` |
| VL-U04 | `bothNull_isAllowed` | 両 null | violation 0 件（編集スキップ扱いはサービス層で判定） |
| VL-U05 | `unitPriceZero_isAllowed` | `unitPrice = 0` | violation 0 件 |

---

## 4. Integration テスト

### 4.1 Controller 結合テスト（`@SpringBootTest` + MockMvc）

`@AutoConfigureMockMvc` + `@WithMockUser` または JWT スタブ。テスト DB は Testcontainers PostgreSQL 17 + Flyway 適用。

#### `BCartProductSetsControllerIT`

| ID | テスト名 | 期待 | 優先度 |
|---|---|---|---|
| PSC-I01 | `PUT_pricing_validRequest_returns200AndUpdatedDto` | 200 OK + 更新後 DTO、DB 履歴 +1, `b_cart_price_reflected=false` | P0 |
| PSC-I02 | `PUT_pricing_unauthenticated_returns401` | JWT なし → 401 | P0 |
| PSC-I03 | `PUT_pricing_invalidShippingSize_returns400` | `shippingSize=2.0` → 400 + violation message | P0 |
| PSC-I04 | `PUT_pricing_setIdNotFound_returns404` | 存在しない `setId` → 404 | P1 |
| PSC-I05 | `PUT_pricing_emptyBody_treatedAsNoChange` | body `{}` → 200 + 履歴 0、本テーブル更新なし | P1 |

#### `BCartPendingChangesControllerIT`

| ID | テスト名 | 期待 | 優先度 |
|---|---|---|---|
| PCC-I01 | `GET_list_returnsAggregatedDiff` | 履歴 fixture から SQL 集約結果が想定 JSON 構造 | P0 |
| PCC-I02 | `GET_list_excludesReflectedRows` | `b_cart_reflected=true` の history は含まれない | P0 |
| PCC-I03 | `GET_list_truncatesAt500` | 501 件の fixture → 500 件返却 + `truncated=true` ヘッダ or フィールド（実装方法を設計に合わせる） | P0 |
| PCC-I04 | `GET_count_returnsAggregatedCount` | 5 セットに 8 件の history → `count=5` (セット単位カウント) | P1 |
| PCC-I05 | `POST_reflect_idsList_returns200WithResult` | productSetIds 指定 → 反映実行、result 構造を検証 | P0 |
| PCC-I06 | `POST_reflect_allTrue_resolvesAllUnreflected` | `{ "all": true }` → サービスが全 ID 取得 → 反映 | P0 |
| PCC-I07 | `POST_reflect_over200ids_returns400` | 201 件 → 400 BAD_REQUEST + メッセージ `件数上限` | P0 / **C-2** |
| PCC-I08 | `POST_reflect_emptyIds_returns200WithZeroCounts` | `[]` → 200 + `succeeded=0, failed=0` | P1 |
| PCC-I09 | `POST_reflect_unauthenticated_returns401` | JWT なし → 401 | P0 |

**実装例（PCC-I07 件数上限ガード、Critical-2 対応）:**
```java
@Test
void POST_reflect_over200ids_returns400() throws Exception {
    List<Long> ids = LongStream.rangeClosed(1, 201).boxed().toList();
    mockMvc.perform(post("/api/v1/bcart/pending-changes/reflect")
            .with(jwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("productSetIds", ids))))
       .andExpect(status().isBadRequest())
       .andExpect(jsonPath("$.message").value(containsString("200")));
}
```

### 4.2 Repository 結合テスト（`@DataJpaTest`）

PG17 Testcontainers + Flyway。

| ID | テスト名 | 期待 | レビュー対応 | 優先度 |
|---|---|---|---|---|
| REP-I01 | `findUnreflected_aggregatesOldestBeforeLatestAfter` | 同一 (target_id, field_name) の履歴 3 行 → before=最古, after=最新 | 設計書 §5 | P0 |
| REP-I02 | `findUnreflected_excludesReflected` | `b_cart_reflected=true` 行は集約されない | M-2 | P0 |
| REP-I03 | `findUnreflected_filtersFieldNameAllowList` | `field_name='description'` は対象外（`unit_price`,`shipping_size` のみ） | 設計書 §6-1 SQL | P0 |
| REP-I04 | `findUnreflected_limit500` | 600 セットの履歴 fixture → 500 件で打ち切り | 設計書 §6-1 | P1 |
| REP-I05 | `markReflectedByIds_updatesOnlyListedIds` | id list 限定 UPDATE、リスト外の `reflected=false` は変化なし | M-1 | P0 |
| REP-I06 | `markReflectedByIds_emptyList_doesNothing` | 空リスト → 影響行 0 | — | P1 |

**実装例（REP-I05 設計書 M-1 対応の核心テスト）:**
```java
@Test
void markReflectedByIds_updatesOnlyListedIds() {
    BCartChangeHistory h1 = saveHistory(/*reflected=*/false);
    BCartChangeHistory h2 = saveHistory(false);
    BCartChangeHistory h3 = saveHistory(false); // ループ実行中に追加された新規編集に相当
    em.flush(); em.clear();

    repo.markReflectedByIds(List.of(h1.getId(), h2.getId()));
    em.flush(); em.clear();

    assertThat(repo.findById(h1.getId()).orElseThrow().isBCartReflected()).isTrue();
    assertThat(repo.findById(h2.getId()).orElseThrow().isBCartReflected()).isTrue();
    assertThat(repo.findById(h3.getId()).orElseThrow().isBCartReflected()).isFalse(); // ★ 不変
}
```

### 4.3 トランザクション挙動 / 同時編集

| ID | テスト名 | 期待 | 優先度 |
|---|---|---|---|
| TX-I01 | `update_historySaveFails_setNotPersisted` | `historyRepo.save` で例外 → トランザクションロールバック、`b_cart_product_sets` も不変 | P0 |
| TX-I02 | `markReflected_setSaveFails_historyAlsoRollsBack` | `setRepo.save` で例外 → history 更新もロールバック | P0 |
| TX-I03 | `concurrentEdit_lastWriteWins_documented` | 2 並行で同一 setId を update → 後勝ち、両方の履歴が保持される（楽観ロック非対応の挙動を明示） | P1 |

---

## 5. E2E テスト（Playwright）

ファイル: `frontend/e2e/bcart-pending-changes.spec.ts`（新規）。
共通: `await mockAllApis(page)` → `await loginAndGoto(page, '/bcart/pending-changes')` パターン。
追加 mock: B-CART pending-changes 系エンドポイントを describe スコープで `page.route` で上書き。

### 5.1 mock fixture 定義

```ts
const MOCK_PENDING_CHANGES = [
  {
    productSetId: 12345,
    productId: 678,
    productName: 'ラ・フランス 2L 5kg',
    setName: 'ケース(10入)',
    productNo: 'ABC-123',
    janCode: '4901234567890',
    changes: [
      { field: 'unitPrice', before: '1000.00', after: '1100.00', changedAt: '2026-05-01T10:30:00', changedBy: 'k_oda' },
      { field: 'shippingSize', before: '0', after: '0.5', changedAt: '2026-05-01T10:30:00', changedBy: 'k_oda' },
    ],
  },
  {
    productSetId: 67890,
    productId: 679,
    productName: '○○製品',
    setName: 'バラ',
    productNo: 'XYZ-001',
    janCode: '4901234567891',
    changes: [
      { field: 'unitPrice', before: '250.00', after: '280.00', changedAt: '2026-05-01T11:00:00', changedBy: 'k_oda' },
    ],
  },
]

async function mockBCartPendingRoutes(page: Page, opts: { changes?: any[]; reflectFails?: number[] } = {}) {
  await page.route(
    (url) => url.pathname === '/api/v1/bcart/pending-changes',
    async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.changes ?? MOCK_PENDING_CHANGES) })
    },
  )
  await page.route(
    (url) => url.pathname === '/api/v1/bcart/pending-changes/count',
    async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ count: (opts.changes ?? MOCK_PENDING_CHANGES).length }) })
    },
  )
  await page.route(
    (url) => url.pathname === '/api/v1/bcart/pending-changes/reflect',
    async (route) => {
      const body = JSON.parse(route.request().postData() || '{}')
      const ids: number[] = body.all ? (opts.changes ?? MOCK_PENDING_CHANGES).map((c: any) => c.productSetId) : body.productSetIds
      const failingIds = new Set(opts.reflectFails ?? [])
      const results = ids.map((id) => failingIds.has(id)
        ? { productSetId: id, status: 'FAILED', errorMessage: 'B-CART API returned 422' }
        : { productSetId: id, status: 'SUCCESS' })
      await route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({
          succeeded: results.filter(r => r.status === 'SUCCESS').length,
          failed: results.filter(r => r.status === 'FAILED').length,
          results,
        }),
      })
    },
  )
  await page.route(
    (url) => /^\/api\/v1\/bcart\/product-sets\/\d+\/pricing$/.test(url.pathname),
    async (route) => {
      const body = JSON.parse(route.request().postData() || '{}')
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 12345, ...body }) })
    },
  )
}
```

### 5.2 シナリオ

| ID | テストケース | 操作 | 期待 | 優先度 |
|---|---|---|---|---|
| E2E-01 | サイドバーから画面遷移 | サイドバー「B-CART」展開 → 「変更点一覧」クリック | URL `/bcart/pending-changes`, 見出し表示 | P0 |
| E2E-02 | 一覧表示・diff レンダリング | 画面遷移 | 行 2 件、`単価: 1000 → 1100`, `配送Sz: 0 → 0.5` 文字列表示 | P0 |
| E2E-03 | 件数バッジ件数表示（任意、実装した場合のみ） | サイドバー表示 | バッジに `2` | P2 |
| E2E-04 | チェックボックス選択→「選択を反映」 | 1 行目 ☑ → ボタン押下 | `succeeded=1` トースト、行が消える | P0 |
| E2E-05 | 「全件反映」 | ボタン押下 | `succeeded=2` トースト、テーブル空状態 | P0 |
| E2E-06 | 反映失敗ハンドリング | `reflectFails: [12345]` → 全件反映 | `failed=1`, 失敗行が赤背景＆エラーメッセージ | P0 |
| E2E-07 | 空状態 | `changes: []` で画面表示 | 「未反映の変更はありません」表示、ボタン disabled | P0 |
| E2E-08 | 件数超過（500 trunc 表示） | `changes` に 500 件 + `truncated=true` | 警告メッセージ「件数が多すぎます」 | P1 |
| E2E-09 | 200 件超で 400 受領 | `reflect` を 400 で返却 | エラートースト `件数上限 200` | P1 / **C-2** |
| E2E-10 | 編集→未反映表示→反映の往復 | 詳細画面でセット編集 → pending-changes へ移動 → 反映 | end-to-end フローが完結 | P0 |
| E2E-11 | 詳細画面のインライン編集（セット一覧タブ） | `/bcart/products/678` の単価入力 → 保存 | toast `セット ... を保存しました（B-CARTには未反映）`, PUT 呼出 | P0 |
| E2E-12 | バリデーション（フロント） | `shippingSize=2` を入力して保存 | クライアント側で 1.0 上限の警告、API 呼ばれない | P1 |
| E2E-13 | 反映中スピナー | reflect レスポンスを 1 秒遅延させる | ボタン disabled + spinner 表示 | P2 |

**実装例（E2E-04）:**
```ts
test('E2E-04: 選択行のみ反映できる', async ({ page }) => {
  await mockAllApis(page)
  await mockBCartPendingRoutes(page)
  await loginAndGoto(page, '/bcart/pending-changes')

  await page.getByRole('row', { name: /ラ・フランス/ }).getByRole('checkbox').check()
  await page.getByRole('button', { name: '選択を反映' }).click()

  await expect(page.getByText('成功: 1')).toBeVisible()
  await expect(page.getByRole('row', { name: /ラ・フランス/ })).toHaveCount(0)
})
```

**実装例（E2E-10 編集→反映フロー）:**
```ts
test('E2E-10: 編集 → 一覧 → 反映 まで通る', async ({ page }) => {
  await mockAllApis(page)
  await mockBCartPendingRoutes(page)
  await page.route(
    (u) => u.pathname === '/api/v1/bcart/products/678',
    async (route) => route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ id: 678, name: 'ラ・フランス', sets: [{ id: 12345, name: 'ケース(10入)', unitPrice: 1000, shippingSize: 0 }] }) }))
  await loginAndGoto(page, '/bcart/products/678')

  await page.getByLabel('単価', { exact: false }).fill('1100')
  await page.getByRole('button', { name: '保存' }).first().click()
  await expect(page.getByText('保存しました', { exact: false })).toBeVisible()

  await page.goto('http://localhost:3000/bcart/pending-changes')
  await page.getByRole('button', { name: '全件反映' }).click()
  await expect(page.getByText('成功: 2')).toBeVisible()
})
```

### 5.3 サイドバー登録テスト（既存 `sidebar-navigation.spec.ts` への追記）

| ID | テスト | 期待 |
|---|---|---|
| SN-01 | B-CART グループに「B-CART変更点一覧」リンクが存在 | リンク href=`/bcart/pending-changes`, `Diff` アイコン |

---

## 6. 手動検証 / スモークテスト

### 6.1 B-CART 実 API への PATCH 疎通（実装初期、設計書 §10 の確認待ち事項を消し込む）

実行環境: 開発環境（B-CART sandbox or 開発トークン）。

**手順:**
```bash
# 1. 既存セット 1 件の現在値を取得
curl -H "Authorization: Bearer $BCART_DEV_TOKEN" \
     "https://api.bcart.jp/api/v1/product_sets/<実在 setId>"

# 2. unit_price のみで PATCH
curl -X PATCH \
     -H "Authorization: Bearer $BCART_DEV_TOKEN" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "unit_price=1100" \
     "https://api.bcart.jp/api/v1/product_sets/<setId>"
# 期待: 200 OK、レスポンス JSON で unit_price=1100

# 3. shipping_size を追加で PATCH
curl -X PATCH \
     -H "Authorization: Bearer $BCART_DEV_TOKEN" \
     -d "shipping_size=0.5" \
     "https://api.bcart.jp/api/v1/product_sets/<setId>"
# 期待: 200 OK、shipping_size=0.5

# 4. 元値に戻す（後処理）
curl -X PATCH ... -d "unit_price=<元値>&shipping_size=<元値>"
```

**確認項目:**
- [ ] フィールド名 `unit_price` / `shipping_size` で B-CART が受け付けるか（仕様未確認領域）
- [ ] `shipping_size` 上限 1.0 の境界（`shipping_size=1.0` が成功、`1.01` が 422）
- [ ] エラーレスポンスの構造（422 のフィールド指摘形式）
- [ ] レート制限到達時の 429 ヘッダ（`Retry-After` の有無）

### 6.2 アプリ全層スモーク（実装フェーズ末）

実行: ローカル backend `./gradlew bootRun --args='--spring.profiles.active=web,dev'` + frontend `npm run dev`。

**手順:**
1. `/bcart/products/<実在 productId>` を開きセット 1 件の `unit_price` を +10 に変更 → 保存
2. DB 確認: `psql -p 55544 -d oda32db -c "SELECT * FROM b_cart_change_history ORDER BY id DESC LIMIT 5;"` で 1 行 INSERT
3. `b_cart_product_sets.b_cart_price_reflected=false` を確認
4. `/bcart/pending-changes` 表示で当該行が出現
5. 「選択を反映」 → success トースト
6. DB 確認: `b_cart_change_history.b_cart_reflected=true`, `b_cart_product_sets.b_cart_price_reflected=true`
7. B-CART 側で当該セットの単価が反映されていることを管理画面 or API で確認
8. **後処理**: 元値に戻し、もう 1 ループ実行して二重反映でも整合性が崩れないことを確認

### 6.3 レート制限到達時の挙動観察（任意 / Phase 3-A 内では非ブロッカ）

200 件の編集を一括反映 → B-CART 429 が混在することを確認。
- 失敗行が UI に赤背景表示されること
- 成功行は `reflected=true` に更新され、再試行で残り失敗行のみ再送されること
- backend ログに 429 レスポンスボディが記録されること

---

## 7. テスト優先度マトリクス

### P0（必須・リリースブロッカ）
- Service Unit: PS-U01 / 02 / 03 / 04 / 06 / 08 / 10、RS-U01 / 02 / 03 / 04 / 05 / 06 / 07 / 08、TX-U01 / 04
- Repository Integration: REP-I01 / 02 / 03 / 05
- Controller Integration: PSC-I01 / 02 / 03、PCC-I01 / 02 / 03 / 05 / 06 / 07 / 09
- Trans/Concurrency: TX-I01 / 02
- Validation: VL-U01 / 02 / 03
- E2E (mock): E2E-01 / 02 / 04 / 05 / 06 / 07 / 10 / 11
- 手動: §6.1 1 件スモーク、§6.2 アプリ全層スモーク

### P1（高・推奨）
- Service Unit: PS-U05 / 07 / 09、RS-U09 / 10 / 11 / 12、TX-U02
- Repository Integration: REP-I04 / 06
- Controller Integration: PSC-I04 / 05、PCC-I04 / 08
- Trans/Concurrency: TX-I03
- Validation: VL-U04 / 05
- E2E: E2E-08 / 09 / 12
- Sidebar: SN-01

### P2（任意）
- Service Unit: RS-U13、TX-U03
- E2E: E2E-03 / 13
- 手動: §6.3 レート制限観察

### Critical/Major レビュー対応の必須カバレッジ（再掲）

| レビュー指摘 | 対応テスト |
|---|---|
| C-1 別 Bean トランザクション (`@Transactional public` プロキシ保証) | TX-U04, TX-I01 / 02 |
| C-2 件数上限 200 件 | PCC-I07, E2E-09 |
| M-1 history id list 保持 + `WHERE id IN (...)` | RS-U07 / 08, REP-I05 |
| M-2 主源泉を `b_cart_change_history` に統一 | REP-I02 |
| M-3 `res.body() != null` NPE 防止 | RS-U10 |
| §5 最古 before / 最新 after | RS-U06, REP-I01 |

---

## 8. カバレッジ目標

| レイヤ | 目標 | 計測 |
|---|---|---|
| バックエンド Service (新規 3 クラス) | line ≥ 90%, branch ≥ 85% | JaCoCo (`./gradlew jacocoTestReport`) |
| バックエンド Controller (新規 2 クラス) | line ≥ 80% | JaCoCo |
| バックエンド全体（差分） | line ≥ 80% (CLAUDE.md グローバル目標) | JaCoCo |
| バックエンド Repository (拡張メソッド) | 全メソッドに 1 件以上 IT 存在 | 目視レビュー |
| フロントエンド主要パス | E2E `pending-changes` ページの primary フロー (E2E-01,02,04,05,07,10,11) PASS | Playwright report |

未カバー許容領域:
- DTO の getter/setter（Lombok 生成）
- 例外メッセージ文字列詳細
- B-CART API エラーレスポンスのバリエーション（422 / 5xx の本文構造）

---

## 9. テストデータ

### 9.1 Unit テスト
- インメモリ POJO で完結、Mockito stub。fixture ファイル不要。

### 9.2 Repository / Controller Integration
- `@Sql` または `@TestEntityManager` でテストごとに fixture を投入。
- 主要 fixture:
  - `b_cart_products`: `id=678, name='テスト商品'`
  - `b_cart_product_sets`: `id=12345, product_id=678, unit_price=1000.00, shipping_size=0, b_cart_price_reflected=true`
  - `b_cart_change_history`: 各テストで動的に INSERT
- スキーマは Flyway 既存 migration を Testcontainers で適用（**migration 不要**は設計書 §5 で明記）。

### 9.3 E2E (mock)
- `mockAllApis(page)` のキャッチオール → fixture mock を上書き（§5.1 参照）。
- 共通 fixture を `e2e/helpers/mock-api.ts` の末尾に **追加** することを推奨（既存 `MOCK_*` の命名に揃え `MOCK_BCART_PENDING_CHANGES` などとして export）。

### 9.4 E2E (smoke 実バックエンド)
- 本番 DB クローン or 開発 DB の `b_cart_product_sets` 任意の 1 行を使用。
- テスト後は元値に戻す（§6.2 手順 8 で巻き戻し）。
- B-CART 側のテスト用商品セット 1 件を「テスト専用」として固定確保することを開発者間で合意。

---

## 10. NG 時の対応指針

### 10.1 切り分けフローチャート

```
テスト失敗
  │
  ├─ Unit 落ち
  │     └─ 期待値が設計書と乖離? → 設計書再確認
  │     └─ Mockito stub と実装の API 呼出が不一致? → 実装バグ濃厚
  │
  ├─ Integration (Controller) 落ち
  │     └─ 401 / 403? → JWT セットアップ確認、実装側の SecurityFilter
  │     └─ 400? → Validation 制約 vs 実装 DTO の mismatch 確認
  │     └─ 500 + ロールバック? → トランザクション境界・例外伝播確認 (TX-I01/02)
  │
  ├─ Integration (Repository) 落ち
  │     └─ aggregate 結果が想定外? → SQL native query を `psql` で直接確認
  │     └─ flush 順序問題? → `em.flush(); em.clear();` を fixture セットアップ後に追加
  │
  ├─ E2E (mock) 落ち
  │     └─ 要素が見つからない (Timeout)?
  │         ├─ Playwright trace で実 DOM を確認
  │         ├─ getByText partial match の衝突 → exact:true へ変更（MEMORY 既知パターン）
  │         └─ shadcn/ui 内部構造の変更 → ロケータ更新
  │     └─ 401 リダイレクト? → mock の `/api/v1/auth/me` を確認
  │     └─ Mock route の優先順位ミス? → 個別 route を catch-all 後に登録（LIFO で先勝ち）
  │
  ├─ E2E (smoke) 落ち
  │     └─ backend 起動失敗? → `./gradlew bootRun --args=...` のログ確認、port 8090 衝突
  │     └─ DB に履歴行が入らない? → トランザクション commit、JPA flush タイミング
  │     └─ B-CART API が 401? → `BCartApiConfig.getInstance().getAccessToken()` の値が dev か
  │
  └─ B-CART API 疎通 (手動) 落ち
        └─ 422 + フィールドエラー? → 設計書 §10 確認待ち項目（フィールド名要修正、定数化済みなら 1 箇所）
        └─ 429? → §6.3 で観察、リトライ頻度を下げる
```

### 10.2 不安定テスト (flaky) への対処

- **WAITING**: `page.waitForLoadState('networkidle')` または `expect(...).toBeVisible({ timeout })` を活用、`waitForTimeout` の固定 sleep は禁止。
- **mock 順序**: `mockAllApis` のキャッチオール後に個別 mock を登録するパターンを厳守（LIFO）。
- **Toast 検証**: shadcn/ui sonner の toast は短時間で消えるため、即時 `expect(getByText)` を入れる。

### 10.3 仕様齟齬を発見した場合

1. **テストを通すために実装を歪めない**。設計書を一次ソースとして PR コメントで指摘。
2. 設計書側を修正すべきと判断したら設計書を先に PR で更新し、テスト計画書も併せて改版。
3. 実装の方が正しいと判断したら設計書をその場で更新（このドキュメントを refresh、`s-design-review` で再確認）。

### 10.4 テスト不安定とバグの切り分けチェックリスト

- [ ] 同一テストを 5 回連続実行して全 PASS なら本物のバグ修正を疑う、3 回以上 PASS なら flaky を疑う
- [ ] CI と local で挙動差? → タイムゾーン / locale / 並列実行数を比較
- [ ] Playwright `--trace on` でリプレイ
- [ ] 失敗時のスクリーンショット / network log を artifact に保存（既存 `playwright.config.ts` を流用）

---

## 11. 実行コマンド早見表

```powershell
# Backend Unit + Integration
cd backend
./gradlew test --tests "jp.co.oda32.domain.service.bcart.*"
./gradlew test --tests "jp.co.oda32.api.bcart.*"
./gradlew test jacocoTestReport

# Frontend type check
cd frontend
npx tsc --noEmit

# Frontend E2E (mock)
npx playwright test bcart-pending-changes.spec.ts

# Frontend E2E (smoke 実バックエンド)
# 別ターミナルで backend 起動後
npx playwright test bcart-pending-changes.spec.ts --project=chromium-real-backend  # 設定追加が必要

# 個別 spec を debug モードで
npx playwright test bcart-pending-changes.spec.ts --debug
```

---

## 12. 完了判定（Definition of Done）

以下が **すべて** 満たされたらテストフェーズ完了とみなす。

- [ ] §3 Unit テスト P0 / P1 全 PASS、JaCoCo 行カバレッジ 80%+ (新規クラス 90%+)
- [ ] §4 Integration テスト P0 / P1 全 PASS
- [ ] §5 E2E (mock) P0 全 PASS、P1 8 割以上 PASS
- [ ] §6.1 B-CART API 1 件 PATCH スモーク 200 OK 確認 → 設計書 §10 確認待ち事項クローズ
- [ ] §6.2 アプリ全層スモーク 8 ステップ全クリア
- [ ] レビュー C-1 / C-2 / M-1 / M-2 / M-3 対応テストが緑
- [ ] CI で 3 回連続 PASS（flaky なし）
- [ ] テスト計画書のテストケース ID が実テストコードのコメントから参照可能（`// PS-U03` 等）

---

## 13. 参考

- 設計書: `claudedocs/design-bcart-pending-changes.md`
- 既存パターン: `frontend/e2e/bcart.spec.ts`, `frontend/e2e/helpers/mock-api.ts`
- 既存 Spring Test サンプル: `backend/src/test/java/jp/co/oda32/domain/service/finance/InvoiceImportServiceTest.java`
- メモリ: `feedback_incremental_review.md`（モック PASS だけに頼らず実バックエンド疎通を 1 パス必須）

---

## 13. テスト計画レビュー後の追補（2026-05-01）

### MA-1: PSC-I01 に DTO 契約アサーション追加 [C-impl-1]
Entity → DTO 化を保証するため、Integration テストに lazy/internal フィールドが漏れていないことを検証:

```java
.andExpect(jsonPath("$.id").exists())
.andExpect(jsonPath("$.unitPrice").value(1100))
.andExpect(jsonPath("$.shippingSize").value(0.5))
.andExpect(jsonPath("$.bCartPriceReflected").value(false))
.andExpect(jsonPath("$.groupPrices").doesNotExist())
.andExpect(jsonPath("$.specialPrices").doesNotExist())
.andExpect(jsonPath("$.stockParent").doesNotExist())
.andExpect(jsonPath("$.bCartProducts").doesNotExist());
```
§7 Critical/Major 対応表に `C-impl-1: DTO 契約 → PSC-I01（拡張）` を追加。

### MA-2: REP-I07 追加（フラグ/履歴の不整合状態）
設計書 §7「`b_cart_price_reflected=false` だが history 0 件」のケース:

```java
@Test
void findUnreflectedForProductSet_履歴ゼロでフラグfalseでも空リストを返す() {
    BCartProductSets set = saveSet(s -> s.setBCartPriceReflected(false));
    // history 行は意図的に作成しない
    List<BCartChangeHistory> result = repository.findUnreflectedForProductSet(
        set.getId(), List.of("unit_price", "shipping_size"));
    assertThat(result).isEmpty();
}
```

### MI-1: E2E-10 fixture を動的化
`mockBCartPendingRoutes(page, { items: 1 })` で 1 件 fixture に切替、アサーションを `成功: 1` に統一。

### MI-2: `truncated` の場所統一
レスポンスボディに `truncated: true`（ヘッダではない）。`jsonPath("$.truncated", is(true))` に統一。

### MI-3: curl コマンドの Content-Type 明示
§6.1 全 PATCH/PUT curl に `-H "Content-Type: application/x-www-form-urlencoded"` を必須化（415 防止）。
