import { useState } from 'react'
import { useAuth } from '../hooks/useAuth'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'

export function LoginPage() {
  const { signIn, loading, error } = useAuth()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    await signIn(username, password)
  }

  return (
    <div className="app-bg min-h-screen flex items-center justify-center px-6">
      <div className="glass w-full max-w-md rounded-3xl p-8 shadow-glow">
        <h1 className="text-2xl font-semibold">Fraud Sentinel</h1>
        <p className="text-sm text-black/60 mt-2">Sign in to monitor fraud activity in real time.</p>
        <form onSubmit={submit} className="mt-6 flex flex-col gap-4">
          <Input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Username" />
          <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" />
          {error && <div className="text-sm text-ember">{error}</div>}
          <Button disabled={loading}>{loading ? 'Signing in...' : 'Sign in'}</Button>
        </form>
      </div>
    </div>
  )
}
