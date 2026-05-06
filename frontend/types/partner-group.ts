export interface PartnerGroup {
  partnerGroupId: number
  groupName: string
  shopNo: number
  partnerCodes: string[]
}

/**
 * 入金グループの作成 / 更新リクエスト DTO。
 * バックエンド `PartnerGroupRequest` と対応。
 */
export interface PartnerGroupRequest {
  groupName: string
  shopNo: number
  partnerCodes: string[]
}

/**
 * `PartnerGroupDialog` 内 `saveMutation` 用。
 * 新規作成時は id を省略、更新時に id を指定。
 */
export interface PartnerGroupSavePayload extends PartnerGroupRequest {
  id?: number
}
