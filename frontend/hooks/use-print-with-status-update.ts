'use client'

import { useCallback, useState } from 'react'
import { flushSync } from 'react-dom'
import { toast } from 'sonner'
import { api } from '@/lib/api-client'
import { getEstimateStatusLabel, getNotifiedStatus } from '@/types/estimate'

/**
 * 見積/比較見積の印刷＋ステータス自動更新共通ロジック。
 *
 * 設計意図:
 * - `window.print()` の戻り値はブラウザによって異なり、ユーザーが印刷ダイアログを
 *   キャンセルしたか否かを確実に判別できない。
 * - よって「印刷ダイアログを開いた時点＝印刷意思あり」とみなしてステータスを更新する。
 * - ConfirmDialog で事前にユーザに明示許可を得ることで、誤更新リスクを抑える。
 *
 * @param currentStatus 現在のステータス（null の場合は自動更新なし）
 * @param onStatusUpdate ステータス更新 API を呼び出す関数（notifiedStatus を受け取る）
 */
export function usePrintWithStatusUpdate(
  currentStatus: string | null | undefined,
  onStatusUpdate: (notifiedStatus: string) => Promise<void>,
) {
  const [pendingNotified, setPendingNotified] = useState<string | null>(null)

  const execute = useCallback(async () => {
    const status = currentStatus ?? null
    const notified = getNotifiedStatus(status)
    // window.print() blocks until the preview closes. flushSync forces the
    // confirm dialog to unmount first so it isn't captured in the preview.
    flushSync(() => setPendingNotified(null))
    window.print()
    if (notified !== null && notified !== status) {
      try {
        await onStatusUpdate(notified)
        toast.success(`ステータスを「${getEstimateStatusLabel(notified)}」に更新しました`)
      } catch {
        toast.error('ステータスの更新に失敗しました')
      }
    }
  }, [currentStatus, onStatusUpdate])

  const trigger = useCallback(() => {
    const status = currentStatus ?? null
    const notified = getNotifiedStatus(status)
    if (notified !== null && notified !== status) {
      setPendingNotified(notified)
      return
    }
    void execute()
  }, [currentStatus, execute])

  return {
    /** 印刷ボタンから呼ぶ。遷移先ステータスが変わるならダイアログを出し、そうでなければ即実行 */
    trigger,
    /** ダイアログの open 判定用（notified が入っていれば表示） */
    pendingNotified,
    /** ダイアログの開閉制御 */
    setPendingNotified,
    /** ダイアログ OK 押下時の実行ハンドラ */
    execute,
  }
}

/**
 * ダイアログに表示する description を生成
 */
export function printConfirmDescription(notifiedStatus: string | null, label = '印刷') {
  if (notifiedStatus === null) return ''
  return `${label}しますか？ステータスが「${getEstimateStatusLabel(notifiedStatus)}」に更新されます。`
}
