import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { Card } from '../components/ui/card'

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
  const [tx, setTx] = useState<any>(null)

  useEffect(() => {
    if (id) {
      api.get(`/api/v1/transactions/${id}`).then((res) => setTx(res.data))
    }
  }, [id])

  if (!tx) return <Card>Loading...</Card>

  return (
    <Card>
      <div className="text-lg font-semibold">Transaction {tx.transactionId}</div>
      <div className="mt-4 grid gap-6 md:grid-cols-2">
        <div>
          <div className="text-sm text-black/60">Verdict</div>
          <div className="text-xl font-semibold">{tx.fraudVerdict}</div>
          <div className="mt-2 text-sm text-black/60">Triggered Rules</div>
          <div className="text-sm">{tx.triggeredRules || 'NONE'}</div>
        </div>
        <div>
          <div className="text-sm text-black/60">Fraud Score</div>
          <Gauge value={tx.fraudScore || 0} />
        </div>
      </div>
    </Card>
  )
}
