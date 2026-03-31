import { use } from 'react'
import { PartnerGoodsDetailPage } from '@/components/pages/partner-goods/detail'

export default function PartnerGoodsDetail({
  params,
}: {
  params: Promise<{ partnerNo: string; destinationNo: string; goodsNo: string }>
}) {
  const { partnerNo, destinationNo, goodsNo } = use(params)
  return (
    <PartnerGoodsDetailPage
      partnerNo={Number(partnerNo)}
      destinationNo={Number(destinationNo)}
      goodsNo={Number(goodsNo)}
    />
  )
}
