# 買掛金・売掛金 累積残高管理 + MF 残高突合 設計書（確定版）

作成日: 2026-04-21
確定: 2026-04-22（4 視点レビュー反映済み）
対象ブランチ: `feat/ap-ar-cumulative-balance`（Phase A で新設予定）
関連設計書: `design-mf-integration-status.md`（本設計は同 §1 の Phase 2「試算表突合」の詳細化にあたる。二重管理を避けるため、試算表連携の詳細は本設計書をマスタとする）

---

## 1. 背景と問題

### 業務上の実例（2026-04-21 ヒアリング）

- **カミ商事**: 12月に仕入した商品に対する値引が 1月に発生
- 1月仕入額が小さかったため、1月の買掛金が **-298,097円（負残）** になった
- 「仕入値引」は「仕入」科目に集約されているため、MF には値引込みのネット金額で連携済み

### 現システムの限界

`t_accounts_payable_summary` / `t_accounts_receivable_summary` は **月次スナップショット方式**で、月をまたいだ累積残高を持たない：

| 月 | カミ商事 買掛 change | 現在の扱い |
|---|---|---|
| 2025-12-20 | +X（仕入）| 独立月データ |
| 2026-01-20 | -298,097（値引＞仕入）| 独立月データ（負残）|
| 2026-02-20 | +Y（通常仕入）| 独立月データ |

一方 MF 側は **取引元帳 (journals)** で累積残高を持つため：
- MF 買掛金残高 at 2/20 = すべての journal を累積した結果
- 自社 2/20 summary = その月単独の change 値

→ 月末残高で突合しようとすると、値引による負残の繰越分だけ乖離する。

### 2026-03-20 の突合結果と未解明部分

MF 仕訳突合で PURCHASE 差分 **+¥2,973,089** が発生。現時点で判明しているのは：
- **¥298,097 分 = カミ商事 1月負残の繰越**（本設計の Phase A が狙う解消対象）

**残り ¥2,675,000 分は未解明**。候補：
1. 他の仕入先にも同様の値引繰越がある
2. 3/20 付けの買掛計上と支払の日付ズレ（別設計「支払予定日」で一部対応済み）
3. MF 側の手動入力仕訳（CSV 経由でない journal）
4. 補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) の計上タイミングズレ
5. supplierNo=303 等の除外ルール差
6. **（Phase 0 追加）仮払金の継続的負残**: 1/20 -514,498 → 2/20 -231,498 → 3/20 -1,647,998。従業員立替や相殺未処理の可能性
7. **（Phase 0 追加）未払金の月跨ぎ減少**: 2/20 1,325,472 → 3/20 1,085,656（前月比 -239,816）。本体は買掛金と別 account だが、計上区分差の可能性
8. **（Phase 0 追加）消費税計上タイミング差**: 仮受消費税 18,527,998 / 仮払消費税 18,134,357（差額 393,641）。自社 DB は税抜/税込 change を別管理、MF は仮受/仮払仕訳で保持 — 期末前の差額が乖離に寄与する可能性

**⚠️ Phase A 完了基準は「累積残機能の動作確認」に限定**。MF 完全一致は Phase B 以降で段階的に追求。

### 2026-04-22 Phase A 実装完了後の実測結果（真因の特定）

Phase A 実装完了後、MfJournalReconcileService での再突合で以下が判明:

| 月 | 仕入仕訳 diff (Phase A 前) | 仕入仕訳 diff (Phase A 後) | 改善 |
|---|---|---|---|
| 2026-03-20 | +¥2,973,089 | **+¥10** | ほぼ完全一致（丸め誤差のみ） |
| 2026-02-20 | (+¥2,973,089 相当) | +¥2,963,384 | -¥9,705 (竹の子の里 -63,000 相当) |
| 2026-01-20 | — | +¥1,528,228 | 件数差 3 件 |

**真因の特定**: `AccountsPayableSummaryCalculator.java:167` に
`baseAmount > 0` フィルタがあり、値引超過で subtotal 合計が負になる supplier×taxRate 行
（カミ商事 -¥298,096 / 三興化学 -¥13,750 / 竹の子の里 -¥63,000 他）を summary 生成から
除外していた。Phase A-11 で `!= 0` に緩和した結果、これらの負 change が自社集計に入り、
MfJournalReconcileService の当月合計が MF と一致するようになった。

**カミ商事解釈の訂正**: memory 初出の「1/20 買掛金 -¥298,097 負残」は誤りで、正しくは
「1/20 change が -¥298,096（税抜→税込再計算で 1 円丸め）」。開始残高 +¥18,540,038 があった
ため closing は +¥18,241,942 で負残にはなっていない。**実在する月跨ぎ負残は
「竹の子の里（株）物販 (030302, 税率0%)」**（2/20 close=-63,000 → 3/20 open=-63,000 → close=-64,400）。

**2/20 残差 +¥2,963,384 の候補** (Phase B で追究):
- 件数差 5 件（MF 42 件 vs 自社 37 件）→ MF 側にあって自社に無い仕訳 5 件
- 候補: 手動入力仕訳、補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) が MF 側のみ、仕入先の除外差
- 仕入先別 journals 累積 (Phase B の `/journals` fallback) で内訳判定可能

**買掛支払 diff の解釈**:
- 2/20 mf=¥7,976,458 vs self=¥255,883 (+¥7,720,575)、3/20 mf=¥0 vs self=¥588,721 (-¥588,721)
- MF の買掛金取崩仕訳タイミング（支払日）と自社の支払予定日ベースのズレ
- `mf_transfer_date` (V023) との付き合わせで Phase B 初期に解消可能性

### Phase 0 実測結果（2026-04-22）

`GET /debug/trial-balance-raw?end_date=YYYY-MM-DD` を 1/20, 2/20, 3/20 で実行して確定した事実：

| 確認項目 | 結果 |
|---|---|
| MF API query 仕様 | **`end_date=YYYY-MM-DD` のみで十分**。`start_date` は opening_balance の起点を変えるが closing は不変 |
| `opening_balance`（API） | **期首残高固定**（全月 14,705,639）。使わない |
| `closing_balance`（API） | **end_date 時点の累積残**。これが突合対象 |
| 買掛金 closing 推移 | 1/20: 19,213,461 / 2/20: 24,234,845 / 3/20: 41,085,601（正値で推移）|
| MF の負残自動振替 | **発生していない**。前払金 account 自体が存在せず、「前払費用」は 35.4M → 36.3M → 36.3M で安定、仕入とは無関係 |
| sub_account 粒度 | **含まれない**（全 account が `rows: null` の leaf）→ 仕入先別の残高突合は Phase B で `/journals` 累積 fallback 必須 |
| 選択肢 D（自動前払振替） | **不要**（実測で否定） |
| **closing 定義** | **累積仕入ベース: `closing = opening + effectiveChange`**（accounts_payable_summary 側の定義）で確定 |

trial_balance_bs レスポンス shape:
```json
{
  "columns": ["opening_balance","debit_amount","credit_amount","closing_balance","ratio"],
  "end_date": "2026-03-20",
  "report_type": "trial_balance_bs",
  "rows": [ /* ネスト構造、type=account で leaf */ ]
}
```

---

## 2. 設計方針

### 選択肢評価（2026-04-21 確定）

| 案 | 概要 | 採用 |
|---|---|---|
| A. フル買掛帳/売掛帳テーブル | `t_supplier_ledger` 取引元帳 | 見送り（既存影響大）|
| **B. 月次 summary に opening_balance 列追加** | 前月末残を次月 opening として繰越 | **採用** |
| C. 累積残ビューのみ追加（DB 変更なし）| SQL 集計で対応 | 見送り（月またぎ処理が暗黙）|
| D. 負残は自動で前払金振替 | 仕訳自動生成 | **不採用（Phase 0 で否定）**: MF 側が負残をそのまま保持していることを実測確認済み |

**B を採用**する理由：
- 既存テーブル拡張で済み、既存機能（集計バッチ・CSV出力・買掛金一覧・売掛金一覧）への影響が管理しやすい
- 値引繰越を明示的に表現できる
- MF 累積残との突合が自然に可能

### Phase 分割

| Phase | スコープ | 依存 | 所要 |
|---|---|---|---|
| **0（新設）** | MF 試算表 API スパイク: `report.read` scope 追加 → 再認可 → `/trial_balance_bs` で 1/20 と 2/20 を実測 → 負残の扱い確定 | なし | 〜1 時間 |
| **A** | 買掛: 前月繰越列 + 専用 backfill Job + 画面変更（突合 Service は無改修）| Phase 0 の結果 | 〜2 日 |
| **B** | MF 試算表 API 連携 + 独立タブ「残高突合」+ 新 Service `MfBalanceReconcileService` | Phase A | 〜2 日 |
| **C** | 売掛も同じ構造化（PK 5 列 + キャメルケース列名是正も含む）| Phase A の実装パターン | 〜2 日 |

---

## 3. Phase 0（新設）: MF 試算表スパイク

### 3.0.1 目的

- MF が買掛金残マイナスを **そのまま保持しているか、前払金へ自動振替しているか** を実測
- 結果によって Phase A の closing 定義（累積仕入 or 買掛金残）を確定
- 併せて `/trial_balance_bs` レスポンス形式・sub_account 粒度の有無を確認

### 3.0.2 手順

1. `MF_DEFAULT_CONFIG.scope` に `mfc/accounting/report.read` を追加（frontend `types/mf-integration.ts`）
2. MF 連携画面で scope を更新保存 → 「再認証」ボタン押下 → 新 scope で認可
3. 一時 debug endpoint `GET /debug/trial-balance-raw?period=YYYY-MM-DD` を追加
4. 1/20、2/20、3/20 の 3 時点で実行し、以下を確認：
   - 買掛金の closing_balance 符号
   - カミ商事の sub_account が含まれるか（粒度）
   - 前払金勘定の closing_balance
5. 結果を本設計書 §1 に事実として追記し、closing 定義を確定

### 3.0.3 結果による分岐

| MF 実測結果 | closing 定義 | Phase A で必要な対応 |
|---|---|---|
| 買掛金残はマイナスのまま保持 | **累積仕入ベース**（closing = opening + change）| 現行設計どおり |
| 負残は前払金に自動振替されている | **買掛金残ベース + 前払金調整** | 突合式を `買掛金残 + 前払金残` に。 D 案の部分採用検討 |
| その他（sub_account 粒度なし等） | 別途検討 | 仕入先別突合は Phase B で journal 累積に切り替え |

---

## 4. Phase A: 買掛金 累積残高管理

### 4.1 DB 変更

```sql
-- V024__alter_accounts_payable_summary_add_opening_balance.sql
ALTER TABLE t_accounts_payable_summary
  ADD COLUMN opening_balance_tax_included NUMERIC NOT NULL DEFAULT 0,
  ADD COLUMN opening_balance_tax_excluded NUMERIC NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_accounts_payable_summary.opening_balance_tax_included IS
  '前月末時点の累積残（税込・符号あり）。前月 closing = opening + change（手動確定時は verifiedAmount 優先）。当月 opening は前月 closing をコピー。';
COMMENT ON COLUMN t_accounts_payable_summary.opening_balance_tax_excluded IS
  '前月末時点の累積残（税抜・符号あり）';

-- 累積残ドリルダウン用
CREATE INDEX IF NOT EXISTS idx_aps_supplier_month_cum
  ON t_accounts_payable_summary (shop_no, supplier_no, transaction_month);
```

**重要**:
- 型は無精度 `NUMERIC`（既存 `tax_included_amount_change` 等と一致。`NUMERIC(15,2)` は桁落ちリスクあり）
- `NOT NULL DEFAULT 0` で NULL 伝搬を防ぎ、既存 8 ヶ月分の行にも安全に適用
- 既存 PK `(shop_no, supplier_no, transaction_month, tax_rate)` 非破壊

### 4.2 Entity 変更

```java
@Column(name = "opening_balance_tax_included")
@Builder.Default
private BigDecimal openingBalanceTaxIncluded = BigDecimal.ZERO;

@Column(name = "opening_balance_tax_excluded")
@Builder.Default
private BigDecimal openingBalanceTaxExcluded = BigDecimal.ZERO;
```

**`@Builder.Default` 必須**: 既存コードで `.build()` した時に NULL が混入しないように。

**派生プロパティ (`closingBalance*`) は Entity には定義しない**:
- `@Transient` で Entity に生やすと Jackson で既存レスポンスに漏れる（`@Data` が全 getter 公開）
- **DTO 層 (`AccountsPayableResponse`) で算出**する

### 4.3 集計バッチ（`accountsPayableAggregation`）修正

既存の `AccountsPayableAggregationTasklet` は **upsert + stale delete** パターンで、`verified_manually=true` 行を保護している。この保護を維持しつつ opening を書き込む：

```java
// 疑似コード（既存ロジックに追加）
LocalDate prevMonth = periodEndDate.minusMonths(1);
List<TAccountsPayableSummary> prev = repository.findByTransactionMonth(prevMonth);
Map<Key, Closing> prevClosing = prev.stream().collect(toMap(
    s -> new Key(s.shopNo, s.supplierNo, s.taxRate),
    s -> {
        // 手動確定時は verifiedAmount を change として優先
        BigDecimal effectiveChange = Boolean.TRUE.equals(s.getVerifiedManually())
                && s.getVerifiedAmount() != null
            ? s.getVerifiedAmount()
            : nz(s.getTaxIncludedAmountChange());
        return new Closing(
            nz(s.getOpeningBalanceTaxIncluded()).add(effectiveChange),
            nz(s.getOpeningBalanceTaxExcluded())
                .add(nz(s.getTaxExcludedAmountChange())));
    }));

// 当月の全行（手動確定行含む）に opening を set
for (var row : allCurrentMonthRows) {
    Closing p = prevClosing.get(new Key(row.shopNo, row.supplierNo, row.taxRate));
    // opening は常に上書き（手動確定でもOK: change 列は保護される）
    row.setOpeningBalanceTaxIncluded(p != null ? p.taxIncluded() : BigDecimal.ZERO);
    row.setOpeningBalanceTaxExcluded(p != null ? p.taxExcluded() : BigDecimal.ZERO);
}
```

**ポリシー（明文化）**:
| カラム | 手動確定行での扱い |
|---|---|
| `tax_included_amount_change` | **保護**（バッチ上書きしない）|
| `tax_excluded_amount_change` | **保護** |
| `verification_result` / `verified_amount` | **保護** |
| **`opening_balance_*`** | **常に上書き**（前月 closing を反映する必要があるため） |

### 4.4 過去データ再集計（新規 Job）

**既存バッチを 9 回ループ呼び出しする運用は不可**（Spring Batch 5.x は同一 JobParameters 再実行不可・進捗管理なし）。
**専用 backfill Job を新設**：

```java
// backend/src/main/java/jp/co/oda32/batch/finance/config/
//   AccountsPayableBackfillConfig.java
@Bean
public Job accountsPayableBackfillJob() {
    return new JobBuilder("accountsPayableBackfill", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(backfillStep())
        .build();
}
```

**Tasklet 実装方針**:
- `JobParameter{fromMonth, toMonth}` を受ける
- 月ごとに `@Transactional(propagation = REQUIRES_NEW)` で独立 commit
- 失敗時は途中まで反映された状態で中断し、再実行で当該月から再開可
- 進捗を `StepExecution.executionContext` に書き込み、`GET /batch/status/accountsPayableBackfill` で UI が取得
- 前月行が全く無い場合は opening=0 + WARN ログで継続（新規事業年度 or 新規仕入先を想定）

**Controller**: `BatchController` に `accountsPayableBackfill` を `JOB_DEFINITIONS` に追加。
`requiresTargetDate` ではなく `{fromMonth, toMonth}` パラメータを受ける形に拡張。

### 4.5 画面変更（`/finance/accounts-payable`）

**2 段ヘッダグルーピング**（横スクロール緩和）:
```
| 仕入先     | 税率 |  当月発生(税込)       | 検証   |  残高 (トグル)  |支払予定|操作|
|           |      | 税抜 | 税込 | 差額 |振込額|状態    | 前月繰越|累積残|       |    |
```

**「残高」列のデフォルト非表示トグル** を追加：`<Switch>` で「残高を表示」ON/OFF。日常運用は OFF、突合時のみ ON。

**負残バッジ**: 累積残が負のセルに `<Badge variant="outline" class="border-amber-500 text-amber-700">値引繰越</Badge>` + `<AlertCircle />` アイコン。

**フィルタ拡張**: 既存 `verificationFilter` とは別軸で `balanceFilter: 'all' | 'negative' | 'positive'` を新設。`VerificationFilter` 型は拡張せず分離（軸が違うため）。

**サマリバー拡張**:
- 既存 orange warning (未検証 N 件 / 不一致 M 件) と**並列** の amber banner を追加
- 「累積負残 N 件 / 累積残合計 ¥X」

**ゼロ値と NULL の表示規定**:
- 既存行 (`opening=0 DEFAULT 適用後`): `¥0` 表示（migration 後は全行 0 確定）
- NULL 表示: そもそも発生しない（`NOT NULL DEFAULT 0` のため）

### 4.6 DTO 変更

**型を分離**（既存型の肥大回避）:

```typescript
// frontend/types/accounts-payable.ts
export interface AccountsPayable { /* 既存のまま */ }

export interface AccountsPayableBalance {
  openingBalanceTaxIncluded: number   // NOT NULL なので非 null
  openingBalanceTaxExcluded: number
  closingBalanceTaxIncluded: number   // 派生（API 側で算出）
  closingBalanceTaxExcluded: number
}

export interface AccountsPayableWithBalance extends AccountsPayable, AccountsPayableBalance {}
```

**API**: `GET /finance/accounts-payable?include=balance` で balance フィールドをオプション展開。
デフォルト（include 無し）は既存レスポンスのまま（無駄なペイロード増を避ける）。

**Backend DTO**:
```java
// AccountsPayableResponse に追加（include=balance 時のみ set）
private BigDecimal openingBalanceTaxIncluded;
private BigDecimal openingBalanceTaxExcluded;
private BigDecimal closingBalanceTaxIncluded;  // from() で算出
private BigDecimal closingBalanceTaxExcluded;
```

### 4.7 突合 Service 調整（Phase A では無改修）

**既存 `MfJournalReconcileService.aggregatePayable` は触らない**。当月 change 合計での仕訳突合は継続。
Phase B で別 Service `MfBalanceReconcileService` を新設して **累積残観点の突合** を追加する。

### 4.8 Phase A の未確定事項（実装着手前に最終確認）

1. ✅ **バッチ前提**: 前月行が無いとき opening=0 で許容 + WARN ログ（§4.4 で確定）
2. ✅ **既存過去データ**: 期首 2025-06-21 から順に再集計（専用 backfill Job で、§4.4）
3. ✅ **粒度**: 累積残は `(supplier × tax_rate)` 単位で保持、画面は supplier 単位で集計表示（§4.5 二段ヘッダ）
4. ✅ **closing 定義**: **累積仕入ベース（opening + effectiveChange）で確定**（2026-04-22 Phase 0 実測結果による。§1 参照）
5. ✅ **符号規約**: 買掛金は貸方が正。opening/change とも `(貸方 - 借方)` の純増減を保持

---

## 5. Phase B: MF 試算表から買掛金残取得 + 突合

### 5.1 前提（Phase 0 で確定）

- `mfc/accounting/report.read` scope 取得済み
- `trial_balance_bs` レスポンス形式確認済み
- sub_account 粒度の有無確認済み

### 5.2 MF API 呼び出し

```
GET /api/v3/reports/trial_balance_bs?period=YYYY-MM-DD
```

- `period`: 対象月末日（締め日 20日）
- レスポンス: 勘定科目ごとの `{ account_id, account_name, closing_balance }` + 可能なら sub_account 内訳

**scope 不足時のハンドリング**:
- 401 は既存の自動 refresh で救済
- **403 は scope 不足を意味** → 専用 `MfScopeInsufficientException` を新設し UI に「`report.read` 権限が不足。再認可してください」トースト＋「接続」タブへの誘導

### 5.3 独立タブ「残高突合」新設

既存「仕訳突合」タブ内サブセクションではなく **独立タブ** とする（仕訳突合と残高突合は粒度が違うため）：

```tsx
<TabsTrigger value="balance-reconcile">残高突合</TabsTrigger>
```

**UI イメージ**:
```
累積残突合（2026-02-20 時点）

種別       自社累積残    MF 残高     差分     状態
買掛金    ¥98,123,456  ¥98,123,456  ¥0      ✅ 一致
（前払金 参考）         ¥123,456     -       ※Phase 0 結果次第
売掛金    ¥12,345,678  ¥12,345,678  ¥0      ✅ 一致

▶ 仕入先別ドリルダウン（自社 vs MF、差分行ハイライト）
```

### 5.4 バックエンド

- `MfApiClient.getTrialBalanceBs(client, accessToken, period)` 追加
- `MfBalanceReconcileService` **新設**（既存 `MfJournalReconcileService` とは別クラス）
- エンドポイント: `GET /mf-integration/balance-reconcile?period=YYYY-MM-DD`

### 5.5 仕入先別ドリルダウン

- `trial_balance_bs` に sub_account 粒度があれば MF から直接取得
- 無ければ `/journals` 累積 fallback（Phase 1 既存 debug 方式を再利用）
- mf_export_enabled=false 行の扱いは 2 軸で保持：
  - 「自社全体累積残」（全 supplier）
  - 「MF 突合用累積残」（mf_export_enabled=true OR verified_manually=true のみ）

### 5.6 Phase B 未確定事項

1. ✅ `trial_balance_bs` に sub_account 粒度が含まれるか → **Phase 0 で「含まれない」と確定**（§1 参照）。仕入先別ドリルダウンは `/journals` 累積 fallback で対応
2. ✅ scope 追加の再認可タイミング → Phase 0 で実施完了
3. ✅ 突合許容差 → **Phase 0 で「自動前払振替なし」と確定** → 許容差 0 で OK（account 単位の closing_balance 直接突合）

---

## 6. Phase C: 売掛金 累積残高管理

### 6.1 Phase C 着手前の前提調査（必須）

Phase A の実装パターンをそのまま適用できない可能性があるため、以下を先に調査：

1. **PK 構造の確認**: `TAccountsReceivableSummaryPK` は 5 列 `(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag)`。opening_balance も **5 列次元で保持する必要あり**
2. **⚠️ `transactionMonth` カラム名が キャメルケース** になっている疑い（レガシーバグ）。DB 実カラム名確認 → 必要なら snake_case 是正 migration を先に実施
3. **締め日切替運用**: partner マスタの締め日変更履歴を確認。過去に切替があれば opening 繰越が断絶する
4. **`is_otake_garbage_bag` との重複**: 設計当初「締め日タイプ単位で累積」としたが、既に `is_otake_garbage_bag` フラグで分離されているため、**新たな軸は不要かもしれない**

### 6.2 DB 変更

```sql
-- V025__alter_accounts_receivable_summary_add_opening_balance.sql
ALTER TABLE t_accounts_receivable_summary
  ADD COLUMN opening_balance_tax_included NUMERIC NOT NULL DEFAULT 0,
  ADD COLUMN opening_balance_tax_excluded NUMERIC NOT NULL DEFAULT 0;
```

（`transactionMonth` カラム名是正が必要な場合は別 V0xx migration で先行実施）

### 6.3 集計バッチ

`accountsReceivableSummary` バッチに前月 closing → 当月 opening 繰越ロジックを追加（Phase A のパターン流用）。5 列キーで紐付け。

### 6.4 画面 / 突合

- `/finance/accounts-receivable` に Phase A と同様の 2 段ヘッダ + 残高列トグル
- **partner 単位で親行 + 税率 × ゴミ袋フラグ単位で子行展開**（Collapsible）で 1 partner = 1 行の見かけを維持
- 残高突合タブに売掛金追加（Phase B の独立タブ内）

---

## 7. 実装順序（次セッション向け）

### Phase 0（新規）
0-1. frontend scope に `mfc/accounting/report.read` 追加
0-2. 画面で scope 更新保存 → 再認証
0-3. debug endpoint `/debug/trial-balance-raw` 追加
0-4. 1/20, 2/20, 3/20 の実測 → 設計書 §1 追記

### Phase A
1. V024 migration
2. `TAccountsPayableSummary` Entity に `@Builder.Default = ZERO` 付きカラム追加
3. `AccountsPayableResponse` DTO に balance フィールド追加 + `from()` で closing 算出
4. `FinanceController#listAccountsPayable` に `include=balance` クエリ実装
5. `AccountsPayableAggregationTasklet` 修正（opening 書込みロジック追加、手動確定行の扱い明文化）
6. 専用 `accountsPayableBackfillJob` 新設（月単位 `REQUIRES_NEW` tx）
7. `BatchController` に backfill ジョブ定義追加 + `{fromMonth, toMonth}` パラメータ対応
8. フロント `types/accounts-payable.ts` 型分離（`AccountsPayableBalance`）
9. フロント `accounts-payable.tsx` 2 段ヘッダ + 残高トグル + 負残バッジ + balanceFilter
10. サマリバー拡張（累積負残 amber banner）
11. 過去 8 ヶ月再集計（backfill Job 実行）
12. Unit テスト（opening 繰越、手動確定、前月行なし、二重実行冪等性）
13. E2E テスト（月跨ぎの負残繰越 golden fixture: カミ商事 298,097 円）
14. 動作確認: 2026-03-20 突合で 298,097 が opening に反映されるか

### Phase B
15. `MfApiClient.getTrialBalanceBs` 本実装（Phase 0 の debug endpoint は維持 or 削除）
16. `MfBalanceReconcileService` 新設（既存 `MfJournalReconcileService` には手を入れない）
17. `GET /mf-integration/balance-reconcile` エンドポイント
18. 独立タブ「残高突合」UI 新設
19. 仕入先別ドリルダウン（Phase 0 で判明した粒度に応じて）
20. 403/scope 不足の専用例外 + UI ハンドリング
21. 動作確認: 自社 vs MF の累積残が Phase 0 結果と整合するか

### Phase C
22. `TAccountsReceivableSummaryPK` / DB 列名確認（キャメルケース問題）
23. 必要なら列名是正 migration 先行
24. V025 migration（売掛 opening 列）
25. `TAccountsReceivableSummary` / DTO 拡張
26. `accountsReceivableSummary` バッチ拡張 + backfill Job
27. 売掛金一覧画面拡張（Collapsible で partner 集約）
28. 残高突合タブに売掛追加

---

## 8. 次セッションへの引き継ぎ事項

### 必読ドキュメント
- **本設計書**（最優先）
- `claudedocs/design-mf-integration-status.md`
- `memory/feature-mf-integration.md`

### 現在の実装状態（2026-04-22 時点）
- Phase 1 MF 連携: 完了済み（OAuth + 勘定科目同期 + 仕訳突合）
- `t_accounts_payable_summary.mf_transfer_date` 追加済み（支払予定日）
- 本設計の Phase 0〜C: **未着手**

### ⚠️ 着手前チェックリスト（必ず上から順に）
- [ ] **Phase 0 スパイク実施**（MF 1/20, 2/20, 3/20 の trial_balance_bs 実測）
- [ ] Phase 0 結果を設計書 §1 に追記し、closing 定義（累積仕入 or 買掛金残）を確定
- [ ] `TAccountsReceivableSummaryPK` の `transactionMonth` カラム名を DB で確認（snake_case vs camelCase）
- [ ] 残り ¥2,675,000 円の乖離の他原因候補を最低 1 つ特定（§1 リストを埋める）
- [ ] ユーザーに Phase A §4.8 の closing 定義を最終確認
- [ ] ユーザーに Phase B §5.6 の許容差を最終確認
- [ ] Phase A 実装開始（V024 migration から）

### 実装ポリシー（レビューで確定）
- **既存機能非破壊**: `MfJournalReconcileService` は Phase A で無改修、Phase B で別 Service 追加
- **手動確定行の保護**: バッチ再集計で change 列は保護、opening 列のみ上書き
- **DTO 層計算**: closing は Entity に持たず、Response DTO で算出
- **backfill は専用 Job**: 既存バッチのループ呼び出しではなく `accountsPayableBackfillJob` を新設
- **型分離**: `AccountsPayableBalance` 型を新設し、API は `include=balance` で展開

### 運用上の注意
- 集計バッチを過去月から順に走らせる必要あり（opening 依存のため）
- MF 側の報告スコープ追加には再認可が必要
- 売掛は PK 5 列 + レガシーカラム名問題で要追加調査
- Phase A 完了 ≠ MF 完全一致。¥2,973,089 のうち解消できるのは ¥298,097 のみ

---

## 9. 参考資料

- 本プロジェクト既存 MF 関連設計書:
  - `design-accounts-payable.md`（買掛金一覧機能）
  - `design-accounts-receivable-mf.md`（売掛金 MF 連携）
  - `design-payment-mf-import.md`（買掛支払 CSV）
  - `design-payment-mf-aux-rows.md`（補助行）
  - `design-mf-integration-status.md`（MF API 連携 Phase 1、本設計の Phase B は同 §1 Phase 2 の詳細化）
- MF API 仕様書: https://developers.api-accounting.moneyforward.com/
- 既存メモリ:
  - `reference_mf_api.md`
  - `feature-mf-integration.md`
  - `feature-accounts-payable.md`
  - `feature-payment-mf-import.md`

---

## 10. 変更履歴

- **2026-04-21**: 初版作成
- **2026-04-22**: 4 視点レビュー（DB / Java / React / アーキテクト）を反映し確定版化
  - Phase 0（MF 試算表スパイク）を新設
  - `NUMERIC NOT NULL DEFAULT 0` + `@Builder.Default` 明記
  - 手動確定行の opening 上書きポリシー明文化
  - 専用 `accountsPayableBackfillJob` 新設方針
  - `MfJournalReconcileService` は Phase A では無改修
  - 残高突合は独立タブ化
  - Phase C の PK 5 列 + キャメルケース列名問題を追記
  - closing は DTO 層計算、型分離方針
  - ¥2,675,000 未解明分の候補リスト追加
- **2026-04-22**: Phase 0 実測完了・結果反映
  - MF trial_balance_bs の query 仕様確定: `end_date=YYYY-MM-DD` のみ
  - 選択肢 D（負残の自動前払振替）を不採用で確定（MF 側振替なし実測）
  - closing 定義を「累積仕入ベース」で確定（§4.8 ⏳ → ✅）
  - sub_account 粒度なし確定 → Phase B の仕入先別突合は `/journals` 累積 fallback で確定
  - ¥2,675,000 候補に 仮払金負残 / 未払金月跨ぎ / 消費税計上タイミング差 を追加
- **2026-04-22**: Phase A 実装完了（A-1 〜 A-11）
  - V024 migration 適用、Entity/DTO/Tasklet/BackfillJob/UI すべて実装
  - **真因判明**: `AccountsPayableSummaryCalculator` の `baseAmount > 0` フィルタが値引超過行を除外していた
  - Phase A-11 でフィルタを `!= 0` に緩和、再集計 + backfill
  - **副次効果**: 3/20 PURCHASE 差分が +¥2,973,089 → **+¥10** に収束（丸め誤差レベルで実質一致）
  - カミ商事解釈訂正（当月 change -¥298,096 であって累積残負残ではない）
  - 実在する月跨ぎ負残ケース: 竹の子の里（株）物販 (2/20 close=-63,000 → 3/20 繰越)
