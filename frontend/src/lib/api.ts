import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export const api = axios.create({
  baseURL,
  timeout: 8000
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('fraud_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
