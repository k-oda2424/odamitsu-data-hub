# コードレビュー: 請求機能 (Cluster A)

レビュー日: 2026-05-04
対象: `InvoiceImportService` / `TInvoiceService` / `MPartnerGroupService` / `InvoiceVerifier` / `TInvoice` / `MPartnerGroup` / `TInvoiceSpecification` / `TInvoiceRepository` / `MPartnerGroupRepository` / `InvoiceResponse` / `InvoiceImportResult` / `PartnerGroupRequest` / `PartnerGroupResponse` / `BulkPaymentDateRequest` / `PaymentDateUpdateRequest` / `FinanceController` 該当箇所 / `frontend/components/pages/finance/invoices.tsx` / `InvoiceImportDialog.tsx` / `PartnerGroupDialog.tsx`
レビュアー: Opus サブエージェント
前提: 設計レビュー (`claudedocs/design-review-invoice-management.md`) で既出の Critical 3 / Major 7 / Minor 8 は再掲しない。コード固有の問題に限定する。

---

## サマリー

- 総指摘件数: **Blocker 0 / Critical 4 / Major 6 / Minor 7**
- マージ可否: **Block** (Critical-1 = stale closure による選択トグル不動作 / Critical-2 = 入金日クリア時の 400 エラー / Critical-3 = 取込時の shopNo 認可欠如 / Critical-4 = Excel 内重複 partnerCode で更新行が二重カウント＋取込結果不整合)

特に重大な新規発見:

1. **C-N1 (Critical)**: `invoices.tsx:248-301` の `useMemo(columns, [])` が **本当に空依存** で固定されており、`SelectCell selectedIds={selectedIds}` の `selectedIds` 参照が初回レンダー時の空 Set に張り付く。チェックボックスを ON にしても見た目が更新されない (実 state は更新されるが表示が乖離)。eslint-disable コメントの解釈が誤り。
2. **C-N2 (Critical)**: `PaymentDateCell` の `onBlur` が空文字列を `paymentDate: '' || null = null` に変換して送るが、`PaymentDateUpdateRequest.paymentDate` は `@NotNull` のため 400 になる。「入金日のクリア」操作が UI 上は操作可能なのに必ず失敗する。
3. **C-N3 (Critical)**: `POST /finance/invoices/import` (`FinanceController.java:447-462`) が `shopNo` を `LoginUserUtil` で正規化していないため、第1事業部ユーザーが `shopNo=2` を送って第2事業部の請求実績を上書き取込できる。設計レビュー C-2 が触れたのは入金日 / partner-group 系のみで、**取込側の IDOR は未指摘**。

---

## Critical 指摘 (新規)

### C-N1: `invoices.tsx:248-301` の `useMemo(columns, [])` が stale closure で `selectedIds` を初回 Set に張り付かせる

- **箇所**: `frontend/components/pages/finance/invoices.tsx:248-301`
- **問題**: `useMemo(() => [...], [])` の配列リテラル中で生成される `render: (item) => <SelectCell selectedIds={selectedIds} ... />` のアロー関数は、配列が初回ビルドされた時点のクロージャに `selectedIds` を捕捉する。`useMemo` は依存配列が空なので 2 回目以降は同じ配列 (= 同じ render 関数) を返す。結果として:
  - `toggleSelect` で `selectedIds` を更新しても、`<DataTable>` に渡される `columns[0].render` は古い `selectedIds` (= 初回の空 Set) を参照したまま。
  - `<SelectCell>` の `checked={selectedIds.has(invoiceId)}` は常に `false`。「全選択」ボタンも見た目だけ ON にならない (内部 state は変わるが UI 反映なし)。
  - `eslint-disable-next-line react-hooks/exhaustive-deps` のコメントは「`SelectCell/PaymentDateCell` が props 経由で最新 state を読む」と書いているが、**props として渡している `selectedIds` 自体がクロージャでキャプチャされた古い参照**なので前提が成立しない。
- **影響**: 一括反映 UI の主機能 (チェックボックスで選択 → 一括入金日反映) がブラックホール化する。グループ選択経由の一括選択 (`handleGroupSelect`) は `selectedIds` を直接 set するので state 自体は更新されるが、各行の表示反映が崩れる。
- **修正案**: `selectedIds` を `columns` の依存に入れる、あるいは `SelectCell` 内で外部 state を読むのをやめて Context 化する。最小修正は依存追加:

```tsx
const columns: Column<Invoice>[] = useMemo(() => [
  {
    key: '_select',
    header: '',
    render: (item: Invoice) => (
      <SelectCell invoiceId={item.invoiceId} selectedIds={selectedIds} onToggle={toggleSelect} />
    ),
  },
  // ... 他列
], [selectedIds, handlePaymentDateChange])
```

`selectedIds` 変更で `columns` が再生成されるため再レンダーのコストはあるが、`SelectCell` 自体は React.memo で抑止可能。なお F1 コメントが「checkbox uses extracted SelectCell」と書いてあるが、SelectCell に `selectedIds` Set 全体を渡しているため SelectCell の memoization も効かない (Set 参照が毎回変わる)。`isSelected: boolean` だけ渡すよう変更すべき:

```tsx
function SelectCell({ checked, invoiceId, onToggle }: { checked: boolean; invoiceId: number; onToggle: (id: number) => void }) {
  return <Checkbox checked={checked} onCheckedChange={() => onToggle(invoiceId)} ... />
}
// 呼び出し
render: (item) => (
  <SelectCell checked={selectedIds.has(item.invoiceId)} invoiceId={item.invoiceId} onToggle={toggleSelect} />
)
```

### C-N2: 入金日クリア操作が必ず 400 を返す (バリデーション設計と UI の不整合)

- **箇所**:
  - `frontend/components/pages/finance/invoices.tsx:71-91` (`PaymentDateCell` の `onBlur`)
  - `frontend/components/pages/finance/invoices.tsx:168-170` (`handlePaymentDateChange` で `value || null` に変換)
  - `backend/src/main/java/jp/co/oda32/dto/finance/PaymentDateUpdateRequest.java:9-12` (`@NotNull(message = "入金日は必須です")`)
- **問題**: `<Input type="date">` のクリアは空文字 `''` を返す。`onBlur` で `newVal !== (paymentDate ?? '')` を満たすため発火し、`handlePaymentDateChange(invoiceId, '')` → `paymentDate: '' || null` で `null` を送る。サーバの `PaymentDateUpdateRequest` は `@NotNull` のため 400 BadRequest。フロント側は `onError: () => toast.error('入金日の更新に失敗しました')` で握り潰す → **入金日のクリア (誤登録の取り消し) が「失敗トースト」しか出ず実質不可能**。
- **影響**: 入金消込の誤登録を訂正するルートが消滅。実運用時に DB 更新を要するハンディングへ。
- **修正案**:
  - 業務として「入金日をクリアできるようにする」なら `@NotNull` を外し `private LocalDate paymentDate;` のままに。Service 側で `inv.setPaymentDate(req.getPaymentDate())` をそのまま通す。同時にエラーメッセージを「null = クリア」と明示。
  - 業務として「クリアは不可」なら、フロント側で空文字入力時にミューテーションを発火させない。

```java
// PaymentDateUpdateRequest.java (クリア許容案)
@Data
public class PaymentDateUpdateRequest {
    private LocalDate paymentDate; // null = クリア
}
```

```tsx
// invoices.tsx (クリア不許容案)
onBlur={(e) => {
  const newVal = e.target.value
  if (!newVal) return // クリア操作はサポートしない
  if (newVal !== (paymentDate ?? '')) onUpdate(invoiceId, newVal)
}}
```

### C-N3: 取込時に `shopNo` の権限境界が無く、他事業部の月次請求実績を上書き可能 (IDOR)

- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:447-462` (`importInvoices`)、`InvoiceImportService.java:54-57`
- **問題**: 設計レビュー C-2 は `bulk-payment-date` / `payment-date` / `partner-groups` の IDOR を指摘済だが、**取込エンドポイントの shopNo 認可は未検証**。
  - `@RequestParam(required = false) Integer shopNo` をそのまま `InvoiceImportService.importFromExcel(file, shopNo)` に渡し、`shopNoParam != null ? shopNoParam : (...)` で採用される。
  - 第1事業部 (shopNo=1) のユーザーが偽の松山 Excel を `shopNo=2` で POST すれば、第2事業部の `(shopNo=2, closingDate=...)` 行を Phase 2 で SELECT → UPSERT で上書き。
  - 既存行が消えず残る件は設計 C-3 が指摘済だが、**「他事業部のデータを汚染できる」という権限漏れ自体は新規論点**。
- **影響**: 経理データの IDOR 取込。データロスではないが他事業部の請求書を上書き可能。
- **修正案**: Controller で認可、Service には正規化済みの `shopNo` を渡す。

```java
@PostMapping("/invoices/import")
public ResponseEntity<?> importInvoices(
        @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) Integer shopNo) {
    Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
    // 非 admin が他 shop を要求してきたら 403
    if (effectiveShopNo != null && shopNo != null && !effectiveShopNo.equals(shopNo)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップの取込はできません");
    }
    try {
        InvoiceImportResult result = invoiceImportService.importFromExcel(file, effectiveShopNo);
        return ResponseEntity.ok(result);
    } catch (...) { ... }
}
```

加えて、`InvoiceImportDialog.tsx:71` の説明文「ファイル名に「松山」を含む場合は第2事業部として取り込みます」も誤解を招く。設計レビュー m-3 と合わせ、UI に事業部セレクトを追加して `shopNo` 必須にすべき。

### C-N4: Excel 内に同じ `partnerCode` が複数行ある場合、`updatedRows` が二重カウント＋既存エンティティが二度上書きされる

- **箇所**: `InvoiceImportService.java:138-170` (Phase 2 UPSERT ループ)
- **問題**: SMILE の出力でサプライヤーの請求行が複数行に分かれる稀なケース (例: 同一 `partnerCode` が「通常分」と「修正分」で 2 行発生) が来ると、
  - `parsedInvoices` には 2 件の TInvoice (同 partnerCode) が並ぶ
  - `existingMap.get(parsed.getPartnerCode())` が同じ `existing` を 2 回返し、
    - 1 回目: setPreviousBalance(A), setNetSales(A), ... → `toSave.add(existing)` / `updatedRows++`
    - 2 回目: setPreviousBalance(B), setNetSales(B), ... → 同じ `existing` を再度 setter → `toSave` に重複追加 / `updatedRows++`
  - 結果: DB には B のみが残る (1 回目の値は失われる) かつ `updatedRows = 2` (実態 1) かつ `toSave` に同一エンティティが 2 件 → JPA は冪等に処理するが DTO のメトリクスが破綻。
- **影響**:
  - 同一 partnerCode の業務データ保持ポリシー (合算 or 後勝ち) が暗黙化していて運用混乱を招く。
  - `result.totalRows / insertedRows / updatedRows / skippedRows` の整合 (`totalRows == inserted + updated + skipped`) が崩れる。`InvoiceImportDialog` の表示が「3件処理 / 4件更新」のように出る可能性。
- **修正案**: Phase 1 終了時に `parsedInvoices` を partnerCode で dedup し、衝突時は明示的な仕様 (= 後勝ち / 合算 / エラー) を選ぶ。最低でも警告ログ + dedup。

```java
// Phase 1 終了後に dedup
Map<String, TInvoice> dedupMap = new LinkedHashMap<>();
for (TInvoice inv : parsedInvoices) {
    TInvoice prev = dedupMap.put(inv.getPartnerCode(), inv);
    if (prev != null) {
        log.warn("Excel 内で partnerCode={} が重複。後行で上書きします", inv.getPartnerCode());
    }
}
List<TInvoice> deduped = new ArrayList<>(dedupMap.values());
// 以降 deduped を Phase 2 で利用
```

合算が業務的に正しい場合は `deduped` ではなく金額フィールドを `add` で集約。設計書で運用判断を確定すること。

---

## Major 指摘 (新規)

### M-N1: `bulkUpdatePaymentDate` が「指定 ID のうち見つからなかった件」をレスポンスに含めず、サイレント失敗する

- **箇所**: `FinanceController.java:464-475`
- **問題**: 設計レビュー m-1 が `notFoundIds` 等の不足を Minor で指摘しているが、コードレベルでさらに踏み込むと:
  - `findByIds` は存在する分だけを返し、見つからなかった ID は単に WARN ログのみ。
  - C-2 (IDOR) を修正した後も「他 shop に属するため除外された ID」と「DB に存在しない ID」を区別できない。
  - `Map.of("updatedCount", invoices.size())` だけ返すため、フロント側 `bulkPaymentDateMutation.onSuccess` も「N 件」しか出せず原因切り分けに困る。
- **影響**: 一括反映の透明性欠如。経理が「100 件選んだが反映は 95 件」になっても理由が解らない。
- **修正案**:

```java
@PutMapping("/invoices/bulk-payment-date")
public ResponseEntity<?> bulkUpdatePaymentDate(@Valid @RequestBody BulkPaymentDateRequest request) {
    Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(null); // C-2 と組み合わせ
    List<TInvoice> invoices = tInvoiceService.findByIds(request.getInvoiceIds());
    Set<Integer> foundIds = invoices.stream().map(TInvoice::getInvoiceId).collect(Collectors.toSet());
    List<Integer> notFoundIds = request.getInvoiceIds().stream()
            .filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
    List<Integer> forbiddenIds = effectiveShopNo == null ? List.of()
            : invoices.stream()
                .filter(inv -> !effectiveShopNo.equals(inv.getShopNo()))
                .map(TInvoice::getInvoiceId).collect(Collectors.toList());
    invoices.removeIf(inv -> forbiddenIds.contains(inv.getInvoiceId()));
    invoices.forEach(inv -> inv.setPaymentDate(request.getPaymentDate()));
    tInvoiceService.saveAll(invoices);
    return ResponseEntity.ok(Map.of(
        "requestedCount", request.getInvoiceIds().size(),
        "updatedCount", invoices.size(),
        "notFoundIds", notFoundIds,
        "forbiddenIds", forbiddenIds
    ));
}
```

### M-N2: `MPartnerGroupService#update` の partnerCodes コピーが空文字 / null / 重複に無防備

- **箇所**: `MPartnerGroupService.java:32-47`、`PartnerGroupDialog.tsx:91-103`
- **問題**:
  - フロント側 `groupPartnerCodes.split(/[\n,\s]+/)` 後に `filter((c) => c.length > 0)` で空は除去するが、`map((c) => c.trim())` のみで重複除去はしない。
  - サーバ側 `request.getPartnerCodes()` をそのまま `.addAll()`。`m_partner_group_member` テーブルにユニーク制約が無い場合、`(partner_group_id, partner_code)` が重複登録される (設計 D-5 で指摘済だが、**コード上は service レイヤで Set 化していない**)。
  - 6桁ゼロ埋めも実施しないので「29」「000029」が両方登録されると `filterGroup.partnerCodes` は両方含む → invoice の `partnerCode = 000029` だけマッチ → グループ件数表示 (`groupName（${partnerCodes.length}件）`) が業務件数と乖離する。
- **影響**: グループ件数表示の信頼性低下、内部 join 時のパフォーマンス低下。
- **修正案**: Service 側で正規化 + dedup を強制。

```java
@Transactional
public MPartnerGroup create(PartnerGroupRequest request) {
    List<String> normalized = request.getPartnerCodes().stream()
            .filter(c -> c != null && !c.isBlank())
            .map(c -> c.trim())
            .map(MPartnerGroupService::normalizePartnerCode) // 6桁0埋め
            .distinct()
            .toList();
    MPartnerGroup group = MPartnerGroup.builder()
            .groupName(request.getGroupName().trim())
            .shopNo(request.getShopNo())
            .partnerCodes(new ArrayList<>(normalized))
            .build();
    return repository.save(group);
}

private static String normalizePartnerCode(String raw) {
    try { return String.format("%06d", Long.parseLong(raw.trim())); }
    catch (NumberFormatException e) { return raw.trim(); }
}
```

`update` 側も同じ正規化を経由させ、可能なら DB に `UNIQUE(partner_group_id, partner_code)` を追加 (設計 C-1 と組み合わせ)。

### M-N3: `TInvoiceService` の `findBySpecification` が field-level の `new TInvoiceSpecification()` を生成している

- **箇所**: `TInvoiceService.java:19` (`private final TInvoiceSpecification tInvoiceSpecification = new TInvoiceSpecification();`)
- **問題**: Specification は stateless な helper だが、Spring DI を使わず `new` で生成。テスト容易性が下がる (差し替え不可) し、プロジェクト規約 (`@RequiredArgsConstructor` + コンストラクタ injection) と一貫しない。同一クラス内の `findByDetailedSpecification` のみが利用しているので、`static` メソッドにするか、Specification を Spring Bean (`@Component`) にする。
- **影響**: コーディング規約違反、テスト時に Specification 生成ロジックを差し替え不能。
- **修正案**:

```java
// CommonSpecification 配下を static メソッド化するか、
// あるいは下記のように field 初期化のみ private static final にする
private static final TInvoiceSpecification SPEC = new TInvoiceSpecification();
```

または `@Component class TInvoiceSpecification ...` + コンストラクタ injection。

### M-N4: `getCellStringValue` の FORMULA 分岐が NUMERIC 時の小数表現で「100.0」を文字列化する罠

- **箇所**: `InvoiceImportService.java:259-273`
- **問題**: `case FORMULA -> { try { yield cell.getStringCellValue(); } catch (IllegalStateException e) { try { double val = cell.getNumericCellValue(); if (val == Math.floor(val)) yield String.valueOf((long) val); yield String.valueOf(val); } ... } }` というネスト。
  - FORMULA セルが文字列を返すなら `getStringCellValue()` は OK。
  - FORMULA セルが NUMERIC を返す場合、小数を持つと `String.valueOf(val)` → `"3.14"` という JS 風表記。これが直後 `convertPartnerCode` に流れると `Long.parseLong("3.14")` で `NumberFormatException` → IllegalArgumentException でロールバック (設計 C-3 と連動)。
  - 数式列 = 得意先コードという稀なシナリオだが「総合計」行検出 (`colE.contains("総合計")`) も `getCellStringValue` を経由するので、E 列が数式の場合に「総合計」検出が失敗する可能性。
- **影響**: 設計 C-3 に内包されるが、コード固有の「FORMULA 二重キャッチ」の入れ子は読みにくい上、`String.valueOf(double)` のリスクが残る。
- **修正案**: 共通の数値→文字列変換関数を切り出し、`%s/%d` 表記の一貫性を保つ。

```java
private static String formatNumeric(double val) {
    if (Double.isInfinite(val)) return null;
    if (val == Math.floor(val)) return String.valueOf((long) val);
    return new BigDecimal(val).toPlainString(); // "3.14" の "E" 表記回避
}

case FORMULA -> {
    try { yield cell.getStringCellValue(); }
    catch (IllegalStateException e) {
        try { yield formatNumeric(cell.getNumericCellValue()); }
        catch (Exception e2) { yield null; }
    }
}
```

### M-N5: `existingInvoices` を Specification で取得しているが Repository に専用 finder を持たせない

- **箇所**: `InvoiceImportService.java:139-143`、`TInvoiceRepository.java`
- **問題**:

```java
List<TInvoice> existingInvoices = tInvoiceRepository
        .findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("shopNo"), shopNo),
                cb.equal(root.get("closingDate"), closingDate)
        ));
```

Service が無名 Specification を直接組み立てており、Repository 規約 (`findByShopNoAndClosingDate` のような derived query) と一貫しない。`TInvoiceRepository` は他にも derived query (`findByPartnerCodeAndClosingDate` など) を提供しているのに、ここだけ Specification 直書き。CLAUDE.md (`Repository 直接注入`) や次セッションタスク B-8 と整合しない。

- **影響**: 同等の検索が複数の書き方で表現される → リファクタ時の grep 性が悪化。
- **修正案**:

```java
// Repository に追加
List<TInvoice> findByShopNoAndClosingDate(Integer shopNo, String closingDate);

// Service 側
List<TInvoice> existingInvoices = tInvoiceRepository.findByShopNoAndClosingDate(shopNo, closingDate);
```

### M-N6: `bulk-payment-date` が `findAllById` で巨大 IN 句を発行しうる (チャンク化なし)

- **箇所**: `FinanceController.java:466`、`TInvoiceService.java:114-117`
- **問題**: フロント `selectedIds` は `Set<number>` で UI 側に上限なし。`Array.from(selectedIds)` で渡す `invoiceIds` が 5000 件を超えるとPostgreSQL の `IN (...)` 句が肥大化し (実用上は数万まで動くが latency 増)、`saveAll` も全件 single transaction で処理。
  - admin が「全件選択 → 一括反映」を試した時に DB ロックを長く取る可能性。
- **影響**: 大量行投入時のロック / レイテンシ。
- **修正案**: バッチサイズ制限 (例: 2000 件) を `BulkPaymentDateRequest` に `@Size(max = 2000)` で付与し、フロントもページング前提で UX を作る。

```java
@Data
public class BulkPaymentDateRequest {
    @NotNull @NotEmpty
    @Size(max = 2000, message = "一括反映は2000件以下にしてください")
    private List<Integer> invoiceIds;
    @NotNull private LocalDate paymentDate;
}
```

---

## Minor 指摘 (新規)

### m-N1: `TInvoiceService#getInvoiceById` が `null` を返す (Optional を返さない)

- **箇所**: `TInvoiceService.java:110-112`
- **問題**: 他メソッド (`findByShopNoAndPartnerCodeAndClosingDate`) は Optional を返すのに、`getInvoiceById` だけ `orElse(null)`。Controller (`FinanceController.java:438-441`) でも `if (invoice == null)` 判定。プロジェクト規約 (`getReferenceById() / findById()`) と整合しない。
- **修正案**: `Optional<TInvoice> findById(Integer)` に統一し、Controller 側で `orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))` 等。

### m-N2: `InvoiceImportService#existingMap` の merge function `(a, b) -> a` がサイレント

- **箇所**: `InvoiceImportService.java:145-146`
- **問題**:

```java
Map<String, TInvoice> existingMap = existingInvoices.stream()
        .collect(Collectors.toMap(TInvoice::getPartnerCode, Function.identity(), (a, b) -> a));
```

ユニーク制約 `(partner_code, closing_date, shop_no)` が DB レベルにあれば衝突しないはずだが、設計 C-1 で DDL 欠落のためユニーク制約も保証なし。`(a, b) -> a` で先勝ちにしているため、もし DB に重複データがあった場合は **2 件目が黙殺**される。WARN ログを残すべき。
- **修正案**:

```java
.collect(Collectors.toMap(TInvoice::getPartnerCode, Function.identity(), (a, b) -> {
    log.warn("DB に partnerCode={} の重複あり (invoiceId={} と {} が同じ closingDate/shop)。先勝ちで処理",
        a.getPartnerCode(), a.getInvoiceId(), b.getInvoiceId());
    return a;
}));
```

### m-N3: `InvoiceImportResult.errors` が常に `List.of()` (空イミュータブル)

- **箇所**: `InvoiceImportService.java:185`、`InvoiceImportDialog.tsx:19` (`errors: string[]`)
- **問題**: 設計 C-3 でも触れられているが、フィールドだけ存在して常に空。フロント側も `errors` を表示しない。デッドフィールド。設計 C-3 を実装するまでの暫定としても、現状はミスリーディング。
- **修正案**: 設計 C-3 実装まで暫定で `result.errors` をフロント側で表示するか、フィールドを削除。

### m-N4: `InvoiceImportService` のフィールドアノテーション順 (`@Transactional` がメソッドレベルのみ)

- **箇所**: `InvoiceImportService.java:40` (メソッドだけに `@Transactional`)
- **問題**: 設計レビュー m-7 が `TInvoiceService` の readOnly 不足を指摘済だが、`InvoiceImportService` も write 専用 method 1 つしかないのにクラスレベルでなくメソッドレベル付与。OK だが将来 read メソッドを追加した時に readOnly を入れ忘れる温床。一貫性のため `@Transactional(readOnly = true)` をクラスレベル + 書込みメソッドのみ override に統一推奨。

### m-N5: `InvoiceVerifier#findQuarterlyInvoice` の四半期判定で年跨ぎが部分的にしか考慮されていない

- **箇所**: `InvoiceVerifier.java:271-286`
- **問題**: 設計レビュー M-4 / M-5 で論点はカバーされているが、コード上の細部:
  - `getSpecialPartnerClosingDates` の前月計算 (`previousMonth = month - 1`) は `month==1` で `previousMonth=12, previousYear--` を考慮しているが、`previousMonth==11` (= 当月 12 月の場合) も case に入る。当月 12 月 → 前月 11 月 (四半期月) → `年/11/末` を生成する。これは正しい。
  - しかし、ループ内で `tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, closingDateStr)` を都度 `Optional<TInvoice>` で取得 → `combined` に `add` するため、closingDates.size() = 2 なら 2 クエリ発行。N+1 ではないが、partner_code に対するインデックスに依存するため、件数は少ないが統計的にはピンポイント検索が増える。`findByShopNoAndPartnerCodeAndClosingDateIn(...)` を Repository に追加して 1 クエリにできる。
- **修正案**: Repository に `IN` クエリを追加。

```java
List<TInvoice> findByShopNoAndPartnerCodeAndClosingDateIn(
    Integer shopNo, String partnerCode, Collection<String> closingDates);
```

### m-N6: `InvoiceImportDialog` がエラー時に `error.message` をそのまま toast 表示する (XSS / 過度な情報露出はないが UX 不親切)

- **箇所**: `InvoiceImportDialog.tsx:43-45`
- **問題**: `api-client` 経由のエラーメッセージは `Map.of("message", e.getMessage())` でサーバから返されるため、Stack trace 等は出ないが、`IllegalArgumentException` の message に「Row2のA列が空です。締日を解析できません」のような技術寄り日本語が直出。経理担当向けに「ファイルの2行目が空です。Excel の Row2 に締日が記載されているか確認してください」など補足したい。
- **修正案**: トースト表示を `message` ベースのテーブル lookup に。

### m-N7: `PartnerGroupDialog` の保存ミューテーション `body` が型 `unknown` で型安全性が薄い

- **箇所**: `PartnerGroupDialog.tsx:53-68`
- **問題**: `mutationFn` の `body` を `{ groupName, shopNo, partnerCodes }` で渡すが、リクエスト型 (`PartnerGroupRequest` ミラー) が定義されていない。`PartnerGroup` (response 型) で兼用。フロント / バックエンド DTO の対応関係が暗黙化。
- **修正案**: `types/partner-group.ts` に `PartnerGroupRequest` を切り出し、`saveMutation` の引数型を厳密化。

```ts
interface PartnerGroupRequest {
  groupName: string
  shopNo: number
  partnerCodes: string[]
}
```

---

## 設計レビューと重複していない新規発見の要約

| ID | Severity | 一行サマリ |
|----|----------|----------|
| C-N1 | Critical | `useMemo(columns, [])` の stale closure でチェックボックスが UI 反映されない |
| C-N2 | Critical | 入金日クリア時 `null` 送信 → `@NotNull` 違反で必ず 400、サイレント失敗 |
| C-N3 | Critical | 取込エンドポイント `/invoices/import` の `shopNo` 認可漏れ (IDOR) |
| C-N4 | Critical | Excel 内重複 `partnerCode` で existing が二重 setter 適用 + メトリクス破綻 |
| M-N1 | Major | `bulk-payment-date` レスポンスに not-found / forbidden ID を含めない |
| M-N2 | Major | partner-group の partnerCodes 正規化・重複除去なし |
| M-N3 | Major | `TInvoiceService` field-level `new TInvoiceSpecification()` で DI 規約違反 |
| M-N4 | Major | `getCellStringValue` FORMULA 分岐の NUMERIC fallback で `String.valueOf(double)` ハマり |
| M-N5 | Major | `InvoiceImportService` が無名 Specification で existing 取得 (Repository 規約違反) |
| M-N6 | Major | `bulk-payment-date` のチャンクサイズ制限なし (DB 圧迫リスク) |
| m-N1 | Minor | `getInvoiceById` のみ `Optional` でなく `null` を返す |
| m-N2 | Minor | `existingMap` の merge function がサイレント先勝ち |
| m-N3 | Minor | `InvoiceImportResult.errors` がデッドフィールド (常に `List.of()`) |
| m-N4 | Minor | `InvoiceImportService` の `@Transactional` クラスレベル化推奨 |
| m-N5 | Minor | `findQuarterlyInvoice` の閉じ日複数取得を IN クエリ 1 発に集約可 |
| m-N6 | Minor | 取込エラーの toast が技術寄り日本語直出 |
| m-N7 | Minor | フロントの `PartnerGroupRequest` 型未定義 (response 型と兼用) |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | コメント |
|------|------|---------|
| N+1 検出 | OK (新規範囲では) | `findQuarterlyInvoice` の N=2 は許容、設計 M-1 (EAGER) は別途 |
| `@Transactional` 適切性 | 改善余地 (m-N4) | `InvoiceImportService` クラスレベル化推奨。`@Transactional` 範囲は OK |
| LazyInitializationException | OK | EAGER 主体 (設計 M-1) のため逆に発生しない |
| DI 方式 (constructor) | NG (M-N3) | `TInvoiceService` が field-level `new` |
| Entity 露出 | OK | DTO factory 経由で露出していない |
| SQL Injection (`@Query` ネイティブ) | OK | `escapeLike` で `%`/`_`/`\` をエスケープ済 (`CommonSpecification.java:34`) |
| 例外処理 | NG (C-N4 関連) | 重複 partnerCode 黙殺 (m-N2)、`errors` 空 (m-N3) |
| Migration 安全性 | (設計 C-1 既出) | 再掲しない |

### Next.js / React 観点

| 項目 | 結果 | コメント |
|------|------|---------|
| TypeScript 厳密性 (any, as キャスト) | OK | `any` なし。`as` 限定 |
| Immutable 更新 | OK | `setSelectedIds` で `new Set(prev)` |
| TanStack Query 使用 | OK | `useQuery` / `useMutation` を一貫利用 |
| ローディング/エラー状態 | OK | `LoadingSpinner` / `ErrorMessage` ハンドリング |
| メモリリーク (useEffect cleanup) | OK | `useEffect` は `PartnerGroupDialog` の 1 箇所のみで cleanup 不要 |
| key 設定 | OK | groups.map / shops.map で `key={...}` 設定済 |
| useMemo 依存配列 | NG (C-N1) | `columns` の `useMemo([])` が stale closure |
| XSS | OK | `dangerouslySetInnerHTML` 不使用 |
| console.log 残存 | OK | 該当なし |
| 認可・型安全 | NG (C-N2 / m-N7) | クリア操作 / 型不足 |

### 請求機能固有のコード品質

| 項目 | 結果 | コメント |
|------|------|---------|
| Excel 取込のエッジケース (重複コード) | NG (C-N4) | 同一 partnerCode 複数行で更新二重 |
| Repository 規約 (derived query 優先) | NG (M-N5) | 無名 Specification 直接記述 |
| Optional vs null | NG (m-N1) | `getInvoiceById` のみ `null` 返却 |
| 認可 (取込 / 一括更新) | NG (C-N3) | 取込側に IDOR (設計 C-2 の延長) |
| メトリクス整合 | NG (C-N4) | `totalRows == inserted + updated + skipped` が崩れうる |
| 業務エラーメッセージ品質 | 改善余地 (m-N6) | toast に技術寄り日本語直出 |

---

## 推奨アクション順序

1. **C-N1 (UI 機能停止)**: 一括反映 UI の根幹を壊しているので即修正。`SelectCell` を `checked: boolean` プロップに変更し `useMemo` を `[selectedIds, handlePaymentDateChange]` 依存に。
2. **C-N2 (UI 操作不能)**: `PaymentDateUpdateRequest.@NotNull` 撤去 or フロント側ガード。業務判断必須 (クリア許容/不許容)。
3. **C-N3 (認可)**: 設計レビュー C-2 と同 PR でまとめて取込エンドポイントもガード。
4. **C-N4 (データ整合)**: Phase 1 終了後の dedup を追加。業務に「合算 / 後勝ち」の確認を依頼し設計書 §5.1 へ反映。
5. **M-N1 / M-N2 / M-N5 / M-N6 (透明性 + 規約)**: 設計レビュー C-2 / C-3 修正と同 PR。
6. **M-N3 / M-N4 / m-N1〜m-N7**: コード品質 PR を別建てで。
