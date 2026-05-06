package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MfAccountSyncService の差分計算ロジック (INSERT/UPDATE/DELETE) + 未翻訳 enum 検知を検証。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfAccountSyncServiceTest {

    @Mock MfOauthService mfOauthService;
    @Mock MfApiClient mfApiClient;
    @Mock MfAccountMasterRepository mfAccountMasterRepository;
    @Mock MfEnumTranslationService translationService;
    @InjectMocks MfAccountSyncService service;

    private MMfOauthClient client;

    @BeforeEach
    void setUp() {
        client = MMfOauthClient.builder().id(1).apiBaseUrl("https://api-accounting.moneyforward.com").build();
        when(mfOauthService.findActiveClient()).thenReturn(Optional.of(client));
        when(mfOauthService.getValidAccessToken()).thenReturn("dummy-token");
    }

    // --- helper factories ---

    private MfAccount mfAccount(String id, String name, String group, String category, String fst,
                                 String taxId, boolean available, List<MfAccount.MfSubAccount> subs) {
        return new MfAccount(id, name, group, category, fst, available, "", taxId, subs);
    }

    private MfAccount.MfSubAccount mfSub(String id, String name, String accountId, String searchKey, String taxId) {
        return new MfAccount.MfSubAccount(id, name, accountId, searchKey, taxId);
    }

    private MfAccountMaster local(String reportName, String category, String accountName, String subAccountName,
                                   String tax, String searchKey, boolean isActive, int displayOrder) {
        MfAccountMaster m = new MfAccountMaster();
        m.setReportName(reportName);
        m.setCategory(category);
        m.setFinancialStatementItem(accountName);
        m.setAccountName(accountName);
        m.setSubAccountName(subAccountName);
        m.setTaxClassification(tax);
        m.setSearchKey(searchKey);
        m.setIsActive(isActive);
        m.setDisplayOrder(displayOrder);
        return m;
    }

    private Map<String, String> defaultTranslations() {
        Map<String, String> m = new HashMap<>();
        m.put("FINANCIAL_STATEMENT|BALANCE_SHEET", "貸借対照表");
        m.put("FINANCIAL_STATEMENT|PROFIT_AND_LOSS", "損益計算書");
        m.put("CATEGORY|CASH_AND_DEPOSITS", "現金及び預金");
        m.put("CATEGORY|ACCOUNTS_PAYABLE", "仕入債務");
        return m;
    }

    // --- tests ---

    @Test
    void preview_全一致_差分なし() {
        // MF: 現金 1 件（sub_accountsなし）
        List<MfAccount> mfAccounts = List.of(
                mfAccount("a1", "現金", "ASSET", "CASH_AND_DEPOSITS", "BALANCE_SHEET", "t1", true, List.of())
        );
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(mfAccounts));
        when(mfApiClient.listTaxes(any(), any())).thenReturn(new MfTaxesResponse(List.of(
                new MfTax("t1", "対象外", "対象外", true, "", BigDecimal.ZERO)
        )));
        when(translationService.buildLookup()).thenReturn(defaultTranslations());
        // local: MF と完全一致
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(
                local("貸借対照表", "現金及び預金", "現金", null, "対象外", null, true, 0)
        ));

        MfAccountSyncService.SyncResult result = service.preview();

        assertFalse(result.applied());
        assertEquals(0, result.insertCount());
        assertEquals(0, result.updateCount());
        assertEquals(0, result.deleteCount());
        assertTrue(result.unknownEnums().isEmpty());
        verify(mfAccountMasterRepository, never()).saveAll(any());
    }

    @Test
    void preview_新規追加_MFに存在しローカルに無い() {
        List<MfAccount> mfAccounts = List.of(
                mfAccount("a1", "現金", "ASSET", "CASH_AND_DEPOSITS", "BALANCE_SHEET", "t1", true, List.of()),
                mfAccount("a2", "買掛金", "LIABILITY", "ACCOUNTS_PAYABLE", "BALANCE_SHEET", "t1", true, List.of())
        );
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(mfAccounts));
        when(mfApiClient.listTaxes(any(), any())).thenReturn(new MfTaxesResponse(List.of(
                new MfTax("t1", "対象外", "対象外", true, "", BigDecimal.ZERO)
        )));
        when(translationService.buildLookup()).thenReturn(defaultTranslations());
        // local: 現金だけ存在（買掛金は未登録）
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(
                local("貸借対照表", "現金及び預金", "現金", null, "対象外", null, true, 0)
        ));

        MfAccountSyncService.SyncResult result = service.preview();

        assertEquals(1, result.insertCount());
        assertEquals(0, result.deleteCount());
        assertEquals("買掛金", result.insertSamples().get(0).accountName());
    }

    @Test
    void preview_削除_ローカルに存在しMFに無い() {
        List<MfAccount> mfAccounts = List.of(
                mfAccount("a1", "現金", "ASSET", "CASH_AND_DEPOSITS", "BALANCE_SHEET", "t1", true, List.of())
        );
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(mfAccounts));
        when(mfApiClient.listTaxes(any(), any())).thenReturn(new MfTaxesResponse(List.of(
                new MfTax("t1", "対象外", "対象外", true, "", BigDecimal.ZERO)
        )));
        when(translationService.buildLookup()).thenReturn(defaultTranslations());
        // local: 現金 + 廃止予定の旧勘定（MF から消えた）
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(
                local("貸借対照表", "現金及び預金", "現金", null, "対象外", null, true, 0),
                local("貸借対照表", "仕入債務", "買掛金", "廃業したX社", "対象外", "999999", true, 5)
        ));

        MfAccountSyncService.SyncResult result = service.preview();

        assertEquals(0, result.insertCount());
        assertEquals(1, result.deleteCount());
        assertEquals("買掛金", result.deleteSamples().get(0).accountName());
        assertEquals("廃業したX社", result.deleteSamples().get(0).subAccountName());
    }

    @Test
    void preview_更新_フィールド値が異なる() {
        List<MfAccount> mfAccounts = List.of(
                mfAccount("a1", "現金", "ASSET", "CASH_AND_DEPOSITS", "BALANCE_SHEET", "t1", true, List.of())
        );
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(mfAccounts));
        when(mfApiClient.listTaxes(any(), any())).thenReturn(new MfTaxesResponse(List.of(
                new MfTax("t1", "対象外", "対象外", true, "", BigDecimal.ZERO)
        )));
        when(translationService.buildLookup()).thenReturn(defaultTranslations());
        // local: display_order が MF (0) と違う (5)
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(
                local("貸借対照表", "現金及び預金", "現金", null, "対象外", null, true, 5)
        ));

        MfAccountSyncService.SyncResult result = service.preview();

        assertEquals(0, result.insertCount());
        assertEquals(1, result.updateCount());
        assertEquals(0, result.deleteCount());
        assertTrue(result.updateSamples().get(0).changes().contains("order"));
    }

    @Test
    void preview_未翻訳enum_英語のまま保存し警告() {
        // 翻訳辞書に無い新規 category
        List<MfAccount> mfAccounts = List.of(
                mfAccount("a1", "仮想通貨", "ASSET", "CRYPTO_ASSETS", "BALANCE_SHEET", "t1", true, List.of())
        );
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(mfAccounts));
        when(mfApiClient.listTaxes(any(), any())).thenReturn(new MfTaxesResponse(List.of(
                new MfTax("t1", "対象外", "対象外", true, "", BigDecimal.ZERO)
        )));
        when(translationService.buildLookup()).thenReturn(defaultTranslations()); // CRYPTO_ASSETS なし
        when(mfAccountMasterRepository.findAll()).thenReturn(new ArrayList<>());

        MfAccountSyncService.SyncResult result = service.preview();

        assertEquals(1, result.insertCount());
        assertTrue(result.unknownEnums().stream().anyMatch(s -> s.contains("CRYPTO_ASSETS")));
        // 英語のまま保存されること
        assertEquals("CRYPTO_ASSETS", result.insertSamples().get(0).category());
    }

    @Test
    void preview_subAccount展開_親1件に対し補助科目数ぶんの行を生成() {
        List<MfAccount> mfAccounts = List.of(
                mfAccount("a1", "買掛金", "LIABILITY", "ACCOUNTS_PAYABLE", "BALANCE_SHEET", "t1", true, List.of(
                        mfSub("s1", "A社", "a1", "000500", "t1"),
                        mfSub("s2", "B社", "a1", "000700", "t1")
                ))
        );
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(mfAccounts));
        when(mfApiClient.listTaxes(any(), any())).thenReturn(new MfTaxesResponse(List.of(
                new MfTax("t1", "対象外", "対象外", true, "", BigDecimal.ZERO)
        )));
        when(translationService.buildLookup()).thenReturn(defaultTranslations());
        when(mfAccountMasterRepository.findAll()).thenReturn(new ArrayList<>());

        MfAccountSyncService.SyncResult result = service.preview();

        assertEquals(2, result.insertCount()); // A社 + B社（親単独行は生成されない）
    }

    @Test
    void apply_差分ありのとき_saveとdeleteが呼ばれる() {
        List<MfAccount> mfAccounts = List.of(
                mfAccount("a1", "現金", "ASSET", "CASH_AND_DEPOSITS", "BALANCE_SHEET", "t1", true, List.of()),
                mfAccount("a2", "買掛金", "LIABILITY", "ACCOUNTS_PAYABLE", "BALANCE_SHEET", "t1", true, List.of())
        );
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(mfAccounts));
        when(mfApiClient.listTaxes(any(), any())).thenReturn(new MfTaxesResponse(List.of(
                new MfTax("t1", "対象外", "対象外", true, "", BigDecimal.ZERO)
        )));
        when(translationService.buildLookup()).thenReturn(defaultTranslations());
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(
                local("貸借対照表", "仕入債務", "廃止勘定", null, "対象外", null, true, 0)
        ));

        MfAccountSyncService.SyncResult result = service.apply(1);

        assertTrue(result.applied());
        assertEquals(2, result.insertCount());
        assertEquals(1, result.deleteCount());
        verify(mfAccountMasterRepository, times(1)).deleteAllInBatch(any());
        verify(mfAccountMasterRepository, times(2)).saveAll(any()); // update + insert の 2 回
    }
}
