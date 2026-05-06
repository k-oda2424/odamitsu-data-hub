import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDate(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date
  return d.toLocaleDateString('ja-JP', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

export function formatNumber(num: number): string {
  return new Intl.NumberFormat('ja-JP').format(num)
}

export function formatDateJP(dateStr: string | null): string {
  if (!dateStr) return '-'
  const [y, m, d] = dateStr.split('-').map(Number)
  return `${y}年${m}月${d}日`
}

export function formatCurrency(num: number): string {
  return new Intl.NumberFormat('ja-JP', { style: 'currency', currency: 'JPY' }).format(num)
}

/**
 * 検索用にNFKC正規化を行います。全角英数→半角、半角カナ→全角カナに統一されます。
 * バックエンド側 StringUtil.normalizeForSearch() と同じ正規化です。
 */
export function normalizeForSearch(str: string): string {
  return str.normalize('NFKC')
}

/**
 * 金額入力を正規化して数値化する。
 * - 前後の空白を除去
 * - 半角/全角カンマを除去
 * - 全角数字 (０-９) と全角マイナス (－/−) を半角化
 * - 空文字は 0 を返す
 * - パース不能 (NaN) は null を返す (呼び出し側で `null` チェックして UX 制御)
 */
export function parseAmount(input: string | number | null | undefined): number | null {
  if (input === null || input === undefined) return null
  if (typeof input === 'number') return Number.isFinite(input) ? input : null
  const trimmed = input.trim()
  if (trimmed === '') return 0
  const halfWidth = trimmed
    .replace(/[,，]/g, '')
    .replace(/[０-９]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0xfee0))
    .replace(/[－−ー]/g, '-')
  const n = Number(halfWidth)
  return Number.isFinite(n) ? n : null
}
