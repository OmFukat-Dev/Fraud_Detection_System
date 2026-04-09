import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import { StatCard } from '../components/StatCard'
import { Card } from '../components/ui/card'

type FraudStats = {
 totalTransactions: number
 totalFraud: number
 totalReview: number
 totalAllow: number
 totalPending: number
 flaggedToday: number
 averageFraudScore: number
 fraudRate: number
}

export function DashboardPage() {
 const [stats, setStats] = useState<FraudStats | null>(null)

 useEffect(() => {
 const load = () => api.get('/api/v1/fraud/stats').then((res) => setStats(res.data))
 load()
 const id = setInterval(load, 10000)
 return () => clearInterval(id)
 }, [])

 return (
 <div className={'grid gap-6'}>
 <div className={'grid gap-4 md:grid-cols-4'}>
 <StatCard label={'Total Transactions'} value={stats ? String(stats.totalTransactions) : '—'} />
 <StatCard label={'Flagged Today'} value={stats ? String(stats.flaggedToday) : '—'} accent={'text-ember'} />
 <StatCard label={'Fraud Rate'} value={stats ? stats.fraudRate.toFixed(1) + '%' : '—'} accent={'text-ember'} />
 <StatCard label={'Avg Score'} value={stats ? stats.averageFraudScore.toFixed(2) : '—'} accent={'text-electric'} />
 </div>
 <Card>
 <div className={'text-sm text-black/60'}>System Status</div>
 <div className={'mt-2 text-lg font-semibold'}>All services nominal</div>
 </Card>
 </div>
 )
}
