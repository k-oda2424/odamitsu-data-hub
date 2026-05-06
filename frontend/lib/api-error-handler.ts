import { toast } from 'sonner'
import { ApiError } from './api-client'

/**
 * G3-M12 (2026-05-06): API エラーの toast 表示を統一する共通ハンドラ。
 *
 * 主要な business error code に対しては固有の文言・誘導 (再認可ボタン等) を出し、
 * 未知の code は status code に応じた variant で {@code error.message} を出す。
 *
 * 使い方 (mutation onError 例):
 * <pre>{@code
 * useMutation({
 *   mutationFn: ...,
 *   onError: (e) => handleApiError(e, { fallbackMessage: '反映失敗' }),
 * })
 * }</pre>
 *
 * 使い方 (try/catch 例):
 * <pre>{@code
 * try { await api.post(...) }
 * catch (e) { handleApiError(e) }
 * }</pre>
 *
 * @returns 既知 code として扱った場合 true、未知 code / 非 ApiError は false
 */
export function handleApiError(
  error: unknown,
  options?: { fallbackMessage?: string },
): boolean {
  if (!(error instanceof ApiError)) {
    // 非 ApiError (network 切断等) は fallback message。
    const msg = error instanceof Error
      ? error.message
      : (options?.fallbackMessage ?? '予期せぬエラーが発生しました')
    toast.error(options?.fallbackMessage ?? '予期せぬエラーが発生しました', {
      description: msg !== options?.fallbackMessage ? msg : undefined,
    })
    return false
  }

  switch (error.code) {
    // 買掛仕入 MF 振込明細の per-supplier 1 円不一致。force=true で突破可能。
    case 'PER_SUPPLIER_MISMATCH':
      toast.error(error.message, {
        description:
          'per-supplier 1 円整合性違反があります。Excel を修正するか、業務承認のうえ「強制反映」してください。',
      })
      return true

    // MF tenant 不一致 (別会社誤接続)。切断 → 再認可が必要。
    case 'MF_TENANT_MISMATCH':
      toast.error('MF テナントが一致しません', {
        description:
          '別会社の MF へ誤認可された可能性があります。MF 連携を一旦切断してから再接続してください。',
        action: {
          label: 'MF 連携設定',
          onClick: () => {
            window.location.href = '/finance/mf-integration'
          },
        },
      })
      return true

    // tenant binding 失敗 (短期再試行可能)。
    case 'MF_TENANT_BINDING_FAILED':
      toast.error('MF テナント情報の取得に失敗しました', {
        description:
          '一時的な MF API 障害か、再認可が必要な可能性があります。MF 連携設定から再接続してください。',
        action: {
          label: '再認可',
          onClick: () => {
            window.location.href = '/finance/mf-integration'
          },
        },
      })
      return true

    // OAuth 連携先ホストの allowlist 違反。
    case 'MF_HOST_NOT_ALLOWED':
      toast.error('MF OAuth エンドポイントが許可されていません', {
        description: error.message,
      })
      return true

    // MF refresh_token 失効など、ユーザー再認可が必要。
    case 'MF_REAUTH_REQUIRED':
      toast.error('MF 再認可が必要です', {
        description: error.message,
        action: {
          label: '再認可',
          onClick: () => {
            window.location.href = '/finance/mf-integration'
          },
        },
      })
      return true

    // MF API scope 不足。
    case 'MF_SCOPE_INSUFFICIENT':
      toast.error('MF API のスコープが不足しています', {
        description: error.message,
        action: {
          label: '再認可',
          onClick: () => {
            window.location.href = '/finance/mf-integration'
          },
        },
      })
      return true

    default:
      // 既知 code 外: backend message をそのまま表示 (status code に応じた variant)
      if (error.status >= 500) {
        toast.error('サーバーエラー', { description: error.message })
      } else if (error.status === 401 || error.status === 403) {
        toast.error('権限エラー', { description: error.message })
      } else {
        toast.error(error.message)
      }
      return false
  }
}
