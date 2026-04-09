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

type MerchantCount = {
 merchantId: string
 flaggedCount: number
}

type ScoreBucket = {
 label: string
 lowerBound: number
 upperBound: number
 count: number
}

function SparkLine({ values }: { values: number[] }) {
 const max = Math.max(...values, 1)
 const points = values.map((v, i) => String(i * 30) + ',' + String(60 - (v / max) * 50)).join(' ')
 return (
 <svg width={'180'} height={'60'}>
 <polyline fill={'none'} stroke={'#3f8cff'} strokeWidth={'2'} points={points} />
 </svg>
 )
}

function ColumnBars({ items }: { items: { label: string; value: number }[] }) {
 const max = Math.max(...items.map((item) => item.value), 1)
 return (
 <div className={'flex items-end gap-3 h-28'}>
 {items.map((item) => (
 <div key={item.label} className={'flex flex-1 flex-col items-center gap-2'}>
 <div className={'flex h-20 w-full items-end rounded-xl bg-black/5 p-2'}>
 <div className={'w-full rounded-lg bg-ember/70'} style={{ height: String((item.value / max) * 100) + '%' }} />
 </div>
 <div className={'text-center text-[11px] text-black/60 leading-tight'}>{item.label}</div>
 <div className={'text-xs font-semibold'}>{item.value}</div>
 </div>
 ))}
 </div>
 )
}

function HorizontalBars({ items }: { items: { label: string; value: number }[] }) {
 const max = Math.max(...items.map((item) => item.value), 1)
 return (
 <div className={'space-y-3'}>
 {items.map((item) => (
 <div key={item.label}>
 <div className={'mb-1 flex items-center justify-between text-xs text-black/60'}>
 <span className={'truncate pr-3'}>{item.label}</span>
 <span>{item.value}</span>
 </div>
 <div className={'h-2 overflow-hidden rounded-full bg-black/10'}>
 <div className={'h-full rounded-full bg-electric'} style={{ width: String((item.value / max) * 100) + '%' }} />
 </div>
 </div>
 ))}
 </div>
 )
}

export function AnalyticsPage() {
 const [series, setSeries] = useState<Point[]>([])
 const [merchants, setMerchants] = useState<MerchantCount[]>([])
 const [distribution, setDistribution] = useState<ScoreBucket[]>([])

 useEffect(() => {
 let active = true
 async function load() {
 try {
 const [timeseriesRes, merchantsRes, distributionRes] = await Promise.all([
 api.get('/api/v1/fraud/timeseries?days=6'),
 api.get('/api/v1/fraud/analytics/top-merchants?limit=5'),
 api.get('/api/v1/fraud/analytics/score-distribution')
 ])
 if (!active) return
 setSeries(timeseriesRes.data)
 setMerchants(merchantsRes.data)
 setDistribution(distributionRes.data)
 } catch {
 if (!active) return
 setSeries([])
 setMerchants([])
 setDistribution([])
 }
 }
 load()
 return () => { active = false }
 }, [])

 const rateSeries = useMemo(() => series.map((p) => p.fraudRate), [series])
 const last = series[series.length - 1]
 const merchantItems = merchants.length ? merchants.map((m) => ({ label: m.merchantId, value: m.flaggedCount })) : [{ label: 'No flagged merchants yet', value: 0 }]
 const distributionItems = distribution.length ? distribution.map((bucket) => ({ label: bucket.label, value: bucket.count })) : [{ label: '0.0-0.2', value: 1 }, { label: '0.2-0.4', value: 2 }, { label: '0.4-0.6', value: 3 }, { label: '0.6-0.8', value: 2 }, { label: '0.8-1.0', value: 1 }]

 return (
 <div className={'grid gap-6 lg:grid-cols-2'}>
 <Card>
 <div className={'text-sm text-black/60'}>Fraud Rate (Last 6 Days)</div>
 <div className={'mt-2'}><SparkLine values={rateSeries.length ? rateSeries : [1, 2, 3, 2, 3, 4]} /></div>
 </Card>
 <Card>
 <div className={'text-sm text-black/60'}>Verdict Mix (Latest)</div>
 <div className={'mt-3'}>
 <ColumnBars items={[{ label: 'FRAUD', value: last ? last.fraud : 1 }, { label: 'REVIEW', value: last ? last.review : 1 }, { label: 'ALLOW', value: last ? last.allow : 1 }]} />
 </div>
 </Card>
 <Card>
 <div className={'text-sm text-black/60'}>Top Flagged Merchants</div>
 <div className={'mt-4'}>
 <HorizontalBars items={merchantItems} />
 </div>
 </Card>
 <Card>
 <div className={'text-sm text-black/60'}>Fraud Score Distribution</div>
 <div className={'mt-4'}>
 <ColumnBars items={distributionItems} />
 </div>
 </Card>
 </div>
 )
}
