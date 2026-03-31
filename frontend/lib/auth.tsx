'use client'

import { createContext, useContext, useState, useCallback, useEffect, useRef, type ReactNode } from 'react'
import { api } from './api-client'

interface User {
  loginUserNo: number
  userName: string
  loginId: string
  companyNo: number
  companyType: string
  shopNo: number
}

interface AuthContextType {
  user: User | null
  token: string | null
  login: (loginId: string, password: string) => Promise<void>
  logout: () => void
  isAuthenticated: boolean
  isLoading: boolean
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

interface LoginResponse {
  token: string
  user: User
}

function isUser(value: unknown): value is User {
  if (typeof value !== 'object' || value === null) return false
  const obj = value as Record<string, unknown>
  return (
    typeof obj.loginUserNo === 'number' &&
    typeof obj.userName === 'string' &&
    typeof obj.loginId === 'string' &&
    typeof obj.companyNo === 'number' &&
    typeof obj.companyType === 'string' &&
    typeof obj.shopNo === 'number'
  )
}

function loadStoredUser(): User | null {
  try {
    const stored = localStorage.getItem('user')
    if (!stored) return null
    const parsed: unknown = JSON.parse(stored)
    return isUser(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const justLoggedIn = useRef(false)

  // クライアントサイドでlocalStorageから復元（SSR hydration mismatch回避）
  useEffect(() => {
    const storedToken = localStorage.getItem('token')
    const storedUser = loadStoredUser()
    if (storedToken) {
      setToken(storedToken)
      setUser(storedUser)
    } else {
      setIsLoading(false)
    }
  }, [])

  // トークン検証（login直後はスキップ）
  useEffect(() => {
    if (!token) return
    if (justLoggedIn.current) {
      justLoggedIn.current = false
      setIsLoading(false)
      return
    }
    api.get<User>('/auth/me')
      .then(userData => {
        setUser(userData)
        localStorage.setItem('user', JSON.stringify(userData))
      })
      .catch(() => {
        setToken(null)
        setUser(null)
        localStorage.removeItem('token')
        localStorage.removeItem('user')
      })
      .finally(() => setIsLoading(false))
  }, [token])

  const login = useCallback(async (loginId: string, password: string) => {
    const response = await api.post<LoginResponse>('/auth/login', { loginId, password })
    localStorage.setItem('token', response.token)
    localStorage.setItem('user', JSON.stringify(response.user))
    justLoggedIn.current = true
    setToken(response.token)
    setUser(response.user)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setToken(null)
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{
      user,
      token,
      login,
      logout,
      isAuthenticated: !!token && !!user,
      isLoading,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
