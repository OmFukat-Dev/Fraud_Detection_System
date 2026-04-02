import React from 'react'
import { login } from '../lib/auth'

type AuthState = {
  token: string | null
  username: string | null
  loading: boolean
  error: string | null
}

type AuthContextValue = AuthState & {
  signIn: (username: string, password: string) => Promise<void>
  signOut: () => void
}

const AuthContext = React.createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = React.useState<string | null>(localStorage.getItem('fraud_token'))
  const [username, setUsername] = React.useState<string | null>(localStorage.getItem('fraud_user'))
  const [loading, setLoading] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)

  const signIn = async (u: string, p: string) => {
    setLoading(true)
    setError(null)
    try {
      const data = await login(u, p)
      localStorage.setItem('fraud_token', data.token)
      localStorage.setItem('fraud_user', data.username)
      setToken(data.token)
      setUsername(data.username)
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  const signOut = () => {
    localStorage.removeItem('fraud_token')
    localStorage.removeItem('fraud_user')
    setToken(null)
    setUsername(null)
  }

  return (
    <AuthContext.Provider value={{ token, username, loading, error, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = React.useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
