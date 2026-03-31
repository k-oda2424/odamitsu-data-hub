import { use } from 'react'
import { GoodsDetailPage } from '@/components/pages/goods/detail'

export default function GoodsDetail({ params }: { params: Promise<{ goodsNo: string }> }) {
  const { goodsNo } = use(params)
  return <GoodsDetailPage goodsNo={Number(goodsNo)} />
}
