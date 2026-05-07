package jp.co.oda32.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T2: {@link FinanceAuditAspect} の単体テスト。
 * ProceedingJoinPoint をモックし、success / failed パスで Writer が呼ばれることを確認する。
 * <p>
 * C4/C5 修正後 (2026-05-04):
 * <ul>
 *   <li>{@code pkExpression} (SpEL) で複合 PK を {@code Map} として組み立てられる</li>
 *   <li>{@link AuditEntityLoader} 登録 table は before/after が実 entity (テスト内では文字列) になる</li>
 *   <li>Loader 未登録 table は before=null、after は {@code captureReturnAsAfter} のみフォールバック</li>
 * </ul>
 */
class FinanceAuditAspectTest {

    private FinanceAuditWriter writer;
    private FinanceAuditAspect aspect;
    private AuditLog annotation;

    @BeforeEach
    void setUp() {
        writer = mock(FinanceAuditWriter.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<jakarta.servlet.http.HttpServletRequest> provider =
                (ObjectProvider<jakarta.servlet.http.HttpServletRequest>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AuditEntityLoaderRegistry registry = new AuditEntityLoaderRegistry(Collections.emptyList());
        aspect = new FinanceAuditAspect(writer, provider, registry);
        SecurityContextHolder.clearContext();
        annotation = annotation("t_test", "verify", true, false, "", 0);
    }

    @Test
    void success_path_writes_audit_log_with_after_values_when_no_loader() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{ 42 });
        when(pjp.proceed()).thenReturn("OK");

        Object result = aspect.record(pjp, annotation);

        assertThat(result).isEqualTo("OK");
        ArgumentCaptor<JsonNode> after = ArgumentCaptor.forClass(JsonNode.class);
        // Loader 未登録 → before=null, after=戻り値 (captureReturnAsAfter=true のフォールバック)
        verify(writer).write(eq("t_test"), eq("verify"), isNull(), eq("SYSTEM"),
                any(JsonNode.class), isNull(), after.capture(), isNull(), isNull(), isNull());
        assertThat(after.getValue().asText()).isEqualTo("OK");
    }

    @Test
    void failed_path_writes_audit_log_with_failed_reason_and_rethrows() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{ 42 });
        RuntimeException boom = new RuntimeException("ng");
        when(pjp.proceed()).thenThrow(boom);

        assertThatThrownBy(() -> aspect.record(pjp, annotation))
                .isSameAs(boom);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(writer).write(eq("t_test"), eq("verify"), isNull(), eq("SYSTEM"),
                any(JsonNode.class), isNull(), isNull(), reason.capture(), isNull(), isNull());
        assertThat(reason.getValue()).startsWith("FAILED:").contains("ng");
    }

    @Test
    void writer_failure_does_not_break_business_call() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{ 1 });
        when(pjp.proceed()).thenReturn("OK");
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(writer).write(anyString(), anyString(), any(), anyString(),
                        any(), any(), any(), any(), any(), any());

        // 業務 tx 成功時の監査ログ書込み失敗は元処理を巻き戻さない (warn ログのみ)
        Object result = aspect.record(pjp, annotation);
        assertThat(result).isEqualTo("OK");
    }

    /**
     * C4: pkExpression (SpEL) で複合 PK を Map として組み立てられることを確認。
     */
    @Test
    void composite_pk_via_pkExpression_serialized_as_map() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{ 1, 123, "2026-04-20", "10" });
        when(pjp.proceed()).thenReturn("OK");
        // SpEL のメソッド名引数解決を回避するため、MethodSignature を mock して getParameterNames を返さないようにする
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getDeclaringTypeName()).thenReturn("TestService");
        when(sig.getName()).thenReturn("verify");
        when(sig.getParameterNames()).thenReturn(null);
        when(pjp.getSignature()).thenReturn((Signature) sig);

        AuditLog ann = annotation("t_test", "verify", true, false,
                "{'shopNo': #a0, 'supplierNo': #a1, 'transactionMonth': #a2, 'taxRate': #a3}", -1);

        aspect.record(pjp, ann);

        ArgumentCaptor<JsonNode> pk = ArgumentCaptor.forClass(JsonNode.class);
        verify(writer).write(eq("t_test"), eq("verify"), isNull(), eq("SYSTEM"),
                pk.capture(), isNull(), any(JsonNode.class), isNull(), isNull(), isNull());
        JsonNode pkJson = pk.getValue();
        assertThat(pkJson.get("shopNo").asInt()).isEqualTo(1);
        assertThat(pkJson.get("supplierNo").asInt()).isEqualTo(123);
        assertThat(pkJson.get("transactionMonth").asText()).isEqualTo("2026-04-20");
        assertThat(pkJson.get("taxRate").asText()).isEqualTo("10");
    }

    /**
     * C5: AuditEntityLoader が登録されている table では before/after が loader 経由で取得される。
     */
    @Test
    void loader_provides_before_and_after_snapshots() throws Throwable {
        // before "BEFORE", after "AFTER" を返す stub loader
        AuditEntityLoader stubLoader = new AuditEntityLoader() {
            int call = 0;
            @Override public String table() { return "t_loaded"; }
            @Override public Optional<Object> loadByPk(JsonNode pkJson) {
                call++;
                return Optional.of(call == 1 ? "BEFORE" : "AFTER");
            }
        };
        AuditEntityLoaderRegistry registry = new AuditEntityLoaderRegistry(List.of(stubLoader));
        @SuppressWarnings("unchecked")
        ObjectProvider<jakarta.servlet.http.HttpServletRequest> provider =
                (ObjectProvider<jakarta.servlet.http.HttpServletRequest>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FinanceAuditAspect aspectWithLoader = new FinanceAuditAspect(writer, provider, registry);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{ 42 });
        when(pjp.proceed()).thenReturn("RESULT");

        AuditLog ann = annotation("t_loaded", "verify", true, false, "", 0);
        aspectWithLoader.record(pjp, ann);

        ArgumentCaptor<JsonNode> before = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> after = ArgumentCaptor.forClass(JsonNode.class);
        verify(writer).write(eq("t_loaded"), eq("verify"), isNull(), eq("SYSTEM"),
                any(JsonNode.class), before.capture(), after.capture(), isNull(), isNull(), isNull());
        assertThat(before.getValue().asText()).isEqualTo("BEFORE");
        // Loader が after も返すので、戻り値 "RESULT" ではなく Loader の "AFTER" が記録される
        assertThat(after.getValue().asText()).isEqualTo("AFTER");
    }

    /**
     * G3-M12-fix: Loader 登録あり + PK shape mismatch (loadByPk が空) のとき、
     * captureReturnAsAfter=true なら戻り値を after にフォールバックする。
     * <p>
     * 想定ユースケース: {@code PaymentMfImportService.applyVerification} の
     * {@code pkExpression="{uploadId, userNo, force}"} と
     * {@code TAccountsPayableSummaryAuditLoader} の {@code {shopNo, supplierNo, ...}} の不一致。
     */
    @Test
    void loader_pk_mismatch_falls_back_to_return_value_for_after() throws Throwable {
        AuditEntityLoader emptyLoader = new AuditEntityLoader() {
            @Override public String table() { return "t_loaded"; }
            @Override public Optional<Object> loadByPk(JsonNode pkJson) { return Optional.empty(); }
        };
        AuditEntityLoaderRegistry registry = new AuditEntityLoaderRegistry(List.of(emptyLoader));
        @SuppressWarnings("unchecked")
        ObjectProvider<jakarta.servlet.http.HttpServletRequest> provider =
                (ObjectProvider<jakarta.servlet.http.HttpServletRequest>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FinanceAuditAspect aspectWithLoader = new FinanceAuditAspect(writer, provider, registry);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{ "uploadId-1", 99, true });
        when(pjp.proceed()).thenReturn("VERIFY_RESULT");

        AuditLog ann = annotation("t_loaded", "verify", true, false, "", 0);
        aspectWithLoader.record(pjp, ann);

        ArgumentCaptor<JsonNode> after = ArgumentCaptor.forClass(JsonNode.class);
        verify(writer).write(eq("t_loaded"), eq("verify"), isNull(), eq("SYSTEM"),
                any(JsonNode.class), isNull(), after.capture(), isNull(), isNull(), isNull());
        // Loader 登録あり + loadByPk が空 → captureReturnAsAfter=true で戻り値が after に入る
        assertThat(after.getValue().asText()).isEqualTo("VERIFY_RESULT");
    }

    /**
     * G3-M12-fix: Loader 登録あり + PK shape mismatch + captureReturnAsAfter=false のとき、
     * captureArgsAsAfter=true なら引数を after にフォールバックする。
     */
    @Test
    void loader_pk_mismatch_falls_back_to_args_when_return_disabled() throws Throwable {
        AuditEntityLoader emptyLoader = new AuditEntityLoader() {
            @Override public String table() { return "t_loaded"; }
            @Override public Optional<Object> loadByPk(JsonNode pkJson) { return Optional.empty(); }
        };
        AuditEntityLoaderRegistry registry = new AuditEntityLoaderRegistry(List.of(emptyLoader));
        @SuppressWarnings("unchecked")
        ObjectProvider<jakarta.servlet.http.HttpServletRequest> provider =
                (ObjectProvider<jakarta.servlet.http.HttpServletRequest>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FinanceAuditAspect aspectWithLoader = new FinanceAuditAspect(writer, provider, registry);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{ "uploadId-1", 99, true });
        when(pjp.proceed()).thenReturn("IGNORED");

        // captureReturnAsAfter=false, captureArgsAsAfter=true
        AuditLog ann = annotation("t_loaded", "verify", false, true, "", 0);
        aspectWithLoader.record(pjp, ann);

        ArgumentCaptor<JsonNode> after = ArgumentCaptor.forClass(JsonNode.class);
        verify(writer).write(eq("t_loaded"), eq("verify"), isNull(), eq("SYSTEM"),
                any(JsonNode.class), isNull(), after.capture(),
                anyString(), isNull(), isNull());
        JsonNode afterArgs = after.getValue();
        assertThat(afterArgs.isArray()).isTrue();
        assertThat(afterArgs.get(0).asText()).isEqualTo("uploadId-1");
        assertThat(afterArgs.get(1).asInt()).isEqualTo(99);
        assertThat(afterArgs.get(2).asBoolean()).isTrue();
    }

    /**
     * G3-M12-fix: Loader 登録あり + PK 解決成功時は entity snapshot を優先 (= 既存挙動を破壊しない)。
     */
    @Test
    void loader_pk_resolved_keeps_entity_snapshot_priority() throws Throwable {
        AuditEntityLoader stubLoader = new AuditEntityLoader() {
            int call = 0;
            @Override public String table() { return "t_loaded"; }
            @Override public Optional<Object> loadByPk(JsonNode pkJson) {
                call++;
                return Optional.of(call == 1 ? "BEFORE_ENTITY" : "AFTER_ENTITY");
            }
        };
        AuditEntityLoaderRegistry registry = new AuditEntityLoaderRegistry(List.of(stubLoader));
        @SuppressWarnings("unchecked")
        ObjectProvider<jakarta.servlet.http.HttpServletRequest> provider =
                (ObjectProvider<jakarta.servlet.http.HttpServletRequest>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        FinanceAuditAspect aspectWithLoader = new FinanceAuditAspect(writer, provider, registry);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{ 1 });
        when(pjp.proceed()).thenReturn("RETURN_VALUE");

        // captureReturnAsAfter=true でも、Loader が AFTER_ENTITY を返す限りそちらが優先される
        AuditLog ann = annotation("t_loaded", "verify", true, true, "", 0);
        aspectWithLoader.record(pjp, ann);

        ArgumentCaptor<JsonNode> after = ArgumentCaptor.forClass(JsonNode.class);
        verify(writer).write(eq("t_loaded"), eq("verify"), isNull(), eq("SYSTEM"),
                any(JsonNode.class), any(JsonNode.class), after.capture(),
                anyString(), isNull(), isNull());
        // Loader が成功するのでフォールバックは発火せず、entity snapshot がそのまま入る
        assertThat(after.getValue().asText()).isEqualTo("AFTER_ENTITY");
    }

    private static AuditLog annotation(String table, String operation,
                                        boolean captureReturn, boolean captureArgs,
                                        String pkExpression, int pkArgIndex) {
        return new AuditLog() {
            @Override public Class<? extends Annotation> annotationType() { return AuditLog.class; }
            @Override public String table() { return table; }
            @Override public String operation() { return operation; }
            @Override public int pkArgIndex() { return pkArgIndex; }
            @Override public String pkExpression() { return pkExpression; }
            @Override public boolean captureReturnAsAfter() { return captureReturn; }
            @Override public boolean captureArgsAsAfter() { return captureArgs; }
        };
    }
}
