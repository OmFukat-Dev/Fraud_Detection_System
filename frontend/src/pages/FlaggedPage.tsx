import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import { Card } from '../components/ui/card'
import { Badge } from '../components/ui/badge'
import { Link } from 'react-router-dom'
import { useSse } from '../hooks/useSse'

export function FlaggedPage() {
  const [rows, setRows] = useState<any[]>([])

  const load = () => {
    api.get('/api/v1/transactions/flagged').then((res) => setRows(res.data))
  }

  useEffect(() => {
    load()
  }, [])

  useSse((payload) => {
    if (payload?.fraudVerdict === 'FRAUD') {
      load()
    }
  })

  return (
    <Card>
      <div className="text-lg font-semibold">Flagged Transactions</div>
      <div className="mt-4 space-y-3">
        {rows.map((r) => (
          <div key={r.transactionId} className="flex items-center justify-between rounded-lg border border-black/5 p-3">
            <div>
              <Link className="text-sm text-electric hover:underline" to={`/transactions/${r.transactionId}`}>
                {r.transactionId}
              </Link>
              <div className="text-xs text-black/50">{r.merchantId}</div>
            </div>
            <Badge className="bg-ember/15 text-ember">{r.fraudVerdict}</Badge>
          </div>
        ))}
      </div>
    </Card>
  )
}
