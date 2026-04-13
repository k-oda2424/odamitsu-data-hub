import { use } from 'react'
import { PurchasePriceFormPage } from '@/components/pages/purchase-price/form'

export default function Page({ params }: { params: Promise<{ purchasePriceNo: string }> }) {
  const { purchasePriceNo } = use(params)
  return <PurchasePriceFormPage purchasePriceNo={Number(purchasePriceNo)} />
}
