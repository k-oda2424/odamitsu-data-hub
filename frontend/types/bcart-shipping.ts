export const BCART_SHIPMENT_STATUSES = ['未発送', '発送指示', '発送済', '対象外'] as const
export type BCartShipmentStatus = (typeof BCART_SHIPMENT_STATUSES)[number]

export interface BCartShippingInputResponse {
  bCartLogisticsId: number
  partnerCode: string | null
  partnerName: string | null
  deliveryCompName: string | null
  deliveryCode: string | null
  shipmentDate: string | null
  memo: string | null
  adminMessage: string | null
  shipmentStatus: BCartShipmentStatus
  goodsInfo: string[]
  /** SMILE連携後の処理連番 (psn_updated=true の行のみ) */
  smileSerialNoList: number[]
  bCartCsvExported: boolean
}

export interface BCartShippingUpdateRequest {
  bCartLogisticsId: number
  deliveryCode: string
  /** YYYY-MM-DD, 空文字は null として送信 */
  shipmentDate: string | null
  memo: string
  /** 編集されていない場合は null を送信（バックエンドで adminMessage の上書きをスキップ） */
  adminMessage: string | null
  shipmentStatus: BCartShipmentStatus
}

export interface BCartShippingBulkStatusRequest {
  bCartLogisticsIds: number[]
  shipmentStatus: BCartShipmentStatus
}

export const BCART_SHIPMENT_STATUS_OPTIONS: Array<{ value: BCartShipmentStatus; label: string }> = [
  { value: '未発送', label: '未発送' },
  { value: '発送指示', label: '発送指示' },
  { value: '発送済', label: '発送済' },
  { value: '対象外', label: '対象外' },
]

/** 検索用: 「全て」含むフィルタ値 */
export const BCART_SHIPMENT_FILTER_ALL = 'ALL' as const

export const BCART_SHIPMENT_FILTER_OPTIONS: Array<{ value: BCartShipmentStatus | typeof BCART_SHIPMENT_FILTER_ALL; label: string }> = [
  { value: BCART_SHIPMENT_FILTER_ALL, label: '全て' },
  { value: '未発送', label: '未発送' },
  { value: '発送指示', label: '発送指示' },
  { value: '発送済', label: '発送済' },
  { value: '対象外', label: '対象外' },
]
