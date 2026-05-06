package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.repository.finance.MMfEnumTranslationRepository;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MfEnumTranslationService の autoSeed 自己学習ロジック + upsertAll の dedup/flush を検証。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfEnumTranslationServiceTest {

    @Mock MMfEnumTranslationRepository translationRepository;
    @Mock MfAccountMasterRepository mfAccountMasterRepository;
    @Mock MfOauthService mfOauthService;
    @Mock MfApiClient mfApiClient;
    @InjectMocks MfEnumTranslationService service;

    @BeforeEach
    void setUp() {
        MMfOauthClient client = MMfOauthClient.builder().id(1).build();
        when(mfOauthService.findActiveClient()).thenReturn(Optional.of(client));
        when(mfOauthService.getValidAccessToken()).thenReturn("dummy-token");
    }

    private MfAccountMaster local(String reportName, String category, String accountName) {
        MfAccountMaster m = new MfAccountMaster();
        m.setReportName(reportName);
        m.setCategory(category);
        m.setAccountName(accountName);
        m.setFinancialStatementItem(accountName);
        return m;
    }

    private MfAccount mfAccount(String name, String category, String fst) {
        return new MfAccount("id-" + name, name, "ASSET", category, fst, true, "", "t1", List.of());
    }

    @Test
    void autoSeed_既存localと一致する科目から翻訳を学習() {
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(List.of(
                mfAccount("現金", "CASH_AND_DEPOSITS", "BALANCE_SHEET"),
                mfAccount("買掛金", "ACCOUNTS_PAYABLE", "BALANCE_SHEET")
        )));
        when(mfAccountMasterRepository.findAll()).thenReturn(Arrays.asList(
                local("貸借対照表", "現金及び預金", "現金"),
                local("貸借対照表", "仕入債務", "買掛金")
        ));
        when(translationRepository.findAllByDelFlgOrderByEnumKindAscEnglishCodeAsc("0"))
                .thenReturn(new ArrayList<>());

        MfEnumTranslationService.AutoSeedResult result = service.autoSeed(1);

        // BALANCE_SHEET (1回目の出現で学習) + CASH_AND_DEPOSITS + ACCOUNTS_PAYABLE = 3 件
        assertEquals(3, result.added());
        assertEquals(0, result.unresolvedCount());

        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
        verify(translationRepository).saveAll(captor.capture());
        List<MMfEnumTranslation> saved = captor.getValue();

        assertTrue(saved.stream().anyMatch(t ->
                "FINANCIAL_STATEMENT".equals(t.getEnumKind()) && "BALANCE_SHEET".equals(t.getEnglishCode())
                        && "貸借対照表".equals(t.getJapaneseName())));
        assertTrue(saved.stream().anyMatch(t ->
                "CATEGORY".equals(t.getEnumKind()) && "CASH_AND_DEPOSITS".equals(t.getEnglishCode())
                        && "現金及び預金".equals(t.getJapaneseName())));
        assertTrue(saved.stream().anyMatch(t ->
                "CATEGORY".equals(t.getEnumKind()) && "ACCOUNTS_PAYABLE".equals(t.getEnglishCode())
                        && "仕入債務".equals(t.getJapaneseName())));
    }

    @Test
    void autoSeed_localに無い科目は空の日本語で行だけ追加_警告リストに記録() {
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(List.of(
                mfAccount("現金", "CASH_AND_DEPOSITS", "BALANCE_SHEET"),
                mfAccount("仮想通貨", "CRYPTO_ASSETS", "BALANCE_SHEET")
        )));
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(
                local("貸借対照表", "現金及び預金", "現金")
                // 仮想通貨は local に存在しない
        ));
        when(translationRepository.findAllByDelFlgOrderByEnumKindAscEnglishCodeAsc("0"))
                .thenReturn(new ArrayList<>());

        MfEnumTranslationService.AutoSeedResult result = service.autoSeed(1);

        // BALANCE_SHEET + CASH_AND_DEPOSITS (学習) + CRYPTO_ASSETS (未解決、空挿入) = 3
        assertEquals(3, result.added());
        assertEquals(1, result.unresolvedCount());
        assertTrue(result.unresolved().get(0).contains("CRYPTO_ASSETS"));

        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
        verify(translationRepository).saveAll(captor.capture());
        MMfEnumTranslation crypto = captor.getValue().stream()
                .filter(t -> "CRYPTO_ASSETS".equals(t.getEnglishCode()))
                .findFirst().orElseThrow();
        assertEquals("", crypto.getJapaneseName()); // japanese_name 空で挿入
    }

    @Test
    void autoSeed_既存翻訳が存在する英語enumはスキップ() {
        when(mfApiClient.listAccounts(any(), any())).thenReturn(new MfAccountsResponse(List.of(
                mfAccount("現金", "CASH_AND_DEPOSITS", "BALANCE_SHEET")
        )));
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(
                local("貸借対照表", "現金及び預金", "現金")
        ));
        // CASH_AND_DEPOSITS は既に登録済み
        MMfEnumTranslation existing = MMfEnumTranslation.builder()
                .enumKind("CATEGORY").englishCode("CASH_AND_DEPOSITS").japaneseName("既存値").delFlg("0").build();
        MMfEnumTranslation existingFs = MMfEnumTranslation.builder()
                .enumKind("FINANCIAL_STATEMENT").englishCode("BALANCE_SHEET").japaneseName("貸借対照表").delFlg("0").build();
        when(translationRepository.findAllByDelFlgOrderByEnumKindAscEnglishCodeAsc("0"))
                .thenReturn(List.of(existing, existingFs));

        MfEnumTranslationService.AutoSeedResult result = service.autoSeed(1);

        assertEquals(0, result.added()); // 両方既存なので何も追加されない
        assertEquals(0, result.unresolvedCount());
    }

    @Test
    void upsertAll_重複キーをdedupして保存() {
        when(translationRepository.findAllByDelFlgOrderByEnumKindAscEnglishCodeAsc("0"))
                .thenReturn(new ArrayList<>());

        // 同じ (CATEGORY, CASH_AND_DEPOSITS) で 2 行送信 → 最後勝ちで 1 行
        List<MMfEnumTranslation> requests = List.of(
                MMfEnumTranslation.builder().enumKind("CATEGORY").englishCode("CASH_AND_DEPOSITS").japaneseName("現金1").build(),
                MMfEnumTranslation.builder().enumKind("CATEGORY").englishCode("CASH_AND_DEPOSITS").japaneseName("現金2").build(),
                MMfEnumTranslation.builder().enumKind("CATEGORY").englishCode("ACCOUNTS_PAYABLE").japaneseName("仕入債務").build()
        );

        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
        when(translationRepository.saveAll(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.upsertAll(requests, 1);

        verify(translationRepository).flush();
        List<MMfEnumTranslation> saved = captor.getValue();
        assertEquals(2, saved.size()); // dedup で 2 件に
        assertEquals("現金2", saved.stream()
                .filter(t -> "CASH_AND_DEPOSITS".equals(t.getEnglishCode()))
                .findFirst().orElseThrow().getJapaneseName()); // 最後勝ち
    }

    @Test
    void buildLookup_mapに変換される() {
        when(translationRepository.findAllByDelFlgOrderByEnumKindAscEnglishCodeAsc("0"))
                .thenReturn(List.of(
                        MMfEnumTranslation.builder().enumKind("CATEGORY").englishCode("CASH_AND_DEPOSITS").japaneseName("現金及び預金").build(),
                        MMfEnumTranslation.builder().enumKind("FINANCIAL_STATEMENT").englishCode("BALANCE_SHEET").japaneseName("貸借対照表").build()
                ));

        Map<String, String> lookup = service.buildLookup();

        assertEquals("現金及び預金", lookup.get("CATEGORY|CASH_AND_DEPOSITS"));
        assertEquals("貸借対照表", lookup.get("FINANCIAL_STATEMENT|BALANCE_SHEET"));
    }
}
