import { useEffect, useMemo, useState } from 'react'
import { api } from '../lib/api'
import { Card } from '../components/ui/card'

type Point = {
  date: string
  total: number
  fraud: number
  review: number
  allow: number
  fraudRate: number
}

function SparkLine({ values }: { values: number[] }) {
  const max = Math.max(...values, 1)
  const points = values.map((v, i) => `${i * 30},${60 - (v / max) * 50}`).join(' ')
  return (
    <svg width="180" height="60">
      <polyline fill="none" stroke="#3f8cff" strokeWidth="2" points={points} />
    </svg>
  )
}

function Bars({ values }: { values: number[] }) {
  const max = Math.max(...values, 1)
  return (
    <div className="flex items-end gap-2 h-20">
      {values.map((v, i) => (
        <div key={i} className="w-6 bg-moss/70 rounded" style={{ height: `${(v / max) * 100}%` }} />
      ))}
    </div>
  )
}

export function AnalyticsPage() {
  const [series, setSeries] = useState<Point[]>([])

  useEffect(() => {
    api.get('/api/v1/fraud/timeseries?days=6').then((res) => setSeries(res.data))
  }, [])

  const rateSeries = useMemo(() => series.map((p) => p.fraudRate), [series])
  const last = series[series.length - 1]

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <Card>
        <div className="text-sm text-black/60">Fraud Rate (Last 6 Days)</div>
        <div className="mt-2"><SparkLine values={rateSeries.length ? rateSeries : [1,2,3,2,3,4]} /></div>
      </Card>
      <Card>
        <div className="text-sm text-black/60">Verdict Mix (Latest)</div>
        <div className="mt-3">
          <Bars values={[
            last?.fraud ?? 1,
            last?.review ?? 1,
            last?.allow ?? 1
          ]} />
        </div>
      </Card>
    </div>
  )
}
