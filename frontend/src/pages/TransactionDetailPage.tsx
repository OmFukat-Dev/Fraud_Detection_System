import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { Card } from '../components/ui/card'
import { Badge } from '../components/ui/badge'

type ExplanationReason = {
  ruleName: string
  triggered?: boolean
  scoreContribution?: number
  explanation?: string
}

type TransactionDetail = {
  transactionId: string
  fraudVerdict?: string
  fraudScore?: number
  triggeredRules?: string
  explanationReasons?: ExplanationReason[]
}

function Gauge({ value }: { value: number }) {
  const angle = Math.min(180, Math.max(0, value * 180))
  return (
    <div className="relative h-24 w-48">
      <div className="absolute bottom-0 h-24 w-48 rounded-t-full bg-black/10" />
      <div
        className="absolute bottom-0 h-24 w-48 rounded-t-full bg-ember/40"
        style={{
          clipPath: 'polygon(50% 100%, 0 100%, 0 0, 100% 0, 100% 100%)',
          transform: `rotate(${angle}deg)`,
          transformOrigin: '50% 100%'
        }}
      />
      <div className="absolute bottom-0 left-1/2 -translate-x-1/2 text-sm font-semibold">
        {Math.round(value * 100)}%
      </div>
    </div>
  )
}

export function TransactionDetailPage() {
  const { id } = useParams()
  const [tx, setTx] = useState<TransactionDetail | null>(null)

  useEffect(() => {
    if (id) {
      api.get(`/api/v1/transactions/${id}`).then((res) => setTx(res.data))
    }
  }, [id])

  if (!tx) return <Card>Loading...</Card>

  const reasons = tx.explanationReasons ?? []

  return (
    <Card>
      <div className="text-lg font-semibold">Transaction {tx.transactionId}</div>
      <div className="mt-4 grid gap-6 md:grid-cols-2">
        <div className="space-y-4">
          <div>
            <div className="text-sm text-black/60">Verdict</div>
            <div className="text-xl font-semibold">{tx.fraudVerdict}</div>
          </div>

          <div>
            <div className="text-sm text-black/60">Triggered Rules</div>
            <div className="text-sm">{tx.triggeredRules || 'NONE'}</div>
          </div>

          <div>
            <div className="text-sm text-black/60">Top Reasons</div>
            {reasons.length ? (
              <div className="mt-2 space-y-3">
                {reasons.slice(0, 3).map((reason) => (
                  <div key={`${reason.ruleName}-${reason.explanation}`} className="rounded-lg border border-black/5 bg-white/70 p-3">
                    <div className="flex items-center justify-between gap-3">
                      <Badge className="bg-electric/10 text-electric">{reason.ruleName}</Badge>
                      <span className="text-[11px] uppercase tracking-[0.2em] text-black/40">
                        {Math.round((reason.scoreContribution || 0) * 100)} score
                      </span>
                    </div>
                    <div className="mt-2 text-sm text-black/80">
                      {reason.explanation || 'No explanation available.'}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="mt-2 text-sm text-black/50">No explainability reasons were stored for this transaction.</div>
            )}
          </div>
        </div>

        <div>
          <div className="text-sm text-black/60">Fraud Score</div>
          <Gauge value={tx.fraudScore || 0} />
        </div>
      </div>
    </Card>
  )
}