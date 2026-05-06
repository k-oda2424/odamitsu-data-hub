# コードレビュー: MF Cashbook Import (Cluster B)

レビュー日: 2026-05-04
ブランチ: `refactor/code-review-fixes`
レビュアー: code-reviewer subagent (Opus)
対象設計レビュー: `claudedocs/design-review-mf-cashbook-import.md` (Critical 1 / Major 6 / Minor 5)

## 前提

設計レビュー指摘 (C-1 / M-1〜M-6 / m-1〜m-5) と重複する内容は本レビューで再掲しない。
本レビューはコード固有 (実装パターン、ライブラリ用法、フロント TS、Migration 詳細など) に絞る。

## サマリー

- 総指摘件数: **Blocker 0 / Critical 2 / Major 6 / Minor 8**
- 承認状態: **Needs Revision**
- 新発見トップ:
  1. (C-impl-1) `csv_content` を `byte[] -> new String(StandardCharsets.UTF_8)` で復元し DB 永続化しているため、**先頭 BOM (EF BB BF) と 19 列ヘッダ + CRLF 行終端が UTF-8 文字列としてラウンドトリップされ、CSV を再ダウンロードする経路を作ったらバイト不一致になる**。設計レビュー M-5 (PII 永続化) とは別軸の実害バグ。
  2. (C-impl-2) `MfClientMappingsPage` (フロント) で `delete` ボタンを admin 限定にしているが、**バックエンドの `update` (PUT) は `shopNo == 0` 強制なのに UI ではボタンが出てしまい、一般ユーザが Pencil → 更新 を押すと 403 エラー**。`MfJournalRulesPage` 側は admin チェック済みで、**Mapping 画面だけ抜けている**。

---

## Blocker

なし。

---

## Critical

### C-impl-1: `csv_content` の文字列ラウンドトリップで BOM/CRLF 情報がロスする
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:135-137`, `:582-587`
- **コード**:
  ```java
  byte[] csvBytes = toCsvBytes(preview.getRows());
  saveHistory(cached, preview, new String(csvBytes, StandardCharsets.UTF_8));
  ```
  ```java
  out.write(0xEF); out.write(0xBB); out.write(0xBF);
  out.writeBytes(body);
  ```
- **問題**:
  - `toCsvBytes` は **UTF-8 BOM 3 byte + 本体 (改行 CRLF)** を返す。これを `new String(csvBytes, StandardCharsets.UTF_8)` に入れると、BOM は U+FEFF 1 文字になり (3 byte → 1 char)、その後 PostgreSQL の `TEXT` カラム (`csv_content`) に格納される。
  - 後に `csv_content.getBytes(StandardCharsets.UTF_8)` で復元しようとしても、U+FEFF が「3 byte の BOM」ではなく「3 byte の UTF-8 シーケンス (EF BB BF)」として戻るため、見た目は同じになるが、**MoneyForward 側の取込パーサが「ファイル先頭 BOM」と「先頭フィールドの zero-width space」を区別する**実装の場合に再アップロードで桁ズレを起こす。
  - 加えて、JPA の `String` フィールド経由で PostgreSQL の `TEXT` に書く場合、内部 collation・改行正規化次第で **CRLF が LF に折りたたまれる**ことがある。`columnDefinition = "TEXT"` 指定 (`TCashbookImportHistory.java:37`) はあるが、Hibernate / JDBC ドライバの中継で改行コードが保証される保証はない。
  - 設計レビュー M-5 では「PII / 認可」として論じられているが、本指摘は**バイナリ精度の喪失バグ**であり、再ダウンロード API を将来追加した瞬間に MF への取込で「列数が違う」エラーが発生する。
- **影響**:
  - 現状 `csv_content` を読む API はないので即時影響はないが、次の機能追加 (履歴詳細画面等) を入れたときに**サイレントに壊れる**。
  - 「ダウンロード時のバイト列」と「永続化された文字列のバイト列復元」が一致しない仕様は監査ログとしても機能しない。
- **修正案**:
  - 案A (推奨): `csv_content` を `byte[]` (`columnDefinition = "BYTEA"` / Java 側 `byte[]`) に変える。永続化バイト列とダウンロードバイト列を完全一致させる。
  - 案B: 設計レビュー M-5 と合わせて `csv_content` を廃止し、`SHA-256` ハッシュ + 件数 + 期間のみ保存。再生成は uploadId キャッシュ or 再アップロードで対応 (M-5 案A と同じ)。
  - 案C (暫定): 文字列保存を残すなら、保存時に Base64 で固定し、復元時に Base64 デコードでバイト一致を保証。

### C-impl-2: フロント `MfClientMappingsPage` で **編集ボタンが一般ユーザにも表示される**が PUT は admin 限定
- **箇所**:
  - フロント: `frontend/components/pages/finance/mf-client-mappings.tsx:135-148`
    ```tsx
    <Button size="sm" variant="ghost" onClick={() => openEdit(m)}>
      <Pencil className="h-3.5 w-3.5" />
    </Button>
    {isAdmin && (
      <Button size="sm" variant="ghost" onClick={() => setDeleteTarget(m)}>
        <Trash2 className="h-3.5 w-3.5 text-red-500" />
      </Button>
    )}
    ```
  - バックエンド: `MfClientMappingController.java:33-37` `@PreAuthorize("authentication.principal.shopNo == 0")` 付き PUT
- **問題**:
  - 一般ユーザにも Pencil ボタンが表示され、ダイアログを開いて「更新」を押すと**バックエンドが 403 で弾く**。toast に `403 Forbidden` 等のメッセージが出てユーザは何が悪いか分からない (UX 不良)。
  - 同じファイルの `MfJournalRulesPage` (`mf-journal-rules.tsx:150-163`) では `{isAdmin && (<Pencil>...<Trash>)}` で admin gating されている。**Cluster B 内で UX 一貫性が崩れている**。
  - 設計レビュー C-1 で「create だけ一般ユーザ可、update/delete は admin」と認可仕様が議論されているため、フロント側の表示ガードもこの方針に揃える必要がある。
- **影響**: 中規模。一般ユーザが「編集できそうに見えて押すとエラー」体験。
- **修正案**:
  ```tsx
  {isAdmin && (
    <Button size="sm" variant="ghost" onClick={() => openEdit(m)}>
      <Pencil className="h-3.5 w-3.5" />
    </Button>
  )}
  {isAdmin && (
    <Button size="sm" variant="ghost" onClick={() => setDeleteTarget(m)}>
      <Trash2 className="h-3.5 w-3.5 text-red-500" />
    </Button>
  )}
  ```
  もしくは設計レビュー C-1 で「update/delete も一般ユーザ可」に揃えるなら、バックエンドの `@PreAuthorize` を外す。**いずれにせよ表示と認可を一致させる**。

---

## Major

### M-impl-1: `extractPeriodLabel` が任意シートの上位 11 行 × 5 列を全走査するが、シート選別が `name.contains("Sheet")` ヒューリスティック
- **箇所**: `CashBookConvertService.java:100-119`
- **コード**:
  ```java
  if ("Sheet2".equals(name) || (name != null && name.contains("Sheet"))) {
      Sheet s = workbook.getSheetAt(i);
      for (int r = 0; r <= Math.min(s.getLastRowNum(), 10); r++) {
          ...
          if (v != null && v.contains("～")) {
              return v.strip().replaceAll("\\s+", " ");
          }
      }
  }
  ```
- **問題**:
  - `name.contains("Sheet")` は日本語シート名 (`記入`, `現金出納帳`, `MF` 含むシート) には**マッチしない**ので、Excel デフォルトの `Sheet2` 名を残しているケースのみで動く。`Sheet2` がリネームされた瞬間 (例: `期間` 等) に period 抽出失敗 → `saveHistory` 内で `period = cached.getFileName()` にフォールバック。
  - そのフォールバックでも `t_cashbook_import_history.period_label UNIQUE` 制約により**同じファイル名の 2 回目アップロードが UPDATE 扱い**になり、過去履歴が破壊される。
  - また「`～`」(全角チルダ U+FF5E) のみ検査しているが、入力 Excel に「`〜`」(波ダッシュ U+301C) が混じった場合に periodLabel 抽出失敗。Python 版がどちらを使っているか脚注で示すべき。
- **影響**: シート名/記号変動でサイレントに履歴破壊。
- **修正案**:
  - 全シート対象に `(name != null && !name.contains("MF") && !name.equals(selectedSheet))` で選別するか、明示的に「期間ラベルが取れなかった場合は履歴を**保存しない**」 (`period == null` で skip) ガードを追加。
  - 全角チルダと波ダッシュ両方 (`v.contains("～") || v.contains("〜")`) を許容。

### M-impl-2: `Scheduled fixedDelay = 5 * 60 * 1000` の `@Scheduled` が `BatchApplication` プロファイルでは無効化されない
- **箇所**: `CashBookConvertService.java:173-177`, `WebApplication.java:9` (`@EnableScheduling`)
- **問題**:
  - `WebApplication` のみ `@EnableScheduling` 付き。`BatchApplication` (バッチプロファイル) では `@Scheduled` は走らないので、現状 batch 起動時には cleanExpired は no-op。
  - しかし `CashBookConvertService` は `@Service` で常に Bean 化される。 web/batch のどちらでも DI が走るため、batch 側で本サービスがロードされても問題は出ないが、cache (`ConcurrentHashMap`) は batch 側で**永久に解放されない**(誰も put しないので空ではあるが、メモリは確保される)。
  - また `cleanExpired` 自体は同期化されておらず、`enforceCacheLimit` (`synchronized`) と並行アクセス時に `removeIf` のラムダが TOCTOU ウィンドウを開く。`ConcurrentHashMap.entrySet().removeIf` は弱整合だが、`enforceCacheLimit` 側で `synchronized(this)` を取っているのに `cleanExpired` 側は外れているため、ロック保護の対称性が壊れている。
- **影響**: 同時 upload + cleanup タイミングで稀に「直前 put したエントリが直後 evict される」可能性。
- **修正案**:
  - `cleanExpired` も `synchronized` を付けるか、`enforceCacheLimit` の `synchronized` を外して `ConcurrentHashMap` の弱整合に任せる (どちらかに統一)。
  - 設計レビュー m-5 と合わせて、`@ConditionalOnProperty(name="spring.profiles.active", havingValue="web", matchIfMissing=true)` でバッチ環境では Bean 自体ロードしない構成も検討。

### M-impl-3: `selectSheet` のフォールバックで「MF を含まない先頭シート」を返してしまう
- **箇所**: `CashBookConvertService.java:222-243`
- **コード**:
  ```java
  // フォールバック: 先頭シート（MFを除く）
  for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
      String name = workbook.getSheetName(i);
      if (name != null && !name.contains("MF")) return workbook.getSheetAt(i);
  }
  ```
- **問題**:
  - `記入` / `現金出納帳` のいずれでもないシート (例: `集計`, `Sheet2`, `データ`) を**フォールバックで cashbook データとして解釈**してしまう。先頭シートは Excel デフォルトで `Sheet1` のことが多く、空シートでも `parseSheet` が走る。
  - 結果、空シートでは `parsed.size() == 0` で 0 件 CSV を出すが、"集計" シートのような別構造のシートだと**意図しない誤データ**が CSV 化されかねない。
  - 設計書では `selectSheet` の優先順位は「記入完全一致 > 記入部分一致 > 現金出納帳部分一致」までで、フォールバックは記載がない。
- **影響**: ユーザの想定外 Excel 形式で "通ってしまう" 危険。
- **修正案**: フォールバックを撤去し、見つからなければ `null` を返して呼び出し側で `IllegalArgumentException` (現状の動作) に倒す。

### M-impl-4: `parseHalf` 内で年更新を `continue` した直後の行が「年更新行＋データが同行に含まれる Excel」だとデータ落ちする
- **箇所**: `CashBookConvertService.java:329-345`
- **コード**:
  ```java
  if (monthVal != null && monthVal >= 2000) {
      year = monthVal;
      prevMonth = null;
      prevDay = null;
      continue;  // 年セルが入っていた行をまるごとスキップ
  }
  ```
- **問題**:
  - A 列に `2026` が入っている行に**摘要 C/D・収支金額が同居しているケース**は Python 版で意図的に skip しているとのことだが、ゴールデンマスタテストにそのケースの fixture が無い (`backend/src/test/resources/cashbook/` 確認)。
  - 実 Excel で「年更新行に取引が入る」ことが起きた場合、サイレントに 1 行欠落。設計書 §Excel解析ロジック にも明記されていない。
- **影響**: 年更新行に取引データが混じった場合の欠落。実際にはレアと思われるが、検出機構なし。
- **修正案**:
  - `continue` する前に「同じ行に descC/descD or income/payment が入っていないか」を assertion として log.warn する。
  - 設計書 §Excel解析ロジック step 5 に「A列が 2000 以上 = 年更新行は摘要・金額があってもスキップ」と明記。

### M-impl-5: `findRule` の優先度計算に**マジックナンバー2倍**が出現するが理由がコメントだけ
- **箇所**: `CashBookConvertService.java:520-527`
- **コード**:
  ```java
  // 優先度計算: priority が小さいほど先。同priorityならキーワード一致ルールを優先
  // (priority*2 の空間に kwBonus 0/1 を差し込む → priority=5+kw(0)=10 < priority=5+nokw(1)=11 < priority=10)
  .min(Comparator.comparingInt(r -> {
      String kw = r.getDescriptionDKeyword();
      int kwBonus = (kw == null || kw.isEmpty()) ? 1 : 0;
      return r.getPriority() * 2 + kwBonus;
  }))
  ```
- **問題**:
  - `priority * 2` という計算は、`priority` が `Integer.MAX_VALUE / 2` 以上だとオーバーフロー。 schema (`m_mf_journal_rule.priority INTEGER`) は値域制限なし。
  - もっと素直に `Comparator.comparingInt(MMfJournalRule::getPriority).thenComparing(r -> r.getDescriptionDKeyword() == null || r.getDescriptionDKeyword().isEmpty() ? 1 : 0)` で書ける。可読性低下とリスクをトレードオフする意味がない。
  - また、設計書 §テスト記載の「priority 同値時のキーワード優先テスト」が `CashBookConvertServiceGoldenMasterTest` には無い (設計レビュー m-3 が指摘済みなので重複は避けるが、本ロジックは特に testability が低い)。
- **影響**: priority に巨大値を入れると順序が崩れる。実運用では出ないがコードの脆弱性。
- **修正案**:
  ```java
  .min(Comparator.comparingInt(MMfJournalRule::getPriority)
      .thenComparingInt(r -> {
          String kw = r.getDescriptionDKeyword();
          return (kw == null || kw.isEmpty()) ? 1 : 0;
      }))
  ```

### M-impl-6: `addMappingMutation.onSuccess` で**他コンポーネントの query を invalidate しているが Cashbook 画面の rePreview は別 mutate で実行**(プレビュー再取得の semantics が二重)
- **箇所**: `frontend/components/pages/finance/cashbook-import.tsx:52-64`
- **コード**:
  ```tsx
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['mf-client-mappings'] })
    toast.success('マッピングを追加しました')
    setMappingDialog(null)
    setMfName('')
    if (preview) rePreviewMutation.mutate(preview.uploadId)
  },
  ```
- **問題**:
  - `invalidateQueries(['mf-client-mappings'])` は Cashbook 画面では `useQuery(['mf-client-mappings'])` が**そもそも呼ばれていない**ので意味がない (mapping list は表示していない)。死コード。
  - `rePreviewMutation.mutate(preview.uploadId)` で再プレビューを呼んでいるが、`onSuccess` で `setPreview(r)` するだけなので `previewMutation` の結果と排他関係になっておらず、新しい file を選んで preview を出した直後にユーザがマッピング追加を押した場合に**古い uploadId を打ち**て上書きされる race condition がある (state の `preview` が 2 つの非同期で書かれる)。
  - `preview.uploadId` の TTL は backend で 30 分なので実害は少ないが、UX として「マッピング追加 → 古いアップロードに対して再プレビュー」の事故が起きうる。
- **影響**: 中規模 (実害は稀)。
- **修正案**:
  - `invalidateQueries(['mf-client-mappings'])` を削除 (現画面では query 不要)。
  - `rePreview` 呼び出しは `previewMutation.isPending` チェックを入れる。

---

## Minor

### m-impl-1: `validateFile` の MIME チェックが Windows で `application/octet-stream` を素通しする抜け道
- **箇所**: `CashBookConvertService.java:181-198`
- **コード**:
  ```java
  if (contentType != null && !XLSX_CONTENT_TYPE.equals(contentType) && !contentType.isBlank()) {
      if (!"application/octet-stream".equals(contentType)) {
          throw new IllegalArgumentException("xlsxファイルのみ対応しています");
      }
  }
  ```
- **問題**: `application/octet-stream` を許容しているため、拡張子 `.xlsx` であれば任意バイナリが通る。XSSFWorkbook 側で例外になるので深刻ではないが、上位での MIME チェックが事実上**拡張子チェックのみ**なのに「MIME 検証している風」のコードになっている。
- **修正案**: `application/octet-stream` 許可コメントを追加 (Windows 互換性) し、その場合のみ `XSSFWorkbook` 例外を `400` に翻訳する。

### m-impl-2: `cleanExpired` の `removeIf` 述語が boxing と λ allocation を毎回行う
- **箇所**: `CashBookConvertService.java:173-177`
- **問題**: 5 分ごとの軽微な処理だが、cache が 100 件未満なら短絡判定でよい (`if (cache.isEmpty()) return;`)。
- **修正案**: 軽微な早期 return を追加。

### m-impl-3: `MfJournalRule` Entity が Lombok `@Data` で `equals/hashCode` を全フィールド対象にしている
- **箇所**: `MMfJournalRule.java:11`, `MMfClientMapping.java:11`, `TCashbookImportHistory.java:8`
- **問題**: Hibernate Entity に `@Data` (= `@EqualsAndHashCode` 全フィールド) は**proxy/lazy load 時に SELECT N+1 を引き起こす**典型アンチパターン。`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` への分解 + `@EqualsAndHashCode(of = "id")` 推奨。
- **影響**: `findAll` 等で List 操作したときの hashCode 計算で全フィールドアクセス。MF Cashbook はマスタ件数小なので実害ほぼなし。
- **修正案**: `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` + `@EqualsAndHashCode.Include` を `id` に付与。

### m-impl-4: `Field` コンポーネントが `mf-journal-rules.tsx` 内のローカル定義 (再利用できない)
- **箇所**: `frontend/components/pages/finance/mf-journal-rules.tsx:229-236`
- **問題**: 似た 2 列フォーム (Label + child) は他画面でも使う可能性が高い。ローカル `function Field` は再利用阻害。
- **修正案**: `frontend/components/features/common/FormField.tsx` 等に切り出し。

### m-impl-5: `mf-cashbook.ts` に `TAX_RESOLVERS` と `as const` で定義しているが `MfJournalRuleRequest.debitTaxResolver` は `string` 型のまま
- **箇所**: `frontend/types/mf-cashbook.ts:73-101`
- **問題**: `as const` で literal union 型に絞れるのに `debitTaxResolver: string` で型情報を捨てている。これにより `<Select>` の `onValueChange` から任意 `string` が入ってしまう。
- **修正案**:
  ```ts
  export type TaxResolver = typeof TAX_RESOLVERS[number]
  // ...
  debitTaxResolver: TaxResolver
  creditTaxResolver: TaxResolver
  ```

### m-impl-6: `cashbook-import.tsx` `downloadCsv` で URL.createObjectURL のリーク懸念
- **箇所**: `frontend/components/pages/finance/cashbook-import.tsx:66-83`
- **問題**: `try { ... URL.revokeObjectURL(url) } catch { ... }` の構造で、`URL.revokeObjectURL` 前に exception (`a.click()` 失敗等) が起きれば URL がリークする。
- **修正案**: `try/finally` で `revokeObjectURL` を保証。

### m-impl-7: `parseSheet` の右半分検出ロジックが緩く、誤検出リスク
- **箇所**: `CashBookConvertService.java:262-284`
- **問題**: 列15-17 のヘッダ文字列に「年」「摘」「収入」のいずれかが含まれれば `hasRightHalf = true` にしている。Excel で `備考` 等の列が右側にある場合に誤検出する可能性。fixture では拾えていないだけ。
- **修正案**: `年` AND `月` の同時存在、または `摘要` 完全一致など、より厳格なパターンマッチに変更。

### m-impl-8: `csvEscape` が `\n` `\r` を含む摘要を quoted にするが、CSV 出力全体で改行 `\r\n` を行終端として使っているため**摘要内 `\r\n` で MF 取込が崩れる**可能性
- **箇所**: `CashBookConvertService.java:589-595`, `:556`, `:580`
- **問題**: 摘要 (`summaryTemplate.replace("{d}", ...)`) に `\r\n` が含まれる場合、quoted フィールド内に CRLF が入る。RFC 4180 では合法だが、MoneyForward 側パーサが「フィールド内 CRLF」を許容するか未検証。Python 版 (`pandas.to_csv`) は `\r\n` を `\\r\\n` にエスケープせず生で出すため**Python と意味等価**ではあるが、リスク認識は必要。
- **修正案**: 摘要内の `\r\n` を ` ` に置換するか、設計書に「`{d}` 内改行は許容、MF 側未検証」と注記。

---

## 設計レビューと重複しない新発見の要点

| ID | 区分 | 内容 | Severity |
|---|---|---|---|
| C-impl-1 | データ整合性 | `csv_content` を String 経由で永続化 → BOM/CRLF ロスの可能性 | Critical |
| C-impl-2 | UX/認可 | `MfClientMappingsPage` の編集ボタンが一般ユーザにも見える | Critical |
| M-impl-1 | パース | `extractPeriodLabel` のシート選別が緩い + `〜`未対応 | Major |
| M-impl-2 | 並行性 | `cleanExpired` と `enforceCacheLimit` のロック非対称 | Major |
| M-impl-3 | パース | `selectSheet` フォールバックが意図しないシートを通す | Major |
| M-impl-4 | パース | 年更新行のデータが同居した場合に欠落、検出なし | Major |
| M-impl-5 | 可読性 | `priority * 2 + kwBonus` のマジックナンバー、Comparator 分解で済む | Major |
| M-impl-6 | フロント | mapping 追加 → rePreview の race / 死 invalidate | Major |
| m-impl-1〜8 | 各所 | (本文参照) | Minor |

---

## 観点別チェック

### Spring Boot
- [x] Controller 薄い、Service 委譲済み
- [x] Constructor injection (`@RequiredArgsConstructor`)
- [ ] `@Transactional` スコープ — 設計レビュー指摘済み (history save の race window)
- [x] DTO 変換 (`Response.from(entity)`)
- [ ] Bean Validation — 設計レビュー指摘済み (m-1, enum 検証なし)
- [x] N+1 なし
- [x] Migration idempotency — `IF NOT EXISTS` 付き、seed のみ INSERT 重複リスク

### Next.js / React
- [x] `'use client'` 明示
- [x] TanStack Query で server state 管理
- [ ] **admin gating の不統一** (C-impl-2)
- [ ] union 型を活かしていない (m-impl-5)
- [ ] FormField の再利用性 (m-impl-4)

### MF Cashbook 固有
- [ ] CSV エンコーディング — UTF-8 BOM + CRLF は実装通り (設計書側の LF 記述が誤り、設計レビュー M-1 で指摘済み)
- [ ] 税リゾルバ網羅性 — `PURCHASE_AUTO_WIDE` 設計書未記載 (設計レビュー M-3 で指摘済み)
- [ ] amountSource ガード — INCOME/PAYMENT 両ルール共存テスト無し (設計レビュー M-6 で指摘済み)
- [ ] ファイル MIME 検証 — `application/octet-stream` 許容で実質拡張子のみ (m-impl-1)
- [ ] サイズ検証 — 20MB 設定済み、ストリーミング非対応 (設計レビュー M-4 で指摘済み)
- [ ] 履歴重複チェック — `period_label UNIQUE` に依存、period 抽出失敗時の fallback で誤上書きリスク (M-impl-1)

### コード品質 (Global Rules)
- [x] No hardcoded secrets
- [x] Parameterized queries (JPA)
- [ ] Immutability — `@Data` 多用 (m-impl-3、設計レビュー m-4 と関連)
- [x] Small file — 707 行 (CLAUDE.md 上限 800 内)
- [x] Small function — `parseHalf` 101 行のみ大きめ

---

## 推奨アクション

1. **C-impl-1 最優先**: `csv_content` を `byte[]` に変更、または設計レビュー M-5 と合わせて廃止+ハッシュ化。merge 前の判断必須。
2. **C-impl-2**: フロント `MfClientMappingsPage:135` の Pencil ボタンを `{isAdmin && (...)}` でガード。 1 行修正。
3. **M-impl-1, M-impl-3**: `extractPeriodLabel` と `selectSheet` のフォールバック撤去 / 厳格化。サイレント失敗を例外に倒す。
4. **M-impl-2**: `cleanExpired` のロック整合性を `synchronized` 統一 or 全廃止のいずれかに統一。
5. **M-impl-4**: 年更新行データ同居の検出 log.warn を追加 (将来発生時に気付ける)。
6. **M-impl-5**: `Comparator` を分解で書き直して overflow 余地を消す。
7. **M-impl-6**: mapping 追加成功時の死 `invalidateQueries` を削除、`rePreview` に pending ガード。
8. **Minor 群**: `@EqualsAndHashCode(of="id")` 化、`as const` 型を活かす、`URL.revokeObjectURL` を finally に移動、等。

---

## 確認したファイル

- `backend/src/main/java/jp/co/oda32/api/finance/CashBookController.java`
- `backend/src/main/java/jp/co/oda32/api/finance/MfClientMappingController.java`
- `backend/src/main/java/jp/co/oda32/api/finance/MfJournalRuleController.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MfTaxResolver.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MMfClientMappingService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MMfJournalRuleService.java`
- `backend/src/main/java/jp/co/oda32/domain/model/finance/MMfJournalRule.java`
- `backend/src/main/java/jp/co/oda32/domain/model/finance/MMfClientMapping.java`
- `backend/src/main/java/jp/co/oda32/domain/model/finance/TCashbookImportHistory.java`
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/MMfClientMappingRepository.java`
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/MMfJournalRuleRepository.java`
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/TCashbookImportHistoryRepository.java`
- `backend/src/main/java/jp/co/oda32/dto/finance/cashbook/*.java`
- `backend/src/main/resources/db/migration/V008__create_mf_cashbook_tables.sql`
- `backend/src/main/java/jp/co/oda32/config/SecurityConfig.java` (認可ガード確認)
- `backend/src/main/java/jp/co/oda32/WebApplication.java` / `BatchApplication.java` (`@EnableScheduling` 確認)
- `frontend/components/pages/finance/cashbook-import.tsx`
- `frontend/components/pages/finance/mf-client-mappings.tsx`
- `frontend/components/pages/finance/mf-journal-rules.tsx`
- `frontend/types/mf-cashbook.ts`
- `frontend/lib/api-client.ts` (downloadPost / postForm 経路確認)
