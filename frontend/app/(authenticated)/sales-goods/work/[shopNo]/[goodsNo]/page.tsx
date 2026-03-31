import { use } from 'react'
import { SalesGoodsDetailPage } from '@/components/pages/sales-goods/detail'

export default function SalesGoodsWorkDetail({
  params,
}: {
  params: Promise<{ shopNo: string; goodsNo: string }>
}) {
  const { shopNo, goodsNo } = use(params)
  return (
    <SalesGoodsDetailPage
      shopNo={Number(shopNo)}
      goodsNo={Number(goodsNo)}
      isWork={true}
    />
  )
}
