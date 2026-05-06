package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.exception.FinanceBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MfOauthHostAllowlist} の単体テスト (G1-M5)。
 * <p>
 * production profile (= "dev"/"test" を含まない) と dev profile の両方をカバーし、
 * 攻撃者制御ホストへの client_secret 流出経路が遮断されることを検証する。
 *
 * @since 2026-05-06 (G1-M5)
 */
class MfOauthHostAllowlistTest {

    @Nested
    @DisplayName("production profile")
    class ProdProfile {

        private final MfOauthHostAllowlist sut = new MfOauthHostAllowlist("prod");

        @Test
        @DisplayName("MF 公式ホストは https で許可される")
        void 本番ホストOK() {
            sut.validate("https://api.biz.moneyforward.com/authorize", "authorizeUrl");
            sut.validate("https://api.biz.moneyforward.com/token", "tokenUrl");
            sut.validate("https://api-accounting.moneyforward.com", "apiBaseUrl");
            sut.validate("https://api-accounting.moneyforward.com/api/v3/accounts", "apiBaseUrl");
        }

        @Test
        @DisplayName("攻撃者制御ホストは拒否される (credential exfiltration 対策)")
        void 攻撃者ホスト拒否() {
            assertThatThrownBy(() -> sut.validate("https://attacker.example.com/token", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class)
                    .hasMessageContaining("tokenUrl")
                    .hasMessageContaining("attacker.example.com");
        }

        @Test
        @DisplayName("prod では localhost も拒否される")
        void localhost拒否() {
            assertThatThrownBy(() -> sut.validate("http://localhost:8080/token", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
            assertThatThrownBy(() -> sut.validate("https://localhost:8080/token", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
            assertThatThrownBy(() -> sut.validate("http://127.0.0.1/token", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
        }

        @Test
        @DisplayName("MF 公式ホストでも http は拒否される")
        void 本番ホストでもhttp拒否() {
            assertThatThrownBy(() -> sut.validate("http://api.biz.moneyforward.com/token", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class)
                    .hasMessageContaining("https");
        }

        @Test
        @DisplayName("エラーコードは MF_HOST_NOT_ALLOWED")
        void エラーコード確認() {
            assertThatThrownBy(() -> sut.validate("https://attacker.example.com/", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class)
                    .extracting(e -> ((FinanceBusinessException) e).getErrorCode())
                    .isEqualTo(MfOauthHostAllowlist.ERROR_CODE);
        }
    }

    @Nested
    @DisplayName("dev profile")
    class DevProfile {

        private final MfOauthHostAllowlist sut = new MfOauthHostAllowlist("web,dev");

        @Test
        @DisplayName("localhost は http/https どちらも許可される (mock OAuth サーバー想定)")
        void localhost許可_http可() {
            sut.validate("http://localhost:8080/token", "tokenUrl");
            sut.validate("https://localhost:8443/token", "tokenUrl");
            sut.validate("http://127.0.0.1:9000/authorize", "authorizeUrl");
            sut.validate("https://127.0.0.1:9000/authorize", "authorizeUrl");
        }

        @Test
        @DisplayName("dev でも MF 公式ホストは引き続き許可")
        void 本番ホストもOK() {
            sut.validate("https://api.biz.moneyforward.com/authorize", "authorizeUrl");
            sut.validate("https://api-accounting.moneyforward.com/api/v3", "apiBaseUrl");
        }

        @Test
        @DisplayName("dev でも未許可ホストは拒否")
        void 未許可ホスト拒否() {
            assertThatThrownBy(() -> sut.validate("https://attacker.example.com/token", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
        }

        @Test
        @DisplayName("test profile も dev と同じ扱い")
        void test_profileもlocalhost許可() {
            MfOauthHostAllowlist testSut = new MfOauthHostAllowlist("test");
            testSut.validate("http://localhost:8080/token", "tokenUrl");
        }
    }

    @Nested
    @DisplayName("scheme / 入力検証")
    class SchemeAndInput {

        private final MfOauthHostAllowlist devSut = new MfOauthHostAllowlist("dev");

        @Test
        @DisplayName("http は localhost 以外 (公式ホスト含む) では拒否")
        void http_localhost以外は拒否() {
            assertThatThrownBy(() -> devSut.validate("http://api.biz.moneyforward.com/token", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class)
                    .hasMessageContaining("https");
        }

        @Test
        @DisplayName("不正 URL / null / 空文字は拒否")
        void 不正URL拒否() {
            assertThatThrownBy(() -> devSut.validate("not-a-url", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
            assertThatThrownBy(() -> devSut.validate(null, "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class)
                    .hasMessageContaining("必須");
            assertThatThrownBy(() -> devSut.validate("", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
            assertThatThrownBy(() -> devSut.validate("   ", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
        }

        @Test
        @DisplayName("不正 scheme (ftp/file 等) は拒否")
        void 不正scheme拒否() {
            assertThatThrownBy(() -> devSut.validate("ftp://api.biz.moneyforward.com/", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
            assertThatThrownBy(() -> devSut.validate("file:///etc/passwd", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
        }

        @Test
        @DisplayName("空 profile (= 未設定 / prod 相当) では localhost も拒否")
        void 空profileはprod扱い() {
            MfOauthHostAllowlist emptySut = new MfOauthHostAllowlist("");
            emptySut.validate("https://api.biz.moneyforward.com/", "apiBaseUrl");
            assertThatThrownBy(() -> emptySut.validate("http://localhost:8080/", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
        }

        @Test
        @DisplayName("エラーメッセージにフィールド名が含まれる")
        void エラーメッセージにフィールド名() {
            assertThatThrownBy(() -> devSut.validate("https://attacker.example.com/", "authorizeUrl"))
                    .isInstanceOf(FinanceBusinessException.class)
                    .hasMessageContaining("authorizeUrl");
            assertThatThrownBy(() -> devSut.validate("https://attacker.example.com/", "apiBaseUrl"))
                    .isInstanceOf(FinanceBusinessException.class)
                    .hasMessageContaining("apiBaseUrl");
        }

        @Test
        @DisplayName("ホストはサブドメイン完全一致 (substring 攻撃を防ぐ)")
        void サブドメイン攻撃防御() {
            // attacker.api.biz.moneyforward.com.evil.com 等を防ぐ
            assertThatThrownBy(() -> devSut.validate("https://api.biz.moneyforward.com.evil.com/", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
            assertThatThrownBy(() -> devSut.validate("https://evil-api.biz.moneyforward.com/", "tokenUrl"))
                    .isInstanceOf(FinanceBusinessException.class);
        }

        @Test
        @DisplayName("URL エラーコードは一貫して MF_HOST_NOT_ALLOWED")
        void エラーコード一貫性() {
            for (String invalid : new String[]{
                    null, "", "not-a-url", "ftp://localhost/",
                    "https://attacker.example.com/", "http://api.biz.moneyforward.com/"}) {
                FinanceBusinessException ex = catchFinanceBusiness(() -> devSut.validate(invalid, "tokenUrl"));
                assertThat(ex.getErrorCode()).isEqualTo(MfOauthHostAllowlist.ERROR_CODE);
            }
        }

        private FinanceBusinessException catchFinanceBusiness(Runnable r) {
            try {
                r.run();
            } catch (FinanceBusinessException ex) {
                return ex;
            }
            throw new AssertionError("FinanceBusinessException が throw されませんでした");
        }
    }
}
