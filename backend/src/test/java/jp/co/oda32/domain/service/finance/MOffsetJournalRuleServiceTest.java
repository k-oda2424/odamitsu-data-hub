package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
import jp.co.oda32.dto.finance.OffsetJournalRuleRequest;
import jp.co.oda32.exception.FinanceBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G2-M8: {@link MOffsetJournalRuleService} の CRUD + 監査フィールド更新を検証。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MOffsetJournalRuleServiceTest {

    @Mock MOffsetJournalRuleRepository repository;
    @InjectMocks MOffsetJournalRuleService service;

    private static MOffsetJournalRule sampleEntity() {
        return MOffsetJournalRule.builder()
                .id(1)
                .shopNo(1)
                .creditAccount("仕入値引・戻し高")
                .creditDepartment("物販事業部")
                .creditTaxCategory("課税仕入-返還等 10%")
                .summaryPrefix("相殺／")
                .delFlg("0")
                .build();
    }

    private static OffsetJournalRuleRequest sampleRequest() {
        OffsetJournalRuleRequest req = new OffsetJournalRuleRequest();
        req.setShopNo(1);
        req.setCreditAccount("仕入値引・戻し高");
        req.setCreditSubAccount("");
        req.setCreditDepartment("物販事業部");
        req.setCreditTaxCategory("課税仕入-返還等 10%");
        req.setSummaryPrefix("相殺／");
        return req;
    }

    @Test
    void findByShopNo_active行_返却() {
        when(repository.findByShopNoAndDelFlg(1, "0")).thenReturn(Optional.of(sampleEntity()));

        MOffsetJournalRule result = service.findByShopNo(1);

        assertNotNull(result);
        assertEquals("仕入値引・戻し高", result.getCreditAccount());
    }

    @Test
    void findByShopNo_未登録ならnull() {
        when(repository.findByShopNoAndDelFlg(99, "0")).thenReturn(Optional.empty());

        MOffsetJournalRule result = service.findByShopNo(99);

        assertNull(result);
    }

    @Test
    void findAll_del_flg_0だけを返す() {
        when(repository.findByDelFlgOrderByShopNoAsc("0")).thenReturn(List.of(sampleEntity()));

        List<MOffsetJournalRule> result = service.findAll();

        assertEquals(1, result.size());
        verify(repository).findByDelFlgOrderByShopNoAsc("0");
    }

    @Test
    void create_監査フィールドが付与される() {
        when(repository.save(any(MOffsetJournalRule.class))).thenAnswer(inv -> inv.getArgument(0));

        OffsetJournalRuleRequest req = sampleRequest();
        MOffsetJournalRule saved = service.create(req);

        ArgumentCaptor<MOffsetJournalRule> cap = ArgumentCaptor.forClass(MOffsetJournalRule.class);
        verify(repository).save(cap.capture());
        MOffsetJournalRule captured = cap.getValue();

        assertEquals(1, captured.getShopNo());
        assertEquals("仕入値引・戻し高", captured.getCreditAccount());
        assertEquals("物販事業部", captured.getCreditDepartment());
        assertEquals("課税仕入-返還等 10%", captured.getCreditTaxCategory());
        assertEquals("相殺／", captured.getSummaryPrefix());
        assertEquals("0", captured.getDelFlg());
        assertNotNull(captured.getAddDateTime(), "add_date_time が付与される");
        assertNotNull(captured.getModifyDateTime(), "modify_date_time が付与される");
        // creditSubAccount は空文字 → null 正規化
        assertNull(captured.getCreditSubAccount());
        assertEquals(saved, captured);
    }

    @Test
    void update_既存行を上書きしmodify_date_time更新() {
        MOffsetJournalRule existing = sampleEntity();
        when(repository.findById(1)).thenReturn(Optional.of(existing));
        when(repository.save(any(MOffsetJournalRule.class))).thenAnswer(inv -> inv.getArgument(0));

        OffsetJournalRuleRequest req = sampleRequest();
        req.setCreditAccount("仕入値引（変更後）");
        req.setCreditTaxCategory("対象外");

        MOffsetJournalRule updated = service.update(1, req);

        assertEquals("仕入値引（変更後）", updated.getCreditAccount());
        assertEquals("対象外", updated.getCreditTaxCategory());
        assertNotNull(updated.getModifyDateTime());
    }

    @Test
    void update_存在しないidはIllegalArgumentException() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.update(99, sampleRequest()));
    }

    @Test
    void delete_論理削除でdel_flg_1() {
        MOffsetJournalRule existing = sampleEntity();
        when(repository.findById(1)).thenReturn(Optional.of(existing));
        // active 行が 2 件以上あるので削除 OK
        when(repository.countByShopNoAndDelFlg(1, "0")).thenReturn(2L);
        when(repository.save(any(MOffsetJournalRule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1);

        ArgumentCaptor<MOffsetJournalRule> cap = ArgumentCaptor.forClass(MOffsetJournalRule.class);
        verify(repository).save(cap.capture());
        assertEquals("1", cap.getValue().getDelFlg());
    }

    @Test
    void delete_存在しないidはIllegalArgumentException() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.delete(99));
    }

    @Test
    void delete_最後のactive行は削除禁止() {
        // Codex Major fix: shop_no 単位で active 行が 1 件しか残っていないとき、
        // それを削除すると PaymentMfImportService の OFFSET 副行が hardcoded default fallback
        // に強制される (= 税理士確認反映が失われる)。FinanceBusinessException で防止する。
        MOffsetJournalRule existing = sampleEntity();
        when(repository.findById(1)).thenReturn(Optional.of(existing));
        when(repository.countByShopNoAndDelFlg(1, "0")).thenReturn(1L);

        FinanceBusinessException ex = assertThrows(FinanceBusinessException.class,
                () -> service.delete(1));
        assertEquals("OFFSET_RULE_LAST_ACTIVE", ex.getErrorCode());
        // 削除がブロックされたので save は呼ばれない
        verify(repository, org.mockito.Mockito.never()).save(any(MOffsetJournalRule.class));
    }

    @Test
    void delete_既にdel_flg_1の行は二重削除でも例外にならない() {
        // idempotent 動作維持: 既に論理削除済の行は activeCount 判定をスキップする。
        MOffsetJournalRule zombie = sampleEntity();
        zombie.setDelFlg("1");
        when(repository.findById(1)).thenReturn(Optional.of(zombie));
        when(repository.save(any(MOffsetJournalRule.class))).thenAnswer(inv -> inv.getArgument(0));

        // count を呼ばずに通過することを確認
        service.delete(1);
        verify(repository, org.mockito.Mockito.never()).countByShopNoAndDelFlg(any(), any());
        verify(repository).save(any(MOffsetJournalRule.class));
    }
}
