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
