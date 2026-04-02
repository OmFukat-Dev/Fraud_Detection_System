import { cn } from '../../lib/utils'

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        'w-full rounded-lg border border-black/10 bg-white/80 px-3 py-2 text-sm outline-none focus:border-electric'
      )}
      {...props}
    />
  )
}
