import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'
import { ToastHost } from './ToastHost'
import { useSse } from '../hooks/useSse'
import { useToast } from '../hooks/useToast'

export function Layout({ children }: { children: React.ReactNode }) {
  const { addToast } = useToast()

  useSse((payload) => {
    const verdict = payload?.fraudVerdict || payload?.verdict
    if (!verdict) return
    const id = payload?.transactionId || 'unknown'
    const tone =
      verdict === 'FRAUD' ? 'danger' :
      verdict === 'REVIEW' ? 'info' : 'success'
    addToast({
      title: `Fraud Alert: ${verdict}`,
      message: `Transaction ${id}`,
      tone
    })
  })

  return (
    <div className="app-bg min-h-screen">
      <div className="mx-auto flex min-h-screen max-w-7xl">
        <Sidebar />
        <main className="flex-1 px-6 py-8">
          <Topbar />
          <div className="mt-8">{children}</div>
        </main>
      </div>
      <ToastHost />
    </div>
  )
}
