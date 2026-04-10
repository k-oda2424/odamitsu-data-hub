import { use } from 'react'
import BCartProductDetailPage from '@/components/pages/bcart/product-detail'

export default function Page({ params }: { params: Promise<{ productId: string }> }) {
  const { productId } = use(params)
  return <BCartProductDetailPage productId={Number(productId)} />
}
