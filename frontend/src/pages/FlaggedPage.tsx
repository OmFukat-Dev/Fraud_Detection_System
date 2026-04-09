import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import { Card } from '../components/ui/card'
import { Badge } from '../components/ui/badge'
import { Link } from 'react-router-dom'
import { useSse } from '../hooks/useSse'

type ExplanationReason = {
  ruleName: string
  explanation?: string
  scoreContribution?: number
}

type FlaggedRow = {
  transactionId: string
  merchantId: string
  fraudVerdict: string
  explanationReasons?: ExplanationReason[]
}

export function FlaggedPage() {
  const [rows, setRows] = useState<FlaggedRow[]>([])

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
        {rows.map((r) => {
          const topReason = r.explanationReasons?.[0]
          return (
            <div key={r.transactionId} className="flex items-center justify-between gap-4 rounded-lg border border-black/5 p-3">
              <div>
                <Link className="text-sm text-electric hover:underline" to={`/transactions/${r.transactionId}`}>
                  {r.transactionId}
                </Link>
                <div className="text-xs text-black/50">{r.merchantId}</div>
                <div className="mt-1 text-xs text-black/60">
                  {topReason
                    ? `${topReason.ruleName}${topReason.explanation ? `: ${topReason.explanation}` : ''}`
                    : 'No explainability reasons stored yet.'}
                </div>
              </div>
              <Badge className="bg-ember/15 text-ember">{r.fraudVerdict}</Badge>
            </div>
          )
        })}
      </div>
    </Card>
  )
}