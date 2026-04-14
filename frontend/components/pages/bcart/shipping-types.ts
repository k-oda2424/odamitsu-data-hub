import type { BCartShipmentStatus } from '@/types/bcart-shipping'

/** UIで編集可能なフィールドのdirty差分 */
export interface RowPatch {
  deliveryCode?: string
  shipmentDate?: string
  memo?: string
  adminMessage?: string
  shipmentStatus?: BCartShipmentStatus
}

/** サーバー行 + dirty差分をマージしたビュー（描画専用） */
export interface MergedRow {
  bCartLogisticsId: number
  partnerCode: string | null
  partnerName: string | null
  deliveryCompName: string | null
  goodsInfo: string[]
  smileSerialNoList: number[]
  bCartCsvExported: boolean
  deliveryCode: string
  shipmentDate: string
  memo: string
  adminMessage: string
  shipmentStatus: BCartShipmentStatus
  dirty: boolean
  adminMessageDirty: boolean
}

export function isRowLocked(row: Pick<MergedRow, 'bCartCsvExported' | 'shipmentStatus'>): boolean {
  return row.bCartCsvExported && row.shipmentStatus === '発送済'
}
