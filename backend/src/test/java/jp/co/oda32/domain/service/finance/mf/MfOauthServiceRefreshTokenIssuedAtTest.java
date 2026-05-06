package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.TMfOauthToken;
import jp.co.oda32.domain.repository.finance.MMfOauthClientRepository;
import jp.co.oda32.domain.repository.finance.TMfOauthTokenRepository;
import jp.co.oda32.util.OauthCryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G1-M4 (2026-05-06): {@code refresh_token_issued_at} カラム導入の挙動検証。
 *
 * <p>既存 {@code MfOauthService.persistToken} は softDeleteActiveTokens + 新 row insert で
 * 動作するため {@code add_date_time} は毎回更新される。rotation OFF (= MF レスポンスに
 * refresh_token なし、旧 token 流用) の場合、{@code add_date_time} 起点では 540 日寿命カウントが
 * 残日数過大評価となり警告が遅れていた。
 *
 * <p>本テストは以下を検証する:
 * <ol>
 *   <li>persistToken: rotation 動作時 (refresh_token あり) → 新 row の {@code refresh_token_issued_at = now}</li>
 *   <li>persistToken: rotation OFF (refresh_token なし) → 旧 row の {@code refresh_token_issued_at} を継承</li>
 *   <li>persistToken: 旧 row なし + refresh_token あり → now (= 初回認可)</li>
 *   <li>getStatus: {@code refresh_token_issued_at} 経過日数で {@code daysUntilReauth} を算出する</li>
 *   <li>getStatus: 540 日超過 → {@code reAuthExpired = true}, {@code daysUntilReauth = 0} (clamp)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfOauthServiceRefreshTokenIssuedAtTest {

    @Mock MMfOauthClientRepository clientRepository;
    @Mock TMfOauthTokenRepository tokenRepository;
    @Mock MfApiClient mfApiClient;
    @Mock OauthCryptoUtil cryptoUtil;
    @Mock MfOauthStateStore stateStore;
    @Mock MfOauthHostAllowlist hostAllowlist;
    @InjectMocks MfOauthService service;

    private MMfOauthClient client;

    @BeforeEach
    void setUp() {
        client = MMfOauthClient.builder()
                .id(1)
                .clientId("dummy-client")
                .clientSecretEnc("enc-secret")
                .redirectUri("http://localhost:3000/callback")
                .scope("mfc/accounting/journal.read mfc/accounting/accounts.read mfc/accounting/offices.read "
                        + "mfc/accounting/taxes.read mfc/accounting/report.read mfc/admin/tenant.read")
                .authorizeUrl("https://example/authorize")
                .tokenUrl("https://example/token")
                .apiBaseUrl("https://example/api")
                .delFlg("0")
                .mfTenantId("tenant-x")
                .build();
        ReflectionTestUtils.setField(service, "self", service);

        // crypto は単純に passthrough (実暗号化は不要)
        lenient().when(cryptoUtil.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        lenient().when(cryptoUtil.decrypt(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s != null && s.startsWith("enc:") ? s.substring(4) : s;
        });
    }

    /**
     * 内部 helper: persistToken は private なのでリフレクションで起動。
     */
    private void invokePersistToken(MfTokenResponse res, Integer userNo) {
        ReflectionTestUtils.invokeMethod(service, "persistToken", client, res, userNo);
    }

    // ============================================================
    // persistToken: rotation 動作 (refresh_token あり) → now を採用
    // ============================================================
    @Test
    void persistToken_rotation動作_新発行日はnow() {
        // 旧 active row が存在 (rotation 動作時でも softDelete 対象として読まれる)
        TMfOauthToken previous = TMfOauthToken.builder()
                .id(100L)
                .clientId(1)
                .accessTokenEnc("enc:old-access")
                .refreshTokenEnc("enc:old-refresh")
                .refreshTokenIssuedAt(Timestamp.from(Instant.now().minus(100, ChronoUnit.DAYS)))
                .addDateTime(Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)))
                .delFlg("0")
                .build();
        when(tokenRepository.findFirstByClientIdAndDelFlgOrderByIdDesc(1, "0"))
                .thenReturn(Optional.of(previous));

        Instant before = Instant.now();
        MfTokenResponse res = new MfTokenResponse(
                "new-access", "new-refresh", "Bearer", 3600L, "scope");

        invokePersistToken(res, 999);

        ArgumentCaptor<TMfOauthToken> captor = ArgumentCaptor.forClass(TMfOauthToken.class);
        verify(tokenRepository).save(captor.capture());
        TMfOauthToken saved = captor.getValue();

        // refresh_token_issued_at は now (= persistToken 内で生成された Timestamp.from(Instant.now()))
        assertThat(saved.getRefreshTokenIssuedAt()).isNotNull();
        Instant savedIssuedAt = saved.getRefreshTokenIssuedAt().toInstant();
        assertThat(savedIssuedAt).isAfterOrEqualTo(before.minusSeconds(2));
        assertThat(savedIssuedAt).isBeforeOrEqualTo(Instant.now().plusSeconds(2));
        // 新 refresh_token が暗号化されて入っていることも確認
        assertThat(saved.getRefreshTokenEnc()).isEqualTo("enc:new-refresh");
        // 旧 row は softDelete された
        verify(tokenRepository, times(1)).softDeleteActiveTokens(eq(1), any(Timestamp.class), eq(999));
    }

    // ============================================================
    // persistToken: rotation OFF (refresh_token なし) → 旧 row 継承
    // ============================================================
    @Test
    void persistToken_rotationOFF_新発行日は旧row継承() {
        Instant oldIssuedAt = Instant.now().minus(450, ChronoUnit.DAYS);
        TMfOauthToken previous = TMfOauthToken.builder()
                .id(100L)
                .clientId(1)
                .accessTokenEnc("enc:old-access")
                .refreshTokenEnc("enc:old-refresh")
                .refreshTokenIssuedAt(Timestamp.from(oldIssuedAt))
                // add_date_time は毎回更新されるので別値
                .addDateTime(Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .delFlg("0")
                .build();
        when(tokenRepository.findFirstByClientIdAndDelFlgOrderByIdDesc(1, "0"))
                .thenReturn(Optional.of(previous));

        // refresh_token = null = rotation OFF
        MfTokenResponse res = new MfTokenResponse(
                "new-access", null, "Bearer", 3600L, "scope");

        invokePersistToken(res, 999);

        ArgumentCaptor<TMfOauthToken> captor = ArgumentCaptor.forClass(TMfOauthToken.class);
        verify(tokenRepository).save(captor.capture());
        TMfOauthToken saved = captor.getValue();

        // refresh_token_issued_at は旧 row の値を継承 (= now でも add_date_time でもない)
        assertThat(saved.getRefreshTokenIssuedAt()).isNotNull();
        Instant savedIssuedAt = saved.getRefreshTokenIssuedAt().toInstant();
        long deltaSec = Math.abs(savedIssuedAt.getEpochSecond() - oldIssuedAt.getEpochSecond());
        assertThat(deltaSec).as("旧 row の refresh_token_issued_at が継承されている").isLessThanOrEqualTo(1L);
        // refresh_token_enc は旧 row の値を流用
        assertThat(saved.getRefreshTokenEnc()).isEqualTo("enc:old-refresh");
    }

    // ============================================================
    // persistToken: 旧 row なし + refresh_token あり → now (= 初回認可)
    // ============================================================
    @Test
    void persistToken_初回認可_新発行日はnow() {
        when(tokenRepository.findFirstByClientIdAndDelFlgOrderByIdDesc(1, "0"))
                .thenReturn(Optional.empty());

        Instant before = Instant.now();
        MfTokenResponse res = new MfTokenResponse(
                "new-access", "new-refresh", "Bearer", 3600L, "scope");

        invokePersistToken(res, 999);

        ArgumentCaptor<TMfOauthToken> captor = ArgumentCaptor.forClass(TMfOauthToken.class);
        verify(tokenRepository).save(captor.capture());
        TMfOauthToken saved = captor.getValue();

        assertThat(saved.getRefreshTokenIssuedAt()).isNotNull();
        Instant savedIssuedAt = saved.getRefreshTokenIssuedAt().toInstant();
        assertThat(savedIssuedAt).isAfterOrEqualTo(before.minusSeconds(2));
        assertThat(saved.getRefreshTokenEnc()).isEqualTo("enc:new-refresh");
    }

    // ============================================================
    // getStatus: refresh_token_issued_at 起点で daysUntilReauth を算出
    // ============================================================
    @Test
    void getStatus_発行から100日経過_残日数は440日() {
        when(clientRepository.findFirstByDelFlgOrderByIdDesc("0")).thenReturn(Optional.of(client));
        Instant issuedAt = Instant.now().minus(100, ChronoUnit.DAYS);
        TMfOauthToken token = TMfOauthToken.builder()
                .id(100L)
                .clientId(1)
                .accessTokenEnc("enc:a")
                .refreshTokenEnc("enc:r")
                .refreshTokenIssuedAt(Timestamp.from(issuedAt))
                // add_date_time は意図的に「直近 1 時間」(rotation OFF で更新された風) にしておく
                .addDateTime(Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .expiresAt(Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .scope(client.getScope())
                .delFlg("0")
                .build();
        when(tokenRepository.findFirstByClientIdAndDelFlgOrderByIdDesc(1, "0"))
                .thenReturn(Optional.of(token));

        MfTokenStatus st = service.getStatus();

        // add_date_time 起点なら ~0 日になる。issued_at 起点 (100 日経過) → 440 日が正解。
        // 端数で ±1 日程度のズレが出ても安全側に許容する。
        assertThat(st.daysUntilReauth()).isBetween(439, 440);
        assertThat(st.reAuthRequired()).isFalse();
        assertThat(st.reAuthExpired()).isFalse();
        assertThat(st.refreshTokenIssuedAt()).isNotNull();
    }

    // ============================================================
    // getStatus: 540 日超過 → reAuthExpired=true, daysUntilReauth=0
    // ============================================================
    @Test
    void getStatus_発行から600日経過_期限超過() {
        when(clientRepository.findFirstByDelFlgOrderByIdDesc("0")).thenReturn(Optional.of(client));
        Instant issuedAt = Instant.now().minus(600, ChronoUnit.DAYS);
        TMfOauthToken token = TMfOauthToken.builder()
                .id(100L)
                .clientId(1)
                .accessTokenEnc("enc:a")
                .refreshTokenEnc("enc:r")
                .refreshTokenIssuedAt(Timestamp.from(issuedAt))
                .addDateTime(Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .expiresAt(Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .scope(client.getScope())
                .delFlg("0")
                .build();
        when(tokenRepository.findFirstByClientIdAndDelFlgOrderByIdDesc(1, "0"))
                .thenReturn(Optional.of(token));

        MfTokenStatus st = service.getStatus();

        // 540 日超過: reAuthExpired=true, daysUntilReauth=0 (clamp), reAuthRequired=true
        assertThat(st.reAuthExpired()).isTrue();
        assertThat(st.reAuthRequired()).isTrue();
        assertThat(st.daysUntilReauth()).isEqualTo(0);
    }
}
