import { useMemo, useState } from 'react'
import { api } from '../lib/api'
import { Card } from '../components/ui/card'
import { Input } from '../components/ui/input'
import { Button } from '../components/ui/button'
import { Badge } from '../components/ui/badge'
import { Link } from 'react-router-dom'

type Tx = {
  transactionId: string
  userId: string
  merchantId: string
  amount: number
  currency: string
  status: string
  fraudVerdict: string
  fraudScore: number
  createdAt: string
}

const PAGE_SIZE = 8

export function TransactionsPage() {
  const [userId, setUserId] = useState('user-001')
  const [rows, setRows] = useState<Tx[]>([])
  const [query, setQuery] = useState('')
  const [verdict, setVerdict] = useState('ALL')
  const [page, setPage] = useState(1)

  const load = async () => {
    const { data } = await api.get(`/api/v1/transactions/user/${userId}`)
    setRows(data)
    setPage(1)
  }

  const filtered = useMemo(() => {
    return rows.filter((r) => {
      const matchVerdict = verdict === 'ALL' || r.fraudVerdict === verdict
      const matchQuery = !query ||
        r.transactionId?.includes(query) ||
        r.merchantId?.includes(query)
      return matchVerdict && matchQuery
    })
  }, [rows, verdict, query])

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const pageRows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  return (
    <Card>
      <div className="flex flex-col gap-4 md:flex-row md:items-center">
        <Input value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="User ID" />
        <Button onClick={load}>Load</Button>

        <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search txn or merchant" />

        <select
          className="rounded-lg border border-black/10 bg-white/80 px-3 py-2 text-sm"
          value={verdict}
          onChange={(e) => setVerdict(e.target.value)}
        >
          <option value="ALL">All</option>
          <option value="FRAUD">FRAUD</option>
          <option value="REVIEW">REVIEW</option>
          <option value="ALLOW">ALLOW</option>
        </select>
      </div>

      <div className="mt-6 overflow-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-black/50">
            <tr>
              <th className="py-2">Transaction</th>
              <th>Merchant</th>
              <th>Amount</th>
              <th>Verdict</th>
              <th>Score</th>
            </tr>
          </thead>
          <tbody>
            {pageRows.map((r) => (
              <tr key={r.transactionId} className="border-t border-black/5">
                <td className="py-2">
                  <Link className="text-electric hover:underline" to={`/transactions/${r.transactionId}`}>
                    {r.transactionId}
                  </Link>
                </td>
                <td>{r.merchantId}</td>
                <td>{r.amount} {r.currency}</td>
                <td>
                  <Badge className={r.fraudVerdict === 'FRAUD' ? 'bg-ember/15 text-ember' : 'bg-moss/15 text-moss'}>
                    {r.fraudVerdict}
                  </Badge>
                </td>
                <td>{r.fraudScore?.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-4 flex items-center gap-3 text-sm">
        <Button variant="ghost" onClick={() => setPage(Math.max(1, page - 1))}>Prev</Button>
        <span>Page {page} / {totalPages}</span>
        <Button variant="ghost" onClick={() => setPage(Math.min(totalPages, page + 1))}>Next</Button>
      </div>
    </Card>
  )
}
