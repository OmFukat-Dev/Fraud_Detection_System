import { api } from './api'

export type AuthResponse = {
  token: string
  tokenType: string
  expiresIn: number
  username: string
}

export async function login(username: string, password: string) {
  const { data } = await api.post<AuthResponse>('/api/v1/auth/login', {
    username,
    password
  })
  return data
}
