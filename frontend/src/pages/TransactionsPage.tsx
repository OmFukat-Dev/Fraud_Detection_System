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
type SortKey = 'transactionId' | 'merchantId' | 'amount' | 'fraudVerdict' | 'fraudScore' | 'createdAt'
type SortDirection = 'asc' | 'desc'
const verdictRank: Record<string, number> = { ALLOW: 0, REVIEW: 1, FRAUD: 2 }

export function TransactionsPage() {
 const [userId, setUserId] = useState('user-001')
 const [rows, setRows] = useState<Tx[]>([])
 const [query, setQuery] = useState('')
 const [verdict, setVerdict] = useState('ALL')
 const [page, setPage] = useState(1)
 const [sortKey, setSortKey] = useState<SortKey>('createdAt')
 const [sortDirection, setSortDirection] = useState<SortDirection>('desc')

 const load = async () => {
 const { data } = await api.get('/api/v1/transactions/user/' + userId)
 setRows(data)
 setPage(1)
 }

 const filtered = useMemo(() => {
 return rows.filter((r) => {
 const matchVerdict = verdict === 'ALL' || r.fraudVerdict === verdict
 const matchQuery = !query || r.transactionId?.includes(query) || r.merchantId?.includes(query)
 return matchVerdict && matchQuery
 })
 }, [rows, verdict, query])

 const sorted = useMemo(() => {
 const direction = sortDirection === 'asc' ? 1 : -1
 return [...filtered].sort((a, b) => {
 let compare = 0
 switch (sortKey) {
 case 'transactionId':
 compare = (a.transactionId || '').localeCompare(b.transactionId || '')
 break
 case 'merchantId':
 compare = (a.merchantId || '').localeCompare(b.merchantId || '')
 break
 case 'amount':
 compare = Number(a.amount || 0) - Number(b.amount || 0)
 break
 case 'fraudVerdict':
 compare = (verdictRank[a.fraudVerdict] ?? 99) - (verdictRank[b.fraudVerdict] ?? 99)
 break
 case 'fraudScore':
 compare = Number(a.fraudScore || 0) - Number(b.fraudScore || 0)
 break
 case 'createdAt':
 default:
 compare = new Date(a.createdAt || 0).getTime() - new Date(b.createdAt || 0).getTime()
 break
 }
 return compare * direction
 })
 }, [filtered, sortDirection, sortKey])

 const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE))
 const safePage = Math.min(page, totalPages)
 const pageRows = sorted.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

 const toggleSort = (key: SortKey) => {
 if (sortKey === key) {
 setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'))
 return
 }
 setSortKey(key)
 setSortDirection('asc')
 }

 const sortMarker = (key: SortKey) => {
 if (sortKey !== key) return '-'
 return sortDirection === 'asc' ? '^' : 'v'
 }

 return (
 <Card>
 <div className={'flex flex-col gap-4 md:flex-row md:items-center'}>
 <Input value={userId} onChange={(e) => setUserId(e.target.value)} placeholder={'User ID'} />
 <Button onClick={load}>Load</Button>
 <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={'Search txn or merchant'} />
 <select className={'rounded-lg border border-black/10 bg-white/80 px-3 py-2 text-sm'} value={verdict} onChange={(e) => setVerdict(e.target.value)}>
 <option value={'ALL'}>All</option>
 <option value={'FRAUD'}>FRAUD</option>
 <option value={'REVIEW'}>REVIEW</option>
 <option value={'ALLOW'}>ALLOW</option>
 </select>
 </div>

 <div className={'mt-6 overflow-auto'}>
 <table className={'w-full text-sm'}>
 <thead className={'text-left text-black/50'}>
 <tr>
 <th className={'py-2'}><button type={'button'} className={'inline-flex items-center gap-1'} onClick={() => toggleSort('transactionId')}>Transaction <span className={'text-[10px]'}>{sortMarker('transactionId')}</span></button></th>
 <th><button type={'button'} className={'inline-flex items-center gap-1'} onClick={() => toggleSort('merchantId')}>Merchant <span className={'text-[10px]'}>{sortMarker('merchantId')}</span></button></th>
 <th><button type={'button'} className={'inline-flex items-center gap-1'} onClick={() => toggleSort('amount')}>Amount <span className={'text-[10px]'}>{sortMarker('amount')}</span></button></th>
 <th><button type={'button'} className={'inline-flex items-center gap-1'} onClick={() => toggleSort('fraudVerdict')}>Verdict <span className={'text-[10px]'}>{sortMarker('fraudVerdict')}</span></button></th>
 <th><button type={'button'} className={'inline-flex items-center gap-1'} onClick={() => toggleSort('fraudScore')}>Score <span className={'text-[10px]'}>{sortMarker('fraudScore')}</span></button></th>
 <th><button type={'button'} className={'inline-flex items-center gap-1'} onClick={() => toggleSort('createdAt')}>Created <span className={'text-[10px]'}>{sortMarker('createdAt')}</span></button></th>
 </tr>
 </thead>
 <tbody>
 {pageRows.map((r) => (
 <tr key={r.transactionId} className={'border-t border-black/5'}>
 <td className={'py-2'}><Link className={'text-electric hover:underline'} to={'/transactions/' + r.transactionId}>{r.transactionId}</Link></td>
 <td>{r.merchantId}</td>
 <td>{r.amount} {r.currency}</td>
 <td><Badge className={r.fraudVerdict === 'FRAUD' ? 'bg-ember/15 text-ember' : 'bg-moss/15 text-moss'}>{r.fraudVerdict}</Badge></td>
 <td>{r.fraudScore.toFixed(2)}</td>
 <td>{new Date(r.createdAt).toLocaleString()}</td>
 </tr>
 ))}
 </tbody>
 </table>
 </div>

 <div className={'mt-4 flex items-center gap-3 text-sm'}>
 <Button variant={'ghost'} onClick={() => setPage(Math.max(1, safePage - 1))}>Prev</Button>
 <span>Page {safePage} / {totalPages}</span>
 <Button variant={'ghost'} onClick={() => setPage(Math.min(totalPages, safePage + 1))}>Next</Button>
 </div>
 </Card>
 )
}
