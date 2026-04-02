import { NavLink } from 'react-router-dom'

const links = [
  { to: '/', label: 'Dashboard' },
  { to: '/transactions', label: 'Transactions' },
  { to: '/flagged', label: 'Flagged' },
  { to: '/analytics', label: 'Analytics' }
]

export function Sidebar() {
  return (
    <aside className="hidden md:flex h-full w-56 flex-col border-r border-black/5 bg-white/70 p-5">
      <div className="text-lg font-semibold">Fraud Sentinel</div>
      <div className="mt-6 flex flex-col gap-2 text-sm">
        {links.map((l) => (
          <NavLink
            key={l.to}
            to={l.to}
            className={({ isActive }) =>
              `rounded-lg px-3 py-2 ${isActive ? 'bg-ink text-white' : 'text-ink hover:bg-black/5'}`
            }
          >
            {l.label}
          </NavLink>
        ))}
      </div>
    </aside>
  )
}
