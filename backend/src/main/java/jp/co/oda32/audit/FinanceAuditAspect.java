package jp.co.oda32.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.oda32.annotation.ApplicationType;
import jp.co.oda32.domain.service.data.LoginUser;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * T2: {@link AuditLog} 付き Service メソッドを横断し、呼び出し前後の Entity スナップショットを
 * {@code finance_audit_log} に JSONB で記録する Aspect。
 * <p>
 * <b>web プロファイル限定</b>: バッチ実行では SecurityContext / HttpServletRequest が
 * 取れないため、本 Aspect は web プロファイルのみで起動する (バッチ操作の監査は
 * 将来 Step Listener 等で別途対応)。
 * <p>
 * <b>Transactional 分離</b>: 実書き込みは {@link FinanceAuditWriter#write} に委譲し
 * REQUIRES_NEW で別 tx 化。これにより業務 tx rollback でも失敗操作の証跡が残る。
 * <p>
 * <b>C4 修正 (2026-05-04)</b>: {@link AuditLog#pkExpression()} (SpEL) で複合 PK を
 * {@code Map<String, Object>} として表現できるようにし、{@code target_pk} に投入する。
 * <p>
 * <b>C5 修正 (2026-05-04)</b>: {@link AuditEntityLoaderRegistry} 経由で実 Entity を
 * before/after に再 fetch する。Loader が無い table では before/after は null
 * (引数 JSON は誤誘導を避けるため記録しない)。
 *
 * @since 2026-05-04 (T2)
 */
@Aspect
@Component
@Log4j2
@ApplicationType("web")
public class FinanceAuditAspect {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final FinanceAuditWriter writer;
    private final ObjectProvider<HttpServletRequest> httpRequestProvider;
    private final AuditEntityLoaderRegistry loaderRegistry;
    private final ObjectMapper auditMapper;

    public FinanceAuditAspect(
            FinanceAuditWriter writer,
            ObjectProvider<HttpServletRequest> httpRequestProvider,
            AuditEntityLoaderRegistry loaderRegistry) {
        this.writer = writer;
        this.httpRequestProvider = httpRequestProvider;
        this.loaderRegistry = loaderRegistry;
        // 専用 ObjectMapper: JavaTime対応 + 循環参照例外を出さず空に倒す
        // (@AuditExclude は @JsonIgnore を継承しているため、この mapper でも自動除外される)
        this.auditMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.FAIL_ON_SELF_REFERENCES);
    }

    @Around("@annotation(auditLog)")
    public Object record(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        JsonNode pkJson = computePk(pjp, auditLog);

        Optional<AuditEntityLoader> loaderOpt = loaderRegistry.findByTable(auditLog.table());

        // C5: before snapshot を loader 経由で実 DB row から取得
        JsonNode beforeJson = loadSnapshot(loaderOpt, pkJson, "before");

        AuthInfo authInfo = resolveAuth();
        ReqInfo reqInfo = resolveRequest();
        String argsAux = buildArgsAux(pjp, auditLog);

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable ex) {
            try {
                String reason = composeReason("FAILED: " + ex.getClass().getSimpleName() + ": "
                        + truncate(ex.getMessage(), 600), argsAux);
                writer.write(auditLog.table(), auditLog.operation(),
                        authInfo.userNo, authInfo.actorType,
                        pkJson, beforeJson, null,
                        reason, reqInfo.ip, reqInfo.userAgent);
            } catch (Exception logEx) {
                log.error("[finance-audit] failed-path 監査ログ記録に失敗 (元例外を優先 throw): {}",
                        logEx.toString());
            }
            throw ex;
        }

        // C5: after snapshot を loader 経由で再 fetch (DELETE 後は null になり得る)
        JsonNode afterJson = loadSnapshot(loaderOpt, pkJson, "after");

        // Loader が無い table で returnAsAfter が true の場合のフォールバック
        if (afterJson == null && loaderOpt.isEmpty() && auditLog.captureReturnAsAfter()) {
            afterJson = serialize(result);
        }

        try {
            String reason = composeReason(null, argsAux);
            writer.write(auditLog.table(), auditLog.operation(),
                    authInfo.userNo, authInfo.actorType,
                    pkJson, beforeJson, afterJson,
                    reason, reqInfo.ip, reqInfo.userAgent);
        } catch (Exception logEx) {
            // 監査ログ書込み失敗は業務 tx の成功を巻き戻さない (記録漏れだけ警告)。
            log.error("[finance-audit] success-path 監査ログ記録に失敗: target={}, op={}, err={}",
                    auditLog.table(), auditLog.operation(), logEx.toString());
        }

        return result;
    }

    /**
     * C4: pkExpression (SpEL) または pkArgIndex から PK の JSON を構築する。
     */
    private JsonNode computePk(ProceedingJoinPoint pjp, AuditLog auditLog) {
        // 1) pkExpression が指定されていればそれを最優先 (複合 PK 用)
        String expression = auditLog.pkExpression();
        if (expression != null && !expression.isBlank()) {
            try {
                Object value = evaluatePkExpression(pjp, expression);
                JsonNode json = serialize(value);
                if (json != null) return json;
            } catch (Exception ex) {
                log.warn("[finance-audit] pkExpression 評価に失敗: expr='{}', method={}#{}, err={}",
                        expression,
                        pjp.getSignature().getDeclaringTypeName(),
                        pjp.getSignature().getName(),
                        ex.toString());
            }
            return auditMapper.createObjectNode().put("_pkExpressionError", expression);
        }

        // 2) pkArgIndex が >= 0 なら従来挙動 (単一引数 PK)
        int idx = auditLog.pkArgIndex();
        if (idx >= 0) {
            Object[] args = pjp.getArgs();
            Object pkArg = (args != null && args.length > idx) ? args[idx] : null;
            JsonNode json = serialize(pkArg);
            if (json != null) return json;
        }

        // 3) フォールバック: 空 PK (不明操作 / INSERT 想定)
        return auditMapper.createObjectNode();
    }

    /**
     * C4: SpEL で PK 式を評価する。引数は {@code #a0..#aN} と (debug info があれば) 引数名で参照可能。
     */
    private Object evaluatePkExpression(ProceedingJoinPoint pjp, String expression) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = pjp.getArgs();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                context.setVariable("a" + i, args[i]);
            }
        }
        try {
            String[] paramNames = ((MethodSignature) pjp.getSignature()).getParameterNames();
            if (paramNames != null && args != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    if (paramNames[i] != null) {
                        context.setVariable(paramNames[i], args[i]);
                    }
                }
            }
        } catch (Exception ignored) {
            // 引数名が取れない環境では #a0/#a1 だけで参照する
        }
        Expression expr = SPEL_PARSER.parseExpression(expression);
        return expr.getValue((EvaluationContext) context);
    }

    /**
     * C5: Loader 経由で snapshot を取得する。Loader 未登録または entity 未存在は null。
     */
    private JsonNode loadSnapshot(Optional<AuditEntityLoader> loaderOpt, JsonNode pkJson, String label) {
        if (loaderOpt.isEmpty() || pkJson == null) return null;
        try {
            return loaderOpt.get().loadByPk(pkJson)
                    .map(this::serialize)
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("[finance-audit] {} snapshot 取得に失敗: loader={}, err={}",
                    label, loaderOpt.get().getClass().getSimpleName(), ex.toString());
            return null;
        }
    }

    /**
     * captureArgsAsAfter=true の時に reason 末尾へ補助 args JSON を残す。
     * before/after は実 Entity を入れたいので、この補助情報は reason 列にだけ書く。
     */
    private String buildArgsAux(ProceedingJoinPoint pjp, AuditLog auditLog) {
        if (!auditLog.captureArgsAsAfter()) return null;
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) return null;
        JsonNode json = serialize(args);
        if (json == null) return null;
        return "args=" + truncate(json.toString(), 1500);
    }

    private String composeReason(String head, String argsAux) {
        if (head == null && argsAux == null) return null;
        if (head == null) return argsAux;
        if (argsAux == null) return head;
        return head + " | " + argsAux;
    }

    private AuthInfo resolveAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof LoginUser loginUser) {
            return new AuthInfo(loginUser.getUser().getLoginUserNo(), "USER");
        }
        return new AuthInfo(null, "SYSTEM");
    }

    private ReqInfo resolveRequest() {
        try {
            HttpServletRequest req = httpRequestProvider.getIfAvailable();
            if (req == null) return new ReqInfo(null, null);
            return new ReqInfo(clientIp(req), truncate(req.getHeader("User-Agent"), 500));
        } catch (Exception ignored) {
            return new ReqInfo(null, null);
        }
    }

    private JsonNode serialize(Object obj) {
        if (obj == null) return null;
        try {
            return auditMapper.valueToTree(obj);
        } catch (Exception e) {
            log.warn("[finance-audit] JSON シリアライズに失敗: type={}, err={}",
                    obj.getClass().getName(), e.toString());
            return auditMapper.createObjectNode().put("_serializeError", e.toString());
        }
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return truncate(comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim(), 45);
        }
        return truncate(req.getRemoteAddr(), 45);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record AuthInfo(Integer userNo, String actorType) {}
    private record ReqInfo(String ip, String userAgent) {}
}
