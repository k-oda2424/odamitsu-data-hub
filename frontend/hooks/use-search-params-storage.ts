import { useState, useEffect, useCallback } from 'react'

/**
 * sessionStorage に検索条件を保存・復元するフック。
 * 画面遷移後に戻ったときに検索条件を保持する。
 */
export function useSearchParamsStorage<T>(
  storageKey: string,
  defaultValues: T,
): [T, (values: T) => void, () => void] {
  const [values, setValues] = useState<T>(() => {
    if (typeof window === 'undefined') return defaultValues
    try {
      const saved = sessionStorage.getItem(storageKey)
      if (saved) return JSON.parse(saved) as T
    } catch {
      // ignore
    }
    return defaultValues
  })

  useEffect(() => {
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(values))
    } catch {
      // ignore
    }
  }, [storageKey, values])

  const reset = useCallback(() => {
    setValues(defaultValues)
    try {
      sessionStorage.removeItem(storageKey)
    } catch {
      // ignore
    }
  }, [storageKey, defaultValues])

  return [values, setValues, reset]
}
