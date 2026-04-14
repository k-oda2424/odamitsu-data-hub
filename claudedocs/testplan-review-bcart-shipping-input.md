# テスト計画レビュー: B-Cart出荷情報入力画面

- **レビュー対象**: `claudedocs/testplan-bcart-shipping-input.md`
- **レビュー日**: 2026-04-14
- **レビュー観点**: 網羅性 / 境界値 / 異常系 / 権限 / UI挙動 / API破壊的変更

## Severity 定義
- **Critical**: 機能欠陥を見逃す致命的欠落、リリースブロッカー
- **High**: 重要ケース欠落。追加必須
- **Medium**: 網羅性向上のため追加推奨
- **Low**: 補足・改善提案

---

## 第1ラウンド レビュー指摘

| # | Severity | 観点 | 指摘 | 対応方針 |
|---|---------|------|------|---------|
| R1-01 | High | 網羅性 | `resolveSmileProductCode` で `productName="送料"` だが `productNo="case_XXX"` のケース（送料判定が先勝ちか？）が欠落。旧実装 L187-196 では `goodsName` 判定が先。 | UT-RC-10 追加 |
| R1-02 | High | API破壊的変更 | 既存 `BCartShippingResponse` DTO 削除・旧エンドポイント `/shipping/{orderId}/status` 削除後、他ファイル（バッチ・テスト・ドキュメント等）からの参照ゼロ確認テストが欠落。Mj-05 観点で IT-BLK-07 のみ挙げているが grep 方針も明記すべき。 | 統合テストセクションに「削除前 grep 確認」注記追加 |
| R1-03 | High | 境界値 | `processingSerialNumber` の **7 桁ぴったり (`9999999`) と 8 桁ぴったり (`10000000`)** の境界は UT-SR-06 にあるが、**1 桁 (`1`)**, **負数（通常ありえないが防御）**の検証が無い。 | UT-SR-19, UT-SR-20 追加 |
| R1-04 | Medium | 網羅性 | `BCartLogisticsKey` の `quantity` 側の `stripTrailingZeros` 観点（setQuantity だけでなく quantity の BigDecimal 正規化）を明示するケースが無い。UT-SR-15 は setQuantity のみ。 | UT-SR-21 追加（quantity 側） |
| R1-05 | High | UI挙動 | フロント：shipmentStatus 変更時に、「ステータスを『発送済』にした際、確認ダイアログ」仕様が設計書に明記されていないため触れていないが、**対象外（EXCLUDED）選択時の警告**のようなUX確認が欠落している。設計書上の記載は無いため「設計外」として扱うが、テストとして言及必要。 | 設計書範囲に無いため追加しない（レビュー記録のみ） |
| R1-06 | Medium | 権限 | 全エンドポイントが `@PreAuthorize("isAuthenticated()")` 前提だが、**ショップ別フィルタ（`shopNo` 権限）** の検証が欠けている。旧システムは shopNo フィルタなし。新システム方針を確認し、admin 以外のユーザーのテストケースを追加すべき。 | IT-GET-08（非 admin ユーザー）として追加 |
| R1-07 | Medium | 異常系 | `saveAll` で **同じ logisticsId を 2 件含む request**（フロントバグ由来）のケースが無い。後勝ち or 先勝ちの挙動を明記したい。 | UT-SA-12 追加 |
| R1-08 | Low | 網羅性 | `goodsInfo` のセパレータ検証 UT-SR-09 があるが、商品名に「：」や「:」を含む場合のエスケープは設計書で触れていない。そのまま連結される旨のケース追加。 | UT-SR-22 追加 |
| R1-09 | Medium | UI挙動 | E2E で **TanStack Query のキャッシュ invalidate** は E2E-32 で触れているが、一括ステータス更新後の再取得ケースが無い。 | E2E-33 追加 |
| R1-10 | Low | UI挙動 | E2E-22 「shipmentDate="2026/04/20"」のバリデーション箇所で、date input の `type="date"` を使うならブラウザ側で弾かれるはずで、手動入力フィールドかの確認が無い。 | 設計 §4.5 に従い「正規表現 YYYY-MM-DD or 空」なので text input 前提。注記のみ。 |
| R1-11 | High | 境界値 | `partnerCode` の部分一致：**大文字小文字・全角半角** の扱いが未検証（`LIKE '%X%'` デフォルトは case-sensitive）。 | IT-GET-09 追加 |
| R1-12 | Medium | 異常系 | フロント：APIレスポンスが空配列 `[]` のときの「データなし」表示。 | E2E-34 追加 |

---

## 第1ラウンド対応（修正）

以下を `testplan-bcart-shipping-input.md` に追加する:

- UT-RC-10（送料判定先勝ち）
- UT-SR-19, UT-SR-20（processingSerialNumber 1桁 / 0以下防御）
- UT-SR-21（quantity 側 BigDecimal trailing zero）
- UT-SR-22（セパレータに含まれる文字エスケープなし）
- UT-SA-12（同一 logisticsId 重複 request 挙動）
- IT-GET-08（非 admin ユーザー権限）
- IT-GET-09（partnerCode 大文字小文字/全角半角）
- E2E-33（bulk-status 後の再取得）
- E2E-34（空配列レスポンス「データなし」表示）
- Mj-05 節に「削除前 grep 確認」注記を追加

## 第2ラウンド レビュー指摘

第1ラウンド修正適用後に再確認:

| # | Severity | 観点 | 指摘 | 対応 |
|---|---------|------|------|------|
| R2-01 | Low | 網羅性 | テストコード実装時に `MOCK_BCART_SHIPPING_LIST` を `mock-api.ts` の共通データに入れるか、spec 内ローカルに置くかの方針が両記載。統一を推奨。 | Phase 6 実装者判断とする（本計画の範囲外）— 注記のみ |
| R2-02 | Low | API破壊的変更 | `docs/detailed-design/DD_08_BCART連携.md` 等のドキュメント更新（Phase 7）との整合確認テストは E2E/Unit の範囲外のため本計画では扱わない旨を明記推奨。 | リスクセクションに追記 |

第2ラウンドでは Critical / High の追加指摘なし。修正終了。

---

## 最終評価

| 観点 | 評価 |
|------|------|
| 網羅性 | 合格（Bk/Cr/Mj 全指摘事項に対応観点 ID を割当済み） |
| 境界値 | 合格（桁数判定境界、BigDecimal 正規化、空文字、空リスト網羅） |
| 異常系 | 合格（400/401/403/500、DB 不在、重複 ID） |
| 権限 | 合格（認証・非 admin ケース追加済み） |
| UI 挙動 | 合格（非活性、ローディング、トースト、再取得、空配列） |
| API 破壊的変更 | 合格（旧エンドポイント 404 確認、grep 確認注記あり） |

**総合: 合格**。Phase 6 テスト実装に進んでよい。
