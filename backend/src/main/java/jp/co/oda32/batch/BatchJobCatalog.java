package jp.co.oda32.batch;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Batch ジョブのカタログ (一元管理)。
 * <p>
 * 旧実装は {@code BatchController.JOB_DEFINITIONS} (Map list)、
 * {@code AccountingStatusService.queryBatchJobs()} の SQL IN リスト、
 * フロントエンド {@code accounting-workflow.tsx} の {@code BatchChip.names} と
 * 3 箇所で同種のジョブ名集合が散在していた (SF-H05)。
 * 本クラスでジョブ名・カテゴリ・ワークフローステップ等を集約し、両 backend サービスから参照する。
 * <p>
 * 設計書: claudedocs/design-accounting-workflow.md §7.5
 *
 * @since 2026-05-04 (SF-H05)
 */
public final class BatchJobCatalog {

    /** カタログエントリ。 */
    public record Entry(
            /** Spring Batch ジョブ名。Bean 名は "{jobName}Job"。 */
            String jobName,
            /** カテゴリ表示用ラベル。 */
            String category,
            /** 説明 (BatchController#listJobs の表示用)。 */
            String description,
            /** ジョブ起動に shopNo パラメータが必須か。 */
            boolean requiresShopNo,
            /** 経理ワークフロー画面 (AccountingStatusService) で監視対象とするか。 */
            boolean monitoredInWorkflow,
            /** ワークフロー上のステップ番号 (1〜5、未割当は 0)。 */
            int workflowStep,
            /** ステップ chip 表示用の短縮ラベル。 */
            String shortLabel
    ) {}

    public static final List<Entry> ENTRIES = List.of(
            new Entry("bCartOrderImport", "B-CART連携", "新規受注取込", false, false, 0, "受注取込"),
            new Entry("smileOrderFileImport", "B-CART連携", "売上明細取込", false, false, 0, "売上明細取込"),
            new Entry("bCartLogisticsCsvExport", "B-CART連携", "出荷実績CSV出力", false, false, 0, "出荷CSV"),
            new Entry("bCartMemberUpdate", "B-CART連携", "新規会員取込", false, false, 0, "会員取込"),
            new Entry("bCartProductsImport", "B-CART連携", "商品マスタ同期", false, false, 0, "商品同期"),
            new Entry("bCartCategorySync", "B-CART連携", "カテゴリマスタ同期", false, false, 0, "カテゴリ同期"),
            new Entry("bCartCategoryUpdate", "B-CART連携", "カテゴリマスタ反映", false, false, 0, "カテゴリ反映"),
            new Entry("bCartProductDescriptionUpdate", "B-CART連携", "商品説明反映", false, false, 0, "商品説明反映"),
            new Entry("goodsFileImport", "マスタ取込", "SMILE商品マスタCSV取込", true, false, 0, "商品マスタ取込"),
            new Entry("purchaseFileImport", "SMILE取込", "SMILE仕入ファイル取込", false, true, 1, "仕入取込"),
            new Entry("smilePaymentImport", "SMILE取込", "SMILE支払情報取込", false, true, 2, "支払取込"),
            new Entry("accountsPayableAggregation", "買掛金", "買掛金集計", false, true, 5, "買掛集計"),
            new Entry("accountsPayableVerification", "買掛金", "買掛金検証", false, true, 5, "買掛検証"),
            new Entry("accountsPayableSummary", "買掛金", "買掛金サマリ", false, true, 5, "買掛サマリ"),
            new Entry("accountsPayableBackfill", "買掛金", "買掛金累積残再集計", false, false, 0, "累積残再集計"),
            new Entry("accountsReceivableSummary", "売掛金", "売掛金サマリ", false, true, 4, "売掛集計"),
            new Entry("purchaseJournalIntegration", "仕訳連携", "買掛仕入CSV出力（マネーフォワード連携）", false, true, 5, "仕訳連携(買掛)"),
            new Entry("salesJournalIntegration", "仕訳連携", "売掛売上CSV出力（マネーフォワード連携）", false, true, 4, "仕訳連携(売掛)"),
            new Entry("partnerPriceChangePlanCreate", "見積管理", "得意先価格変更予定作成・見積自動生成", false, false, 0, "価格改定計画")
    );

    private static final Set<String> ALLOWED_JOB_NAMES = ENTRIES.stream()
            .map(Entry::jobName).collect(Collectors.toUnmodifiableSet());

    private static final List<String> MONITORED_JOB_NAMES = ENTRIES.stream()
            .filter(Entry::monitoredInWorkflow)
            .map(Entry::jobName)
            .toList();

    private BatchJobCatalog() {}

    /** 許可されたジョブ名集合 (BatchController の whitelist 用)。 */
    public static Set<String> allowedJobNames() {
        return ALLOWED_JOB_NAMES;
    }

    /** 経理ワークフローで監視対象のジョブ名 (AccountingStatusService 用)。 */
    public static List<String> monitoredJobNames() {
        return MONITORED_JOB_NAMES;
    }
}
