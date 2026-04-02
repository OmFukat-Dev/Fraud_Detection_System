import { Button } from './ui/button'
import { useAuth } from '../hooks/useAuth'

export function Topbar() {
  const { username, signOut } = useAuth()

  return (
    <header className="flex items-center justify-between">
      <div className="text-sm text-black/60">Live risk monitoring console</div>
      <div className="flex items-center gap-3">
        <span className="text-sm font-semibold">{username}</span>
        <Button variant="ghost" onClick={signOut}>Sign out</Button>
      </div>
    </header>
  )
}
