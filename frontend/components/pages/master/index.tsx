'use client'

/**
 * マスタ管理ページ（旧タブ構成）
 *
 * 現在は各マスタが個別ページとして実装されているため、
 * このコンポーネントは使用されていません。
 * - メーカー: /masters/makers (makers.tsx)
 * - 倉庫: /masters/warehouses (warehouses.tsx)
 * - 仕入先: /masters/suppliers (suppliers.tsx)
 * - 得意先: /masters/partners (partners.tsx)
 *
 * /masters へのアクセスは app/(authenticated)/masters/page.tsx で
 * /masters/makers にリダイレクトされます。
 */
export function MasterPage() {
  return null
}
