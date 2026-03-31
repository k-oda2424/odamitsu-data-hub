# バックエンド コードレビュー報告書

**対象**: backend/src/main/java/jp/co/oda32/ (547ファイル)
**レビュー日**: 2026-03-31
**レビュー範囲**: API Controllers, Security/Config, Domain Services/DTOs/Entities, テストカバレッジ

---

## 指摘サマリ

| Severity | 件数 | 主要テーマ |
|----------|------|-----------|
| **Blocker** | 2 | SQLインジェクション、任意Bean実行 |
| **Critical** | 7 | 認証バイパス、@Transactional欠如、NPE、未型付きリクエスト、Repository直接注入 |
| **Major** | 20+ | ページネーション欠如(全Controller)、BeanUtils mass assignment、未使用パラメータバグ、N+1クエリ、RBAC欠如、ログイン率制限なし |
| **Minor** | 10+ | POST→200、throws Exception、日付型不統一、dev環境秘密情報 |

---

## Blocker (マージブロック - 即時対応必須)

### B-1: SQLインジェクション - MGoodsService.buildQuery()
**ファイル**: `domain/service/goods/MGoodsService.java` L180-200
**問題**: ユーザー入力が直接ネイティブSQL文字列に結合されている
**修正案**: パラメータバインド (`?` プレースホルダー + `setParameter()`) に変更、または Spring Data JPA Specification を使用

### B-2: 任意Bean実行 - BatchController
**ファイル**: `api/batch/BatchController.java` L22-24
**問題**: ユーザー指定の `jobName` から `applicationContext.getBean(jobName + "Job")` で任意のSpring Beanを取得・実行可能。攻撃者がBean名を推測して任意のバッチを実行できる
**修正案**:
```java
private static final Set<String> ALLOWED_JOBS = Set.of(
    "accountsPayableAggregation", "purchaseFileImport", "smilePaymentImport" /* ... */
);

@PostMapping("/execute")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> execute(@RequestParam String jobName) {
    if (!ALLOWED_JOBS.contains(jobName)) {
        return ResponseEntity.badRequest().body("Unknown job: " + jobName);
    }
    // ...
}
```

---

## Critical (即時修正が必要)

### C-1: 論理削除ユーザーが認証可能
**ファイル**: `domain/service/login/LoginUserService.java` L73-86
**問題**: `loadUserByUsername()` が `del_flg` をチェックしない。削除済みユーザーでもJWTが発行される
**修正案**: `findByLoginIdAndDelFlg(loginId, "0")` に変更

### C-2: @Transactional がControllerに配置
**ファイル**: `api/goods/SalesGoodsController.java` L116-131
**問題**: `reflectToMaster()` の `@Transactional` がController層にある。Spring AOP のプロキシが正しく動作しない場合がある。トランザクションスコープにHTTPシリアライゼーションが含まれる
**修正案**: Service層に移動

### C-3: NPE（NullPointerException）リスク
**ファイル**: `api/purchase/PurchaseController.java` L31, `api/order/OrderController.java` L31
**問題**: `getByPK()` の結果をnullチェックなしで使用。存在しないリソースへのアクセスで500エラー
**修正案**: Service層で `Optional` を返すか、存在しない場合に `ResponseEntity.notFound()` を返す

### C-4: 未型付きMapリクエストボディ
**ファイル**: `api/estimate/EstimateController.java` L59
**問題**: `@RequestBody Map<String, String>` で `@Valid` なし。`estimateStatus` に任意の文字列を設定可能
**修正案**: `EstimateStatusUpdateRequest` DTOを作成し、`@Valid` + `@Pattern` で `EstimateStatus` enum値に制限

### C-5: Repository直接注入（Service層バイパス）
**ファイル**: `api/purchase/SendOrderController.java` L18, `api/dashboard/DashboardController.java` L20
**問題**: ControllerにRepositoryを直接注入。ビジネスロジック・認可チェック・トランザクション管理が不在
**修正案**: Service層クラスを新設

### C-6: 全Service層の@Transactional欠如
**問題**: ほぼ全てのServiceの書き込みメソッドに `@Transactional` がない。読み取りメソッドにも `@Transactional(readOnly = true)` がない
**影響**: データ不整合リスク、Hibernateダーティチェック最適化の喪失

### C-7: 例外ハンドリング - raw Exception
**問題**: 全Serviceの書き込みメソッドが `throws Exception` を宣言。ドメイン固有例外ではなく汎用Exceptionを使用
**修正案**: `ResourceNotFoundException`, `BusinessRuleViolationException` 等のドメイン例外を定義

---

## Major (修正推奨)

### API設計

| ID | ファイル | 問題 | 修正案 |
|----|---------|------|--------|
| M-1 | 全Controller (14個) | **ページネーション欠如**: 全リストAPIが `List<T>` を返す。データ増加でOOM/性能劣化 | `Pageable` + `Page<T>` を導入 |
| M-2 | GoodsController L120, SalesGoodsController L79,93,109 | **BeanUtils.copyProperties** によるmass assignment: リクエストDTOの全フィールドがEntityに無制限コピー。`delFlg`等が上書きされるリスク | 明示的フィールドマッピングに変更 |
| M-3 | FinanceController L26-28 | **未使用パラメータバグ**: `shopNo`, `supplierNo` を受け取るが `findAll()` を呼び出し。フィルタが機能していない | Specificationでフィルタリングを実装 |
| M-4 | SendOrderController L22 | **未使用パラメータバグ**: `shopNo` を受け取るが `findAll()` を呼び出し | 同上 |
| M-5 | BCartController L30 | **status未検証**: 任意の文字列でB-CART出荷ステータスを設定可能 | `BcartShipmentStatus` enumでバリデーション |

### ドメインロジック

| ID | ファイル | 問題 | 修正案 |
|----|---------|------|--------|
| M-6 | WSalesGoodsService.update/insert | **バグ**: `BeanUtils.copyProperties` 後のクリーン済み商品名が入力パラメータに書き戻され、保存対象エンティティに反映されない | 明示的フィールドマッピングに変更 |
| M-7 | MGoodsUnitService.delete() | **バグ**: `getGoodsNo()` を `findById()` に渡している。`getGoodsUnitNo()` が正しい主キー | 主キー取得を修正 |
| M-8 | MMaker.getShopNo() | **バグ**: 常に `null` を返す。ShopCheckAopのバリデーションをバイパス | フィールドを正しく返すよう修正 |
| M-9 | MasterController L36-37, L53-56 | **インメモリフィルタリング**: DBで全件取得後Javaでフィルタ。不要なデータ転送 | DBクエリでフィルタ |

### セキュリティ

| ID | ファイル | 問題 | 修正案 |
|----|---------|------|--------|
| M-10 | SecurityConfig L44 | **anyRequest().permitAll()**: API以外のエンドポイントが全公開。Actuator等が無認証でアクセス可能 | `.anyRequest().denyAll()` に変更 |
| M-11 | LoginUserService L11 | **RBAC欠如**: 全ユーザーに `ROLE_USER` のみ付与。`companyType` がSpring Security権限に反映されない | `companyType` を `GrantedAuthority` にマッピング |
| M-12 | AuthController | **ログイン率制限なし**: ブルートフォース攻撃に対する防御なし | Bucket4j等で率制限を実装 |
| M-13 | JwtTokenProvider | **リフレッシュトークン/ログアウト未実装**: トークン漏洩時に無効化手段なし（24時間有効） | リフレッシュトークンフロー導入 |
| M-14 | BatchController L35 | **例外メッセージ漏洩**: `e.getMessage()` をクライアントに返却。内部情報（DB接続文字列等）漏洩リスク | 汎用メッセージ返却 + サーバーサイドログ |

### N+1クエリリスク

| ID | ファイル | 問題 |
|----|---------|------|
| M-15 | GoodsController L41-42 | `GoodsResponse::from` がLazy関連（maker等）にアクセスする場合、N+1クエリ発生 |
| M-16 | GoodsDetailResponse.from() | `salesGoodsList` のマッピングでN+1 |
| M-17 | CommonService.getPartnerMap() | Lazy関連へのループアクセス |

---

## Minor (改善推奨)

| ID | 問題 | 対象 |
|----|------|------|
| m-1 | POST成功時にHTTP 200ではなく201を返すべき | GoodsController, SalesGoodsController, StockController |
| m-2 | `throws Exception` をドメイン例外に限定 | 全Controller |
| m-3 | `closingDate` パラメータが `String` 型 (`LocalDate` + `@DateTimeFormat` が適切) | FinanceController |
| m-4 | JWT検証エラーのログ出力なし (攻撃検知不能) | JwtTokenProvider L61-68 |
| m-5 | dev環境のDB資格情報・JWT秘密鍵がソースにハードコード | application-dev.yml |
| m-6 | CORS `allowedHeaders("*")` が過度に寛容 | CorsConfig |
| m-7 | DashboardController のDTO構築パターンが他Controllerと不統一 | DashboardController L25-30 |

---

## テストカバレッジ分析

| カテゴリ | ソースファイル数 | テスト数 | カバレッジ |
|---------|----------------|---------|-----------|
| **合計** | **547** | **6** (実質4クラス) | **~1%** |
| API Controller | 14 | 1 (AuthController) | 7% |
| Service層 | 74 | 0 | **0%** |
| Batch処理 | 146 | 0 | **0%** |
| Repository | 70 | 0 | **0%** |
| Entity/DTO | 154 | 0 | **0%** |
| Config | 13 | 1 (JwtTokenProvider) | 8% |
| Utility | 22 | 2 | 9% |

### テスト優先度

| 優先度 | 対象 | 理由 |
|--------|------|------|
| **P0** | Service層（財務計算、在庫管理） | ビジネスロジックの正確性が未検証 |
| **P0** | バッチジョブ（買掛金集計、SMILE連携） | 財務データパイプラインが未検証 |
| **P1** | 残り13個のController | APIコントラクトが未検証 |
| **P1** | Repository（カスタムクエリ） | SQL正確性が未検証 |
| **P2** | JaCoCo導入 | カバレッジ計測・閾値設定 |

---

## 修正優先順位（推奨）

### 第1優先（即時）
1. **B-1**: SQLインジェクション修正
2. **B-2**: BatchControllerのジョブ名ホワイトリスト化 + 権限チェック
3. **C-1**: 論理削除ユーザーの認証ブロック
4. **C-3**: NPEリスク修正（404レスポンス化）

### 第2優先（今スプリント内）
5. **C-2**: @Transactional をService層に移動
6. **C-4**: EstimateController の型付きDTO化
7. **M-2**: BeanUtils.copyProperties → 明示的マッピング
8. **M-3/M-4**: 未使用パラメータバグ修正
9. **M-6/M-7/M-8**: ドメインロジックバグ修正

### 第3優先（次スプリント）
10. **M-10**: SecurityConfig の `anyRequest().denyAll()` 化
11. **M-11**: RBAC導入
12. **C-6**: 全Service層に @Transactional 追加
13. **M-1**: ページネーション導入（段階的）

### 第4優先（計画的に）
14. **M-12/M-13**: 率制限・リフレッシュトークン
15. テストカバレッジ向上（P0対象から順次）
