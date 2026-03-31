import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import type { Maker, Supplier, Shop } from '@/types/goods'
import type { PartnerResponse, DeliveryDestinationResponse } from '@/types/partner-goods'

const MASTER_STALE_TIME = 5 * 60 * 1000 // 5分

export function useMakers() {
  return useQuery({
    queryKey: ['makers'],
    queryFn: () => api.get<Maker[]>('/masters/makers'),
    staleTime: MASTER_STALE_TIME,
  })
}

export function useShops(enabled = true) {
  return useQuery({
    queryKey: ['shops'],
    queryFn: () => api.get<Shop[]>('/masters/shops'),
    staleTime: MASTER_STALE_TIME,
    enabled,
  })
}

export function useSuppliers(shopNo: string | number | undefined) {
  return useQuery({
    queryKey: ['suppliers', shopNo],
    queryFn: () => api.get<Supplier[]>(`/masters/suppliers?shopNo=${shopNo}`),
    enabled: !!shopNo,
    staleTime: MASTER_STALE_TIME,
  })
}

export function usePartners(shopNo: string | number | undefined) {
  return useQuery({
    queryKey: ['partners', shopNo],
    queryFn: () => api.get<PartnerResponse[]>(`/masters/partners?shopNo=${shopNo}`),
    enabled: !!shopNo,
    staleTime: MASTER_STALE_TIME,
  })
}

export function useDestinations(partnerNo: string | number | undefined) {
  return useQuery({
    queryKey: ['destinations', partnerNo],
    queryFn: () => api.get<DeliveryDestinationResponse[]>(`/masters/destinations?partnerNo=${partnerNo}`),
    enabled: !!partnerNo,
    staleTime: MASTER_STALE_TIME,
  })
}
