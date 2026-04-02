import { cn } from '../../lib/utils'

type Props = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'ghost'
}

export function Button({ className, variant = 'primary', ...props }: Props) {
  return (
    <button
      className={cn(
        'inline-flex items-center justify-center rounded-lg px-4 py-2 text-sm font-semibold transition',
        variant === 'primary' && 'bg-ink text-white hover:bg-graphite',
        variant === 'ghost' && 'bg-transparent text-ink hover:bg-black/5',
        className
      )}
      {...props}
    />
  )
}
