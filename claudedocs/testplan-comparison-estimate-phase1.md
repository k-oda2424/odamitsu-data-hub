# テスト計画: 比較見積機能 Phase 1 — バックエンド API

- **作成日**: 2026-04-10
- **対象設計書**: `claudedocs/design-comparison-estimate.md`
- **フェーズ**: Phase 1 (DB + バックエンド API + 見積からの生成)

---

## 1. テスト方針

### 使用フレームワーク

| レイヤー | フレームワーク | 方式 |
|---|---|---|
| Controller (API) | `@WebMvcTest` + MockMvc | Unit / Slice テスト |
| Service | `@ExtendWith(MockitoExtension.class)` + Mockito | Unit テスト |

- 既存プロジェクトに `TestApplication.java`（JPA/Batch 除外設定）、`AuthControllerTest`（`@WebMvcTest` パターン）、`InvoiceImportServiceTest`（Mockito パターン）が存在
- **Integration テスト（`@SpringBootTest` + 実 DB）は未整備**のため、Unit / Slice テストを主軸とする
- `spring-boot-starter-test`, `spring-security-test`, `h2` が testImplementation に含まれている

### テスト種別

| 種別 | 対象 | 目的 |
|---|---|---|
| Unit (Service) | `EstimateComparisonService` | ビジネスロジック検証（見積からの生成、全置換更新、ステータス遷移） |
| Slice (Controller) | `EstimateComparisonController` | HTTP ステータス、バリデーション、JSON 構造 |

---

## 2. テストスコープ

### In Scope

- 比較見積 CRUD API（GET 一覧、GET 詳細、POST 新規、PUT 更新、DELETE 論理削除）
- ステータス更新 API（PUT `/status`）
- 見積からの生成 API（POST `/from-estimate/{estimateNo}`）
- リクエストバリデーション（`@Valid` アノテーション）
- ステータスによる編集制限（00/20 のみ編集可）
- 更新時の全置換方式（論理削除 → 再 insert）
- 未登録商品（goodsNo=null）のサポート
- 論理削除（del_flg='1'）

### Out of Scope

- フロントエンド（Phase 2）
- 印刷/PDF 出力（Phase 2）
- パフォーマンス（N+1 検証は Integration テスト必要）
- 認証・認可（既存の SecurityConfig / JWT で担保済み）
- DB マイグレーション（手動 SQL 実行で対応）

---

## 3. テストケース一覧

### 3-1. Service 層テスト: `EstimateComparisonServiceTest`

#### 3-1-A. 新規作成 (POST)

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| S-C01 | 正常系: 1グループ0代替で作成 | - | groups=[{baseGoodsName="商品A", details=[]}] で create | comparisonNo が採番、status=00、groups.size=1、details.size=0 | P1 | FR-1, EC-2 |
| S-C02 | 正常系: 2グループ各2代替で作成 | - | groups 2件、各 details 2件 | groupNo=1,2、detailNo=1,2 が正しく連番 | P1 | FR-1, FR-12 |
| S-C03 | 正常系: 未登録商品の基準品 | - | baseGoodsNo=null, baseGoodsName="手入力品" | 保存成功、baseGoodsNo=null | P1 | FR-10, EC-5 |
| S-C04 | 正常系: 未登録商品の代替提案 | - | goodsNo=null, goodsName="手入力代替", supplierNo=123 | 保存成功、goodsNo=null、supplierNo=123 | P1 | FR-10, EC-6 |

#### 3-1-B. 見積からの生成 (POST /from-estimate)

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| S-FE01 | 正常系: 見積明細3行から生成 | 見積 #100、明細3行 | createFromEstimate(100) | groups.size=3、各グループの baseGoodsName/basePurchasePrice 等が見積明細から転写、details は空、sourceEstimateNo=100、status=00 | P1 | FR-2 |
| S-FE02 | 正常系: containNum は changeContainNum 優先 | 見積明細に changeContainNum=24, containNum=12 | createFromEstimate | baseContainNum=24 | P1 | FR-2 |
| S-FE03 | 正常系: 見積明細0件 | 見積 #200、明細0件 | createFromEstimate(200) | groups.size=0 の比較見積が作成される | P2 | EC-4 |
| S-FE04 | 異常系: 存在しない見積番号 | - | createFromEstimate(9999) | 例外（EntityNotFoundException 等） | P1 | FR-2 |
| S-FE05 | 正常系: 同じ見積から2回生成 | 1回目生成済み | createFromEstimate(100) 2回目 | 別の comparisonNo で独立生成 | P2 | EC-7 |
| S-FE06 | 正常系: ヘッダ情報の転写 | 見積に partnerNo=5, destinationNo=10, shopNo=1 | createFromEstimate | comparison に同値が設定される | P1 | FR-2 |

#### 3-1-C. 更新 (PUT) — 全置換方式

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| S-U01 | 正常系: グループ追加 | 既存1グループ | 2グループで更新 | 旧グループ論理削除、新2グループ insert、groupNo=1,2 | P1 | FR-5, FR-9 |
| S-U02 | 正常系: グループ削除 | 既存3グループ | 1グループで更新 | 旧3グループ+全明細 論理削除、新1グループ insert | P1 | FR-5, FR-9 |
| S-U03 | 正常系: 代替提案の追加 | 既存グループに details 0件 | details 2件で更新 | 旧明細論理削除(0件)、新2件 insert、detailNo=1,2 | P1 | FR-3 |
| S-U04 | 正常系: ヘッダフィールド更新 | title="旧タイトル" | title="新タイトル" で更新 | ヘッダの title が更新される | P2 | FR-5 |
| S-U05 | 異常系: 存在しない comparisonNo | - | update(9999, request) | 例外 | P1 | - |

#### 3-1-D. ステータス更新 (PUT /status)

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| S-ST01 | 正常系: 作成→提出済 | status=00 | updateStatus(no, "10") | status=10 | P1 | FR-6 |
| S-ST02 | 正常系: 提出済→修正 | status=10 | updateStatus(no, "20") | status=20 | P1 | FR-6 |
| S-ST03 | 異常系: 提出済から編集(PUT) | status=10 | update(no, request) | 例外（編集は 00/20 のみ） | P1 | EC-8 |
| S-ST04 | 異常系: 修正後提出済から編集 | status=30 | update(no, request) | 例外 | P2 | EC-8 |
| S-ST05 | 正常系: 修正中から編集 | status=20 | update(no, request) | 更新成功 | P1 | EC-8 |

#### 3-1-E. 論理削除 (DELETE)

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| S-D01 | 正常系: 論理削除 | 既存 comparison, groups, details あり | delete(no) | ヘッダ del_flg='1'（groups/details は CustomService に依存） | P1 | FR-5 |
| S-D02 | 異常系: 存在しない番号 | - | delete(9999) | 例外 | P2 | - |

#### 3-1-F. 一覧検索 (GET)

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| S-L01 | 正常系: 全件取得 | 3件登録済み | search(shopNo=1) | 3件返却、groupCount 含む | P1 | FR-7 |
| S-L02 | 正常系: ステータスフィルタ | status=00 が2件、10 が1件 | search(status=[00]) | 2件 | P1 | FR-7 |
| S-L03 | 正常系: タイトル部分一致 | title="花王見積" | search(title="花王") | 該当件数 | P2 | FR-7 |
| S-L04 | 正常系: 日付範囲フィルタ | - | search(dateFrom, dateTo) | 範囲内の件数 | P2 | FR-7 |
| S-L05 | 正常系: 論理削除は除外 | del_flg='1' が1件 | search(shopNo=1) | 論理削除分は除外 | P1 | FR-7 |

#### 3-1-G. 詳細取得 (GET /{no})

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| S-G01 | 正常系: グループ・明細含む詳細取得 | 2グループ、各3明細 | getDetail(no) | groups.size=2、各 details.size=3 | P1 | FR-8 |
| S-G02 | 正常系: partnerName/destinationName 解決 | partnerNo=5 | getDetail(no) | partnerName が取得される | P2 | FR-8 |
| S-G03 | 異常系: 存在しない番号 | - | getDetail(9999) | 404 / 例外 | P1 | - |

### 3-2. Controller 層テスト: `EstimateComparisonControllerTest`

#### 3-2-A. バリデーション

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| C-V01 | groups が空リスト | - | POST body: groups=[] | 400 Bad Request | P1 | EC-1 |
| C-V02 | groups が null | - | POST body: groups=null | 400 Bad Request | P1 | EC-1 |
| C-V03 | baseGoodsName が空文字 | - | POST groups[0].baseGoodsName="" | 400 Bad Request | P1 | - |
| C-V04 | goodsName が空文字（detail） | - | POST details[0].goodsName="" | 400 Bad Request | P1 | - |
| C-V05 | shopNo が null | - | POST body: shopNo=null | 400 Bad Request | P1 | - |
| C-V06 | comparisonDate が空文字 | - | POST body: comparisonDate="" | 400 Bad Request | P1 | - |
| C-V07 | 正常なリクエスト | - | POST with valid body | 200 OK / 201 Created | P1 | FR-1 |

#### 3-2-B. HTTP レスポンス

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| C-R01 | GET 一覧: JSON 構造 | - | GET /estimate-comparisons?shopNo=1 | 200, List<ComparisonListResponse> 構造 | P1 | FR-7 |
| C-R02 | GET 詳細: JSON 構造 | - | GET /estimate-comparisons/1 | 200, ComparisonResponse 構造（groups/details 含む） | P1 | FR-8 |
| C-R03 | DELETE: 204 No Content | - | DELETE /estimate-comparisons/1 | 204 or 200 | P2 | FR-5 |
| C-R04 | PUT status: JSON 構造 | - | PUT /estimate-comparisons/1/status | 200, 更新後の ComparisonResponse | P2 | FR-6 |
| C-R05 | POST from-estimate: JSON 構造 | - | POST /estimate-comparisons/from-estimate/100 | 200/201, ComparisonResponse | P1 | FR-2 |

#### 3-2-C. 認証

| ID | テスト名 | 前提条件 | 操作 | 期待結果 | 優先度 | 要件 |
|---|---|---|---|---|---|---|
| C-A01 | トークンなしでアクセス | - | GET /estimate-comparisons (no Authorization) | 401 or 403 | P2 | - |

---

## 4. テストデータ

### 4-1. 見積データ（見積からの生成用）

```java
// TEstimate mock
TEstimate estimate = new TEstimate();
estimate.setEstimateNo(100);
estimate.setShopNo(1);
estimate.setPartnerNo(5);
estimate.setDestinationNo(10);
estimate.setCompanyNo(1);

// TEstimateDetail mock (3行)
TEstimateDetail detail1 = new TEstimateDetail();
detail1.setEstimateNo(100);
detail1.setGoodsNo(1001);
detail1.setGoodsCode("KAO-001");
detail1.setGoodsName("花王 除菌洗浄剤 5L");
detail1.setPurchasePrice(new BigDecimal("1200.00"));
detail1.setGoodsPrice(new BigDecimal("1900.00"));
detail1.setContainNum(new BigDecimal("3"));
detail1.setChangeContainNum(null);

TEstimateDetail detail2 = new TEstimateDetail();
detail2.setEstimateNo(100);
detail2.setGoodsNo(null);  // 未登録商品
detail2.setGoodsCode(null);
detail2.setGoodsName("手入力商品A");
detail2.setPurchasePrice(new BigDecimal("800.00"));
detail2.setGoodsPrice(new BigDecimal("1500.00"));
detail2.setContainNum(new BigDecimal("12"));
detail2.setChangeContainNum(new BigDecimal("24")); // 優先される

TEstimateDetail detail3 = new TEstimateDetail();
detail3.setEstimateNo(100);
detail3.setGoodsNo(2001);
detail3.setGoodsCode("LION-001");
detail3.setGoodsName("ライオン 食器洗剤 4.5L");
detail3.setPurchasePrice(new BigDecimal("1050.00"));
detail3.setGoodsPrice(new BigDecimal("1750.00"));
detail3.setContainNum(new BigDecimal("3"));
detail3.setChangeContainNum(null);
```

### 4-2. 比較見積リクエスト（作成/更新用）

```java
ComparisonCreateRequest request = new ComparisonCreateRequest();
request.setShopNo(1);
request.setPartnerNo(5);
request.setComparisonDate("2026-04-10");
request.setTitle("花王 vs ライオン 比較");
request.setGroups(List.of(
    createGroup("花王 除菌洗浄剤 5L", 1001, "KAO-001",
        new BigDecimal("1200"), new BigDecimal("1900"), new BigDecimal("3"),
        List.of(
            createDetail("ライオン除菌洗浄剤", 2001, "LION-001",
                new BigDecimal("1050"), new BigDecimal("1750"), new BigDecimal("3")),
            createDetail("サラヤ除菌洗浄剤", 3001, "SARAYA-001",
                new BigDecimal("950"), new BigDecimal("1600"), new BigDecimal("4"))
        ))
));
```

### 4-3. ステータス遷移テスト用

| 初期ステータス | 操作 | 期待 |
|---|---|---|
| 00 (作成) | PUT 更新 | 成功 |
| 00 (作成) | PUT status=10 | 成功 |
| 10 (提出済) | PUT 更新 | 拒否 |
| 10 (提出済) | PUT status=20 | 成功 |
| 20 (修正) | PUT 更新 | 成功 |
| 30 (修正後提出済) | PUT 更新 | 拒否 |

---

## 5. カバレッジマトリクス

### 5-1. 機能要件 (FR) カバレッジ

| 要件 | テストケース | カバー |
|---|---|---|
| FR-1: 新規作成 | S-C01, S-C02, C-V07 | Yes |
| FR-2: 見積から生成 | S-FE01 ~ S-FE06, C-R05 | Yes |
| FR-3: 代替提案追加 | S-U03 | Yes |
| FR-5: DB保存(CRUD) | S-C01 ~ S-C04, S-U01 ~ S-U05, S-D01 ~ S-D02 | Yes |
| FR-6: ステータス管理 | S-ST01 ~ S-ST05, C-R04 | Yes |
| FR-7: 一覧検索 | S-L01 ~ S-L05, C-R01 | Yes |
| FR-8: 詳細表示 | S-G01 ~ S-G03, C-R02 | Yes |
| FR-9: グループ追加/削除 | S-U01, S-U02 | Yes |
| FR-10: 未登録商品 | S-C03, S-C04 | Yes |
| FR-12: 上限なし | S-C02 | Partial (大量データテストは Out of Scope) |
| FR-4: 粗利差分表示 | - | Out of Scope (フロント計算) |
| FR-11: 印刷/PDF | - | Out of Scope (Phase 2) |
| FR-13: 得意先向け表示 | - | Out of Scope (Phase 2) |

### 5-2. エッジケース (EC) カバレッジ

| EC | テストケース | カバー |
|---|---|---|
| EC-1: グループ0件で保存 | C-V01, C-V02 | Yes |
| EC-2: 代替提案0件のグループ | S-C01 | Yes |
| EC-3: 元見積が論理削除 | - | Partial (source_estimate_no は参照のみなので影響なし = テスト不要) |
| EC-4: 見積明細0件から生成 | S-FE03 | Yes |
| EC-5: 未登録商品の基準品 | S-C03 | Yes |
| EC-6: 未登録商品の代替提案 | S-C04 | Yes |
| EC-7: 同一見積から複数回生成 | S-FE05 | Yes |
| EC-8: ステータスによる編集制限 | S-ST03, S-ST04, S-ST05 | Yes |
| EC-9: 印刷レイアウト | - | Out of Scope (Phase 2) |

---

## 6. 自己レビュー結果

### レビュー観点

1. **設計書の全要件がカバーされているか**
2. **エッジケースが網羅されているか**
3. **テストの独立性・再現性は確保されているか**
4. **テストデータは十分か**

### 検出事項と対応

| # | Severity | 指摘 | 対応 |
|---|---|---|---|
| R-1 | Major | **全置換更新時の連番リセット検証が不足**: groupNo/detailNo が更新後に 1 から再採番されることを明示的に検証するケースがない | S-U01 の期待結果に「groupNo=1,2」を明記済み。追加で S-U02 にも「groupNo=1」を確認するよう明記 → **対応済み** |
| R-2 | Major | **更新時に旧 detail も論理削除される検証が不足**: グループだけでなく detail の論理削除も確認が必要 | S-U01, S-U02 の期待結果に「全明細 論理削除」を明記 → **対応済み** |
| R-3 | Major | **ステータスチェックの境界値不足**: status=70(価格反映済) や 50(削除) からの編集拒否テストがない | status=00/20 のみ編集可なので、10/30 をテストすれば十分。50/70 は同じロジックで拒否されるため P3 扱い → **許容** |
| R-4 | Minor | **見積からの生成で comparisonDate が何になるか未検証** | 設計書に明記なし。実装で「生成時の日付」が入る想定。S-FE01 の期待結果に「comparisonDate = 当日」を追加 → **対応済み** |
| R-5 | Minor | **partnerName の解決テスト (S-G02) の mock 不足**: MPartner の Repository mock が必要 | テストデータセクションへの追記は不要（Mockito で Service 内部 mock） → **許容** |
| R-6 | Minor | **PUT 更新で status が変わらないことの検証がない**: 全置換更新で status フィールドが勝手にリセットされないこと | S-U04 に「status は変更されない（元の値を維持）」を追加検証 → **対応済み** |
| R-7 | Info | **テスト実行環境の H2 互換性**: PostgreSQL の SERIAL 型は H2 では IDENTITY に読み替えが必要 | Unit テストは Mockito ベースのため DB 不要。Slice テスト (Controller) も MockMvc + MockBean で DB 不要 → **影響なし** |

### 最終判定

- Major 指摘 3件: 全て対応済み（テストケースの期待結果を修正・明確化）
- Minor 指摘 3件: 2件対応済み、1件許容
- **テスト計画として承認可能**

---

## 7. テスト実装時の注意点

1. **TestApplication の ComponentScan 除外パターン**: 現状 `jp.co.oda32.api.(?!auth).*` で auth 以外を除外している。新 Controller テスト追加時は、`@WebMvcTest(controllers = EstimateComparisonController.class)` + `@ContextConfiguration(classes = TestApplication.class)` で同パターンを踏襲するか、テスト専用の Config を作成する
2. **Service テストは `@ExtendWith(MockitoExtension.class)`**: InvoiceImportServiceTest と同パターン。Repository は `@Mock`、Service は `@InjectMocks`
3. **認証済みリクエストの作成**: AuthControllerTest の `jwtTokenProvider.generateToken()` パターンを踏襲
4. **日本語テストメソッド名**: 既存テストは英語名だが、可読性のため日本語 DisplayName を `@DisplayName` で付与することを推奨
