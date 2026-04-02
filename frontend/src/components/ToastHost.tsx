import { useToast } from '../hooks/useToast'

export function ToastHost() {
  const { toasts, removeToast } = useToast()

  return (
    <div className="fixed right-6 top-6 z-50 flex flex-col gap-3">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`glass w-72 rounded-xl p-4 shadow-glow border ${
            t.tone === 'danger' ? 'border-ember/40' :
            t.tone === 'success' ? 'border-moss/40' : 'border-electric/40'
          }`}
        >
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-sm font-semibold">{t.title}</div>
              {t.message && <div className="text-xs text-black/60 mt-1">{t.message}</div>}
            </div>
            <button
              className="text-xs text-black/50 hover:text-black"
              onClick={() => removeToast(t.id)}
            >
              ✕
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
