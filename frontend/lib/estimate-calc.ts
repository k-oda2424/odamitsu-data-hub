export function calcProfit(goodsPrice: number | null, purchasePrice: number | null): number | null {
  if (goodsPrice == null || purchasePrice == null) return null
  return goodsPrice - purchasePrice
}

export function calcProfitRate(goodsPrice: number | null, purchasePrice: number | null): number | null {
  if (goodsPrice == null || purchasePrice == null || goodsPrice <= 0) return null
  return Math.round((1 - purchasePrice / goodsPrice) * 1000) / 10
}

export function calcCaseProfit(
  goodsPrice: number | null,
  purchasePrice: number | null,
  containNum: number | null,
): number | null {
  const profit = calcProfit(goodsPrice, purchasePrice)
  if (profit == null || containNum == null) return null
  return profit * containNum
}

export function calcTotalProfit(
  goodsPrice: number | null,
  purchasePrice: number | null,
  containNum: number | null,
  qty: number | null,
): number | null {
  const caseProfit = calcCaseProfit(goodsPrice, purchasePrice, containNum)
  if (caseProfit == null || qty == null) return null
  return caseProfit * qty
}

export function fmt(val: number | null | undefined): string {
  if (val == null) return '-'
  return new Intl.NumberFormat('ja-JP', { maximumFractionDigits: 2 }).format(val)
}

export function fmtRate(val: number | null | undefined): string {
  if (val == null) return '-'
  return `${val.toFixed(1)}%`
}

/**
 * 印刷・PDF出力時に得意先名から括弧（全角／半角）で括られた箇所を除去する。
 * 例: 「ＢＡＫＥ　ベルゲン瀬戸店（原）」→「ＢＡＫＥ　ベルゲン瀬戸店」
 */
export function stripPrintParens(name: string | null | undefined): string {
  if (!name) return ''
  return name.replace(/[（(][^）)]*[）)]/g, '').replace(/[\s\u3000]+$/, '')
}
