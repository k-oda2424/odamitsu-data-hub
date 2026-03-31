import { z } from 'zod'

export const goodsFormSchema = z.object({
  goodsName: z.string().min(1, '商品名は必須です'),
  janCode: z.string(),
  makerNo: z.string(),
  caseContainNum: z.string(),
  specification: z.string(),
  keyword: z.string(),
  applyReducedTaxRate: z.boolean(),
})

export type GoodsFormValues = z.infer<typeof goodsFormSchema>

export const salesGoodsFormSchema = z.object({
  goodsCode: z.string().min(1, '商品コードは必須です'),
  goodsSkuCode: z.string(),
  goodsName: z.string().min(1, '商品名は必須です'),
  keyword: z.string(),
  supplierNo: z.string().min(1, '仕入先は必須です'),
  referencePrice: z.string(),
  purchasePrice: z.string().min(1, '標準仕入単価は必須です'),
  goodsPrice: z.string().min(1, '標準売単価は必須です'),
  catchphrase: z.string(),
  goodsIntroduction: z.string(),
  goodsDescription1: z.string(),
  goodsDescription2: z.string(),
})

export type SalesGoodsFormValues = z.infer<typeof salesGoodsFormSchema>
