package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.repository.finance.MMfOauthClientRepository;
import jp.co.oda32.domain.repository.finance.TMfOauthTokenRepository;
import jp.co.oda32.util.OauthCryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G1-M3: {@link MfOauthService#getValidAccessToken()} の未バインド client 強制 binding 動作を検証。
 *
 * <p>P1-01 で導入した tenant binding は refresh 経路でしか働かなかったため、
 * P1-01 導入前から認可済みの client (mf_tenant_id IS NULL) が fresh access_token を持つ間は
 * 業務 API が tenant 検証なしで通る穴があった。
 *
 * <p>本テストは以下の 4 シナリオを mock で検証する:
 * <ol>
 *   <li>{@code mf_tenant_id IS NULL} + token fresh → 強制 {@code getTenant} + bindTenantInNewTx 呼び出し</li>
 *   <li>{@code mf_tenant_id IS NULL} + token fresh + {@code getTenant} 例外 → {@link MfTenantBindingFailedException}</li>
 *   <li>{@code mf_tenant_id} 設定済 + token fresh → {@code getTenant} 呼ばれない (通常 fresh path)</li>
 *   <li>{@code mf_tenant_id IS NULL} + token expired → 通常 refresh path で tenant 検証 (= 既存 P1-01 動作)</li>
 * </ol>
 *
 * @since 2026-05-06 (G1-M3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfOauthServiceForcedBindingTest {

    @Mock MMfOauthClientRepository clientRepository;
    @Mock TMfOauthTokenRepository tokenRepository;
    @Mock MfApiClient mfApiClient;
    @Mock OauthCryptoUtil cryptoUtil;
    @Mock MfOauthStateStore stateStore;
    @Mock MfOauthHostAllowlist hostAllowlist;
    @InjectMocks MfOauthService service;

    private MMfOauthClient unboundClient;
    private MMfOauthClient boundClient;

    @BeforeEach
    void setUp() {
        unboundClient = MMfOauthClient.builder()
                .id(1)
                .clientId("dummy-client")
                .clientSecretEnc("enc-secret")
                .redirectUri("http://localhost:3000/callback")
                .scope("mfc/admin/tenant.read")
                .authorizeUrl("https://example/authorize")
                .tokenUrl("https://example/token")
                .apiBaseUrl("https://example/api")
                .delFlg("0")
                .mfTenantId(null) // 未バインド (P1-01 導入前の旧データ)
                .mfTenantName(null)
                .tenantBoundAt(null)
                .addDateTime(Timestamp.from(Instant.now()))
                .build();
        boundClient = MMfOauthClient.builder()
                .id(2)
                .clientId("dummy-client-2")
                .clientSecretEnc("enc-secret-2")
                .redirectUri("http://localhost:3000/callback")
                .scope("mfc/admin/tenant.read")
                .authorizeUrl("https://example/authorize")
                .tokenUrl("https://example/token")
                .apiBaseUrl("https://example/api")
                .delFlg("0")
                .mfTenantId("tenant-existing")
                .mfTenantName("Existing Co")
                .tenantBoundAt(Timestamp.from(Instant.now()))
                .addDateTime(Timestamp.from(Instant.now()))
                .build();

        // self injection を実体に差し替え (REQUIRES_NEW を proxy 経由でなく直接呼ぶ)
        ReflectionTestUtils.setField(service, "self", service);
    }

    private MfOauthService.TokenSnapshot snapshot(MMfOauthClient client, boolean fresh) {
        Instant expiresAt = fresh
                ? Instant.now().plus(1, ChronoUnit.HOURS)   // fresh
                : Instant.now().minus(1, ChronoUnit.HOURS); // expired
        return new MfOauthService.TokenSnapshot(
                client,
                "plain-secret",
                "plain-access-token",
                "plain-refresh-token",
                expiresAt);
    }

    // --- ケース 1: NULL + fresh → 強制 binding ---

    @Test
    void getValidAccessToken_未バインド_fresh_token_強制バインドが実行される() {
        // 既存 token snapshot: tenant 未バインド + fresh
        MfOauthService spied = org.mockito.Mockito.spy(service);
        ReflectionTestUtils.setField(spied, "self", spied);
        org.mockito.Mockito.doReturn(snapshot(unboundClient, true))
                .when(spied).loadActiveTokenSnapshot();
        org.mockito.Mockito.doNothing()
                .when(spied).bindTenantInNewTx(eq(1), any(MfTenantResponse.class));

        MfTenantResponse tenant = new MfTenantResponse("tenant-new", "New Co");
        when(mfApiClient.getTenant(eq(unboundClient), eq("plain-access-token"))).thenReturn(tenant);

        String token = spied.getValidAccessToken();

        // access_token は変えずに既存の plain-access-token を返却
        assertThat(token).isEqualTo("plain-access-token");
        // /v2/tenant が呼ばれた
        verify(mfApiClient, times(1)).getTenant(eq(unboundClient), eq("plain-access-token"));
        // bindTenantInNewTx で binding 確定
        verify(spied, times(1)).bindTenantInNewTx(eq(1), eq(tenant));
        // refresh は呼ばれない (= fresh path)
        verify(mfApiClient, never()).refreshToken(any(), anyString(), anyString());
    }

    // --- ケース 2: NULL + fresh + getTenant 失敗 → MfTenantBindingFailedException ---

    @Test
    void getValidAccessToken_未バインド_fresh_getTenant失敗_BindingFailed例外() {
        MfOauthService spied = org.mockito.Mockito.spy(service);
        ReflectionTestUtils.setField(spied, "self", spied);
        org.mockito.Mockito.doReturn(snapshot(unboundClient, true))
                .when(spied).loadActiveTokenSnapshot();

        // /v2/tenant が 503 等で例外
        when(mfApiClient.getTenant(eq(unboundClient), anyString()))
                .thenThrow(new HttpServerErrorException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(spied::getValidAccessToken)
                .isInstanceOf(MfTenantBindingFailedException.class)
                .hasMessageContaining("clientId=1");

        // bindTenantInNewTx は呼ばれない (= 業務 API 全体が停止)
        verify(spied, never()).bindTenantInNewTx(any(), any());
    }

    @Test
    void getValidAccessToken_未バインド_fresh_MfReAuthRequiredも_BindingFailedにラップされる() {
        // /v2/tenant が 401 → MfReAuthRequiredException (RuntimeException) → wrap される
        MfOauthService spied = org.mockito.Mockito.spy(service);
        ReflectionTestUtils.setField(spied, "self", spied);
        org.mockito.Mockito.doReturn(snapshot(unboundClient, true))
                .when(spied).loadActiveTokenSnapshot();

        when(mfApiClient.getTenant(eq(unboundClient), anyString()))
                .thenThrow(new MfReAuthRequiredException("再認証が必要"));

        assertThatThrownBy(spied::getValidAccessToken)
                .isInstanceOf(MfTenantBindingFailedException.class)
                .hasCauseInstanceOf(MfReAuthRequiredException.class);
    }

    @Test
    void getValidAccessToken_未バインド_fresh_BindingFailed例外はそのまま通す() {
        // 既に MfTenantBindingFailedException が出る場合は wrap せず再 throw する
        MfOauthService spied = org.mockito.Mockito.spy(service);
        ReflectionTestUtils.setField(spied, "self", spied);
        org.mockito.Mockito.doReturn(snapshot(unboundClient, true))
                .when(spied).loadActiveTokenSnapshot();

        MfTenantBindingFailedException original =
                new MfTenantBindingFailedException("既に組み立て済み");
        when(mfApiClient.getTenant(eq(unboundClient), anyString())).thenThrow(original);

        assertThatThrownBy(spied::getValidAccessToken)
                .isSameAs(original);
    }

    // --- ケース 3: バインド済 + fresh → getTenant 呼ばれない ---

    @Test
    void getValidAccessToken_バインド済_fresh_token_getTenantは呼ばれない() {
        MfOauthService spied = org.mockito.Mockito.spy(service);
        ReflectionTestUtils.setField(spied, "self", spied);
        org.mockito.Mockito.doReturn(snapshot(boundClient, true))
                .when(spied).loadActiveTokenSnapshot();

        String token = spied.getValidAccessToken();

        assertThat(token).isEqualTo("plain-access-token");
        // 通常 fresh path: getTenant も refresh も呼ばれない
        verify(mfApiClient, never()).getTenant(any(), anyString());
        verify(mfApiClient, never()).refreshToken(any(), anyString(), anyString());
        verify(spied, never()).bindTenantInNewTx(any(), any());
    }

    // --- ケース 4: NULL + expired → 通常 refresh path ---

    @Test
    void getValidAccessToken_未バインド_expired_token_既存refreshパスでtenant検証() {
        MfOauthService spied = org.mockito.Mockito.spy(service);
        ReflectionTestUtils.setField(spied, "self", spied);
        org.mockito.Mockito.doReturn(snapshot(unboundClient, false))
                .when(spied).loadActiveTokenSnapshot();
        org.mockito.Mockito.doNothing()
                .when(spied).persistRefreshedTokenAndTenant(any(), any(), any());

        MfTokenResponse refreshed = new MfTokenResponse(
                "new-access-token", "new-refresh-token", "Bearer", 3600L, "mfc/admin/tenant.read");
        when(mfApiClient.refreshToken(eq(unboundClient), anyString(), anyString())).thenReturn(refreshed);

        MfTenantResponse tenant = new MfTenantResponse("tenant-new", "New Co");
        when(mfApiClient.getTenant(eq(unboundClient), eq("new-access-token"))).thenReturn(tenant);

        String token = spied.getValidAccessToken();

        // refresh path: 新 access_token を返す
        assertThat(token).isEqualTo("new-access-token");
        // refresh 後の tenant 検証は P1-01 経路 (refresh 済 token で getTenant)
        verify(mfApiClient, times(1)).refreshToken(eq(unboundClient), anyString(), anyString());
        verify(mfApiClient, times(1)).getTenant(eq(unboundClient), eq("new-access-token"));
        // 強制バインドパスは通っていない (= bindTenantInNewTx は呼ばれず persistRefreshedTokenAndTenant が呼ばれる)
        verify(spied, never()).bindTenantInNewTx(any(), any());
        verify(spied, times(1)).persistRefreshedTokenAndTenant(eq(1), eq(refreshed), eq(tenant));
    }

    // --- bindTenantInNewTx 単体テスト ---

    @Test
    void bindTenantInNewTx_未バインドclient_tenant情報を確定する() {
        when(clientRepository.findById(1)).thenReturn(Optional.of(unboundClient));
        when(clientRepository.save(any(MMfOauthClient.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MfTenantResponse tenant = new MfTenantResponse("tenant-x", "Company X");
        service.bindTenantInNewTx(1, tenant);

        // tenant_id / tenant_name / tenant_bound_at が set される
        assertThat(unboundClient.getMfTenantId()).isEqualTo("tenant-x");
        assertThat(unboundClient.getMfTenantName()).isEqualTo("Company X");
        assertThat(unboundClient.getTenantBoundAt()).isNotNull();
        verify(clientRepository, times(1)).save(unboundClient);
    }

    @Test
    void bindTenantInNewTx_既存バインド不一致_MfTenantMismatchException() {
        when(clientRepository.findById(2)).thenReturn(Optional.of(boundClient));

        MfTenantResponse otherTenant = new MfTenantResponse("tenant-other", "Other Co");

        assertThatThrownBy(() -> service.bindTenantInNewTx(2, otherTenant))
                .isInstanceOf(MfTenantMismatchException.class);

        // mismatch では save されない (= rollback)
        verify(clientRepository, never()).save(any());
    }

    @Test
    void bindTenantInNewTx_clientId存在しない_IllegalStateException() {
        when(clientRepository.findById(999)).thenReturn(Optional.empty());

        MfTenantResponse tenant = new MfTenantResponse("tenant-x", "Company X");

        assertThatThrownBy(() -> service.bindTenantInNewTx(999, tenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MF クライアント設定が消えています");
    }
}
