# コードレビュー: B-Cart 出荷情報入力画面 + バッチ修正（バックエンド）

対象ブランチ: `main` 未コミット変更
レビュー日: 2026-04-14
レビュー範囲: 新規6ファイル + 変更6ファイル + 削除1ファイル
基準: `CLAUDE.md`（Controller 薄く / DTO 分離 / `@Valid` / 論理削除）+ Spring Boot 3.3 ベストプラクティス

---

## サマリー

| Severity | 件数 |
|---|---|
| Critical | 4 |
| Warning  | 10 |
| Info     | 8 |

特に深刻なものは下記 4 点。
1. `BCartOrderService.save(List)` が 1 件ずつ `save` するため、`BCartLogistics(CascadeType.ALL, EAGER)` 経由で意図しない再保存・N 回の flush が走る（Critical）。
2. `syncBCartOrderStatus` で `orderProductList.getBCartLogistics()` を触るが `@EntityGraph` に `orderProductList.bCartLogistics` が含まれておらず LazyInitialization / N+1 の温床（Critical）。
3. `GlobalExceptionHandler.handleGeneral` が例外クラス名＋メッセージをそのまま `detail` に載せて返しており、本番では情報漏えいリスク（Critical）。
4. `BcartShipmentStatus.fromJson` が `IllegalArgumentException` を投げると `handleIllegalArgument` が 400 で返すため、不正値の enum 変換が `400 { message: "Unknown ..." }` となり、Controller 側の「displayName / enum名 両対応＋不正値黙殺」と挙動が矛盾する（Critical: 仕様不整合）。

---

## 1. BCartController.java （刷新）

### 🔴 Critical

#### C-1: `BcartShipmentStatus.fromJson` と Controller 側変換の仕様不整合
**場所**: `BCartController.java:41-50` + `BcartShipmentStatus.java:33-43`

GET `/shipping` は `List<String> statuses` を受けて Controller 側で enum 変換を手動実装し、不正値は「黙殺」。一方で PUT `/shipping` と `/shipping/bulk-status` は `@RequestBody` で `BcartShipmentStatus` を直接受けるため `@JsonCreator fromJson` が走り、不正値は `IllegalArgumentException` → `GlobalExceptionHandler.handleIllegalArgument` で 400。

GET と PUT で不正値の扱いが異なると API 利用側が混乱する。

**修正案**: GET も enum バインディングに寄せる、または `fromJson` を null 返却にし一元化する。
```java
@RequestParam(required = false) List<BcartShipmentStatus> statuses
```
Spring は `@JsonCreator` ではなく `Converter`/`Formatter` か enum デフォルトバインディング（`BcartShipmentStatus.valueOf`）を使うので、`displayName` を URL クエリで受けたいなら `Converter<String, BcartShipmentStatus>` を `WebMvcConfigurer.addFormatters` に登録するのが Spring 流。

### 🟡 Warning

#### W-1: Controller に DTO 変換ロジックが残っている（CLAUDE.md 違反）
**場所**: `BCartController.java:35-52`

「Controller は薄く」規約に反し、List<String> → List<BcartShipmentStatus> の変換が 18 行。Service に移すか、上記 Converter 化で解消する。

#### W-2: `statuses.stream().filter(Objects::nonNull).toList()` が不要
**場所**: `BCartController.java:51`

上のループで `if (st != null) converted.add(st);` として既に null を弾いているため、`filter(Objects::nonNull)` は冗長。さらに `.toList()`（Java 16+ unmodifiable）と上段の `ArrayList` 代入が混在しており可読性が低い。

#### W-3: 409 / 404 等の HTTP ステータス設計がない
**場所**: `BCartController.java:56-68`

`saveAll`/`bulkUpdateStatus` は全て `200 OK` 固定。対象が 0 件で何も更新しなかった / 権限違反以外の業務エラー（例: status=SHIPPED でロックされて更新対象外になった件数がある）をクライアントに返せない。`ResponseEntity<Map<String, Integer>>` で `{ updated: N, skipped: M }` のような形が望ましい（旧画面が該当件数を表示していたなら必要）。

### 🔵 Info

- `import java.util.ArrayList; import java.util.Objects;` が `java.util.List` の import より上に挟まっていて見づらい（Import 順を Spotless 等で固定するとよい）。
- クラスレベル `@PreAuthorize("isAuthenticated()")` はよい。ただし本 API は shop 依存機能なので、`@PreAuthorize` ではなく Service 側の `checkBCartShopAccess()` に委譲している点を Javadoc に明記したい。

---

## 2. BCartShippingInputService.java

### 🔴 Critical

#### C-2: `syncBCartOrderStatus` が `orderProductList.bCartLogistics` を Lazy で触っており N+1 / LazyInit の危険
**場所**: `BCartShippingInputService.java:365-369` + `BCartOrderRepository.java:29-31`

```java
@EntityGraph(attributePaths = {"orderProductList"})  // ← bCartLogistics を含まない
List<BCartOrder> findWithProductsByIdIn(...)
```
直後に
```java
order.getOrderProductList().stream()
    .map(BCartOrderProduct::getBCartLogistics)  // ← LAZY
```
が走る。`BCartOrderProduct.bCartLogistics` は `@ManyToOne`（fetch はデフォルト EAGER だが明示なし）で、`BCartOrder.orderProductList` は LAZY。`@EntityGraph` で orderProductList を取得しても、その子 `bCartLogistics` は取得対象外のため各プロダクトごとに追加 SELECT が発行される。

**修正案**:
```java
@EntityGraph(attributePaths = {"orderProductList", "orderProductList.bCartLogistics"})
@Query("SELECT DISTINCT o FROM BCartOrder o WHERE o.id IN :idList")
List<BCartOrder> findWithProductsByIdIn(@Param("idList") List<Long> idList);
```
Cartesian 問題回避のため `DISTINCT` を追加。また `bCartLogistics` 側の `bCartOrderProductList` は EAGER なので、結果として全グラフが引かれる点は動作確認が必要（MultipleBagFetchException 対策として `@BatchSize` 利用を検討）。

#### C-3: `save(List)` が 1 件ずつ save する実装のまま使われている
**場所**: `BCartOrderService.java:23-27`（呼出: `BCartShippingInputService.java:135, 383`）

```java
public void save(List<BCartOrder> bCartOrderList) {
    for (BCartOrder bCartOrder : bCartOrderList) {
        this.save(bCartOrder);  // N 回 flush 可能性
    }
}
```
`saveAll` を使えば selectCount 1 回 + batched insert/update になる。同様に `BCartLogisticsService.save(List)` も要確認。

**修正案**:
```java
public List<BCartOrder> save(List<BCartOrder> list) {
    return bCartOrderRepository.saveAll(list);
}
```

#### C-4: `BCartLogistics.bCartOrderProductList` が `cascade = ALL + EAGER` のまま order も更新する二重経路
**場所**: `BCartLogistics.java:110` + `BCartShippingInputService.java:122-130, 133-135`

`logistics.bCartOrderProductList` は CascadeType.ALL なので、`bCartLogisticsService.save(targets)` を呼ぶと product も cascade save される。さらに product 経由で取得した `BCartOrder.adminMessage` を変更して `bCartOrderService.save(orderUpdates)` でも保存しているため、保存順によっては **CascadeType.ALL 経由で dirty チェックが order にまで波及**（`BCartOrderProduct.bCartOrder` は ManyToOne なので insertable/updatable=false、直接は cascade しないが、ManyToOne の EAGER ロード+dirty の副作用で flush 順の読みにくさ）。

少なくとも `cascade = ALL` は過剰。`MERGE` や `{PERSIST, MERGE}` で十分なはずで、誤った delete 伝播の事故リスクが高い。このレビュー範囲の改修対象ではないが、本サービスが積極的にこの関係を触るようになったため再評価を推奨。

### 🟡 Warning

#### W-4: `search()` の partnerCode フィルタがメモリ内 substring 検索で性能リスク
**場所**: `BCartShippingInputService.java:85-90`

「logistics 全件取得 → SMILE 突合 → レスポンス構築後に `contains(needle)` でフィルタ」のため、大量の logistics を DB からメモリ上に展開する。現運用でも `findByStatusIn` が PK 無限定の取得なので、**日付や期間で範囲を絞り込む条件**の追加が将来的に必須。

暫定としても JPQL 側で `TSmileOrderImportFile.customerCode` と JOIN して logistics_id を先に絞る方が望ましい。

#### W-5: `@Transactional(readOnly = true)` 内でエンティティの dirty state 変更は禁止だが、convertSetQuantity で変更している
**場所**: `BCartShippingInputService.java:63, 214, 319-323`

```java
@Transactional(readOnly = true)
public List<BCartShippingInputResponse> search(...)
  ...
  .peek(this::convertSetQuantity)  // record.setSetQuantity(ONE)
```
`readOnly=true` でもエンティティの setter は呼べるが、flush はされないため副作用は無いものの、**将来別トランザクションで flush されたときに意図せず DB を上書きするバグ源**。一時変換ならローカル変数で持つか、DTO/レコードを使って mapping すべき。

**修正案**:
```java
private BigDecimal normalizeSetQuantity(TSmileOrderImportFile r) {
    BigDecimal v = r.getSetQuantity();
    return (v == null || BigDecimal.ZERO.compareTo(v) == 0) ? BigDecimal.ONE : v;
}
```
でローカル値として扱う。

#### W-6: `checkBCartShopAccess` の try-catch が広すぎる
**場所**: `BCartShippingInputService.java:166-171`

```java
try {
    loginShopNo = LoginUserUtil.getLoginUserInfo().getUser().getShopNo();
} catch (Exception e) {
    throw new AccessDeniedException("ログインしていません");
}
```
`NullPointerException` や `IllegalStateException` も一律「ログインしていません」になってしまう。`LoginUserUtil` の null 返却か特定の例外かを確認し、ピンポイントで catch するほうが安全。ログも無いので調査困難になる。

#### W-7: `resolveSmileProductCode` が package-private アクセスレベル
**場所**: `BCartShippingInputService.java:325`

`String resolveSmileProductCode(...)` に修飾子なし。private でよいならそうすべき。テストからの呼び出し用であればコメントを残す。

#### W-8: `ArrayList` ではなく `List.copyOf` / `Collectors.toUnmodifiableList` のほうが意図明確
**場所**: `BCartShippingInputService.java:102-103, 361`

`bCartOrderService.findWithProductsByIdIn(new ArrayList<>(affectedOrderIds))` — Set→List 変換は `List.copyOf(affectedOrderIds)` で十分（不変性も得られる）。

#### W-9: `(a, b) -> a` / `(a, b) -> b` のマージ関数が混在して意図が不明瞭
**場所**: `BCartShippingInputService.java:106, 350, 369`

- L106 `toMap(..., (a,b) -> a)` 重複 request の最初を採用
- L350 `toMap(..., (a,b) -> b)` 重複 logistics の最後を採用
- L369 `toMap(..., (a,b) -> a)`

一致性がなくバグの温床。それぞれ何故その選択か Javadoc コメントを残すか、事前 `distinct` で正規化すべき。

#### W-10: `goodsInfo` 内の連結フォーマットにマジックな `：`（全角）と `:`（半角）混在
**場所**: `BCartShippingInputService.java:278`

```java
smile.getProductCode() + "：" + product.getProductName() + ":" + smile.getQuantity()
```
旧システム踏襲だとは思うが、定数化・Formatter 化を推奨。

### 🔵 Info

- `BCartLogisticsKey` record の `equals`/`hashCode` を独自実装するなら、`record` キーワードの自動生成を利用する意味が薄い（ただし `BigDecimal.compareTo` 同値を採用する必要があるため現実装は妥当）。`record` の代わりに通常クラスのほうが誤解を招かない。
- `Arrays.asList(...)` の代わりに `List.of(...)` を使うと immutable かつ意図明確（`L67-69`）。
- `Collectors.toList()` → `.toList()`（Java 16+）へ統一の余地。
- ログは log4j2 と slf4j が混在（Service は `@Log4j2`、Handler は `@Slf4j`）。プロジェクト規約統一推奨。

---

## 3. BCartShippingInputResponse.java / BCartShippingUpdateRequest.java / BCartShippingBulkStatusRequest.java

### 🟡 Warning

#### W-11: `@Data` は equals/hashCode を生成する（Lombok ハザード）
**場所**: 3 DTO 全て

`@Data` = `@Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor`。DTO に対してはほぼ問題ないが、同一性比較を意図しないなら `@Getter @Setter @ToString` に留めるか、Java 16+ の `record` を使う方が Spring Boot 3.3 的には好ましい。

#### W-12: `shipmentDate` が文字列のまま（`@Pattern` で緩く検証）
**場所**: `BCartShippingUpdateRequest.java:19-20`

型は `LocalDate` が望ましい。`@JsonFormat(pattern="yyyy-MM-dd")` + Jackson で変換すれば空文字列問題も `@JsonSetter(nulls = Nulls.AS_EMPTY)` などで統一できる。旧 entity が String 保持なら DTO だけでも LocalDate 化する価値がある。

#### W-13: `memo` / `adminMessage` に最大長バリデーションがない
**場所**: `BCartShippingUpdateRequest.java:22-24`

DB カラム長を超える値が届くと Hibernate 実行時例外 → 500。`@Size(max = N)` を付与。

### 🔵 Info

- `@JsonProperty("bCartLogisticsId")` / `@JsonProperty("bCartCsvExported")` が無いと Jackson は `bcartLogisticsId` / `bcartCsvExported` に lowercase する（Lombok の getter 名規則）。明示は正しいがグローバルに `PropertyNamingStrategies.LOWER_CAMEL_CASE` を保証する方が堅牢。
- `BCartShippingBulkStatusRequest` の `bCartLogisticsIds` にも `@Size(max = 1000)` 等、DoS 対策として上限を付けることを推奨。

---

## 4. BCartShippingResponse.java（削除）

### 🔵 Info

- 旧 DTO の削除は `git grep` で参照が残っていないか最終確認を推奨（`BCartShippingResponse` を Grep したが参照は見つからなかった）。
- `BCartShippingInputResponse` へのリネーム＋責務変更という性質なので、PR の説明文に rename 経緯を残しておくこと。

---

## 5. LocalDateTypeAdapter.java

### 🟡 Warning

#### W-14: `deserialize` で空文字列/null を許容しているが `serialize` は null チェックなし
**場所**: `LocalDateTypeAdapter.java:40-42`

```java
public JsonElement serialize(LocalDate src, Type type, ...) {
    return new JsonPrimitive(src.format(formatter));  // src が null なら NPE
}
```
Gson は通常 null フィールドを serialize する前に hook するが、`serializeNulls()` 有効時や null 明示時に NPE。

**修正案**:
```java
return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(formatter));
```

#### W-15: `deserialize` の `json.getAsString()` が `JsonNull` に対して例外
**場所**: `LocalDateTypeAdapter.java:28`

`json` が `JsonNull.INSTANCE` の場合 `UnsupportedOperationException` が飛ぶ。先に `json.isJsonNull()` をチェックして `null` 返却を推奨。

### 🔵 Info

- 2 引数コンストラクタを公開しているが利用箇所がない（B-Cart API は ISO_LOCAL_DATE 固定想定）。YAGNI 原則に従い削除可能。

---

## 6. BCartController → BCartShippingInputService 連携全体（アーキテクチャ）

### 🟡 Warning

#### W-16: Service 内部で Entity を直接返さない CLAUDE.md 規約は守られているが、内部 record `BCartLogisticsKey` が public になっている
**場所**: `BCartShippingInputService.java:390`

`public record BCartLogisticsKey(...)` は Service 内部ロジック専用なのに public。`private static record` にすべき。

---

## 7. BCartOrderRegisterTasklet.java（変更: LocalDate TypeAdapter 追加）

### 🔵 Info

- `LocalDateTypeAdapter` の追加は妥当。`CustomNumberDeserializer` 登録との整合も取れている。
- 本ファイル既存課題（本改修対象外）:
  - `log.info(response)` は Response オブジェクトを toString しているだけで情報量が乏しい（L61）。
  - `RepeatStatus.CONTINUABLE` を失敗時に返しているが、Spring Batch の CONTINUABLE は「再度 execute を呼ぶ」意味。失敗時は `throw` するか `RepeatStatus.FINISHED` + ExitStatus FAILED が正しい（L53, 58, 81）。これは既存コードの問題だが、改修ついでに直す価値あり。

---

## 8. SmileOrderFileImportConfig.java（変更: 2 Step 追加）

### 🟡 Warning

#### W-17: Step Bean メソッドに `@Bean` が付いていない
**場所**: `SmileOrderFileImportConfig.java:99-156` 全 Step メソッド

`smileOrderImportStep()` / `bCartOrderProcessingSerialNumberUpdateStep()` も含め、全て `public Step xxxStep()` だが `@Bean` が付いていない。`.next(smileOrderImportStep())` のようなメソッド直接呼び出しでは動くが、Spring Batch のジョブ再起動・Step 名解決・JMX 公開で期待通りに動かない可能性がある。

**修正案**: 全 Step メソッドに `@Bean` を付与（既存コードからの累積課題だが、新規追加 2 Step だけでも付けたほうが整合）。

#### W-18: Step の依存順序コメントが不足
**場所**: `SmileOrderFileImportConfig.java:82-97`

新しく `smileOrderImportStep`（SMILE→DB 反映）と `bCartOrderProcessingSerialNumberUpdateStep`（B-Cart 処理連番更新）が追加されたが、なぜ `smileOrderImportStep` が `orderStatusUpdateStep` より前で、B-Cart 連番更新が後なのかの Javadoc が無い。`BCartShippingInputService.search` は `psn_updated=true` のレコードのみ `smileSerialNoList` を埋めるので、この順序は出荷情報入力画面の正確な表示に直結する。コメントで明示すべき。

### 🔵 Info

- `BCartOrderProcessingSerialNumberUpdateTasklet` を SMILE 側の Config に置くのは疎結合の観点で気になる。B-Cart 用 Config に切り出し、Job 内から `@Bean` で参照するほうがパッケージ境界がきれい（ただし今回の改修範囲を超える）。

---

## 9. BcartShipmentStatus.java（@JsonCreator/@JsonValue 追加）

### 🔴 Critical

#### C-5（再掲: C-1 と連動）: `fromJson` の例外が IllegalArgumentException → 400 と混ざる
上記 C-1 参照。enum 不正値の 400 は `MethodArgumentTypeMismatchException` ハンドラで `detail: "invalid enum value"` 的に専用対応するのが望ましい。

### 🟡 Warning

#### W-19: `purse` は typo（`parse` のはず）
**場所**: `BcartShipmentStatus.java:24`

旧コード由来だが、`fromJson` 追加のタイミングで `parse` にリネームすべき。API 規約への影響無し。

### 🔵 Info

- `@JsonValue` に getter を兼任させているのは綺麗。
- enum 名 fallback を `fromJson` に埋めると、将来 i18n で displayName を変えた際に旧データの永続化文字列が読めなくなる。DB 保存値は `name()` ベースにするのが堅実（現状 DB に `"未発送"` 生文字列が入っているようなので要確認）。

---

## 10. BCartOrderRepository.java / BCartOrderService.java

### 🔴 Critical 再掲

- **C-2**: `findWithProductsByIdIn` の EntityGraph に `orderProductList.bCartLogistics` が含まれない。
- **C-3**: `BCartOrderService.save(List)` が `saveAll` を使わず N 回 save。

### 🟡 Warning

#### W-20: `@Autowired` フィールド注入 + フィールド非 final
**場所**: `BCartOrderService.java:16-17`

```java
@Autowired
private BCartOrderRepository bCartOrderRepository;
```
Spring Boot 3.x / CLAUDE.md の「Service 層は Constructor Injection」ベストプラクティスに反する。`@RequiredArgsConstructor + private final` に変更を推奨。既存コードだが、新規 method 追加のタイミングで治すとよい。

---

## 11. GlobalExceptionHandler.java（AccessDenied + detail）

### 🔴 Critical

#### C-6: 本番で例外クラス名と raw メッセージが漏えい
**場所**: `GlobalExceptionHandler.java:57-63`

```java
String detail = ex.getClass().getSimpleName() + ": " + (ex.getMessage() != null ? ex.getMessage() : "");
return ResponseEntity...body(Map.of("message", "システムエラーが発生しました", "detail", detail));
```

Spring のメッセージは SQL error の一部、Hibernate constraint 名、内部パスなどを含むことがあり、**スタックトレース類の情報漏えい**に該当する。少なくとも本番プロファイルでは `detail` を抑止すべき。

**修正案**:
```java
@Value("${app.expose-exception-detail:false}") boolean exposeDetail;
...
Map<String, String> body = new HashMap<>();
body.put("message", "システムエラーが発生しました");
if (exposeDetail) body.put("detail", detail);
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
```
`application-dev.yml` で `app.expose-exception-detail: true`、本番は未設定 or false。

### 🟡 Warning

#### W-21: `@ExceptionHandler(AccessDeniedException.class)` が Spring Security のフィルタ層で発生する AccessDenied を拾えない
**場所**: `GlobalExceptionHandler.java:49-54`

`@RestControllerAdvice` は Controller 層到達後の例外しか拾えない。`MethodSecurity` (`@PreAuthorize`) の例外も拾えるが、Service 層（`BCartShippingInputService.checkBCartShopAccess`）から throw した `AccessDeniedException` は Controller 経由で伝播するので ok。ただし Spring Security の `AccessDeniedHandler` （フィルタ層）で出す 403 とメッセージが異なる二系統ができるので、`SecurityConfig` の `AccessDeniedHandler` も同じ JSON 形式を返すよう揃えるのが望ましい。

### 🔵 Info

- `IllegalArgumentException` は単に 400 を返すだけだが、`message` がそのまま `ex.getMessage()` になるためこちらも情報漏えい観点を要チェック（呼び出し側が安全な文言しか投げないか）。

---

## まとめ（優先度順）

### 🔴 Critical（4 件 + 再掲 2 件）
- C-1 / C-5: `BcartShipmentStatus.fromJson` の例外ハンドリング仕様統一
- C-2: `findWithProductsByIdIn` の EntityGraph 不足（N+1 / LazyInit）
- C-3: `BCartOrderService.save(List)` を `saveAll` 化
- C-4: `BCartLogistics.bCartOrderProductList` の `CascadeType.ALL` 再評価
- C-6: 本番での `detail` 漏えい抑止

### 🟡 Warning（10 件）
- W-1〜3: Controller の責務過多、HTTP ステータス設計、冗長な stream
- W-4〜6: メモリ内 filter、readOnly Tx での setter、try-catch 粒度
- W-7〜10: アクセス修飾子、List 不変化、マージ関数一貫性、フォーマット定数
- W-11〜13: DTO の Lombok / 日付型 / 最大長
- W-14〜15: LocalDateTypeAdapter の null 安全
- W-17〜18: Step Bean の `@Bean` / 依存順序コメント
- W-19〜21: enum の typo、フィールド注入、Security フィルタ系エラー整合

### 🔵 Info（8 件）
- ログ framework 混在、Import 整形、`List.of` 推奨、Javadoc 補強、`record` 化、定数化、アクセス修飾、YAGNI など

---

## 推奨する対応優先度

1. **今 PR で修正推奨**: C-2, C-3, C-6, W-11〜15, W-17（Step Bean）
2. **次 PR**: C-1/C-5（API 仕様統一）、W-1〜4（Controller/性能）
3. **技術負債 Issue 化**: C-4（Cascade 再設計）、W-20（既存 @Autowired フィールド注入一斉置換）
