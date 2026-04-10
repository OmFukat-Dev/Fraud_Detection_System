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

type GraphRing = {
  ringId: string
  summary: string
  nodeCount: number
  userCount: number
  sharedEntityCount: number
  transactionCount: number
  flaggedTransactionCount: number
  averageFraudScore: number
  nodes: string[]
  transactionIds: string[]
}

type GraphPattern = {
  patternType: string
  label: string
  description: string
  userCount: number
  transactionCount: number
  flaggedTransactionCount: number
  users: string[]
  transactionIds: string[]
}

type GraphChain = {
  chainId: string
  patternType: string
  description: string
  path: string[]
  userCount: number
  transactionCount: number
  flaggedTransactionCount: number
  transactionIds: string[]
}

type GraphAnalysis = {
  totalTransactions: number
  totalNodes: number
  totalEdges: number
  totalComponents: number
  fraudRingsDetected: number
  sharedDevices: number
  sharedIps: number
  sharedMerchants: number
  sharedLocations: number
  rings: GraphRing[]
  patterns: GraphPattern[]
  chains: GraphChain[]
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

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div className={'rounded-xl border border-black/5 bg-white/60 p-3'}>
      <div className={'text-[11px] uppercase tracking-[0.18em] text-black/40'}>{label}</div>
      <div className={'mt-1 text-lg font-semibold'}>{value}</div>
    </div>
  )
}

export function AnalyticsPage() {
  const [series, setSeries] = useState<Point[]>([])
  const [merchants, setMerchants] = useState<MerchantCount[]>([])
  const [distribution, setDistribution] = useState<ScoreBucket[]>([])
  const [graph, setGraph] = useState<GraphAnalysis | null>(null)

  useEffect(() => {
    let active = true
    async function load() {
      try {
        const [timeseriesRes, merchantsRes, distributionRes, graphRes] = await Promise.all([
          api.get('/api/v1/fraud/timeseries?days=6'),
          api.get('/api/v1/fraud/analytics/top-merchants?limit=5'),
          api.get('/api/v1/fraud/analytics/score-distribution'),
          api.get('/api/v1/fraud/analytics/graph?limit=5')
        ])
        if (!active) return
        setSeries(timeseriesRes.data)
        setMerchants(merchantsRes.data)
        setDistribution(distributionRes.data)
        setGraph(graphRes.data)
      } catch {
        if (!active) return
        setSeries([])
        setMerchants([])
        setDistribution([])
        setGraph(null)
      }
    }
    load()
    return () => { active = false }
  }, [])

  const rateSeries = useMemo(() => series.map((p) => p.fraudRate), [series])
  const last = series[series.length - 1]
  const merchantItems = merchants.length ? merchants.map((m) => ({ label: m.merchantId, value: m.flaggedCount })) : [{ label: 'No flagged merchants yet', value: 0 }]
  const distributionItems = distribution.length ? distribution.map((bucket) => ({ label: bucket.label, value: bucket.count })) : [{ label: '0.0-0.2', value: 1 }, { label: '0.2-0.4', value: 2 }, { label: '0.4-0.6', value: 3 }, { label: '0.6-0.8', value: 2 }, { label: '0.8-1.0', value: 1 }]
  const graphStats = graph ?? {
    totalTransactions: 0,
    totalNodes: 0,
    totalEdges: 0,
    totalComponents: 0,
    fraudRingsDetected: 0,
    sharedDevices: 0,
    sharedIps: 0,
    sharedMerchants: 0,
    sharedLocations: 0,
    rings: [],
    patterns: [],
    chains: []
  }

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

      <Card className={'lg:col-span-2'}>
        <div className={'flex flex-col gap-4 md:flex-row md:items-start md:justify-between'}>
          <div>
            <div className={'text-sm text-black/60'}>Graph Analysis</div>
            <div className={'text-lg font-semibold'}>Fraud rings and repeated entity patterns</div>
            <div className={'mt-1 text-sm text-black/50'}>
              Builds a transaction graph across users, merchants, devices, IPs, and locations.
            </div>
          </div>
          <div className={'grid grid-cols-2 gap-3 md:grid-cols-4'}>
            <MiniStat label={'Nodes'} value={String(graphStats.totalNodes)} />
            <MiniStat label={'Edges'} value={String(graphStats.totalEdges)} />
            <MiniStat label={'Rings'} value={String(graphStats.fraudRingsDetected)} />
            <MiniStat label={'Components'} value={String(graphStats.totalComponents)} />
          </div>
        </div>

        <div className={'mt-4 grid gap-3 md:grid-cols-4'}>
          <MiniStat label={'Shared Devices'} value={String(graphStats.sharedDevices)} />
          <MiniStat label={'Shared IPs'} value={String(graphStats.sharedIps)} />
          <MiniStat label={'Shared Merchants'} value={String(graphStats.sharedMerchants)} />
          <MiniStat label={'Shared Locations'} value={String(graphStats.sharedLocations)} />
        </div>

        <div className={'mt-6 grid gap-4 xl:grid-cols-3'}>
          <div className={'rounded-2xl border border-black/5 bg-white/55 p-4'}>
            <div className={'text-sm font-semibold'}>Fraud Rings</div>
            <div className={'mt-3 space-y-3'}>
              {graphStats.rings.length ? graphStats.rings.map((ring) => (
                <div key={ring.ringId} className={'rounded-xl border border-black/5 bg-white/70 p-3'}>
                  <div className={'flex items-center justify-between gap-3'}>
                    <div className={'text-sm font-semibold'}>{ring.ringId}</div>
                    <div className={'text-xs text-black/40'}>{ring.flaggedTransactionCount} flagged</div>
                  </div>
                  <div className={'mt-1 text-sm text-black/70'}>{ring.summary}</div>
                  <div className={'mt-2 text-xs text-black/50'}>
                    {ring.userCount} users, {ring.sharedEntityCount} shared entities, avg score {ring.averageFraudScore.toFixed(2)}
                  </div>
                </div>
              )) : <div className={'text-sm text-black/50'}>No ring patterns detected yet.</div>}
            </div>
          </div>

          <div className={'rounded-2xl border border-black/5 bg-white/55 p-4'}>
            <div className={'text-sm font-semibold'}>Repeated Patterns</div>
            <div className={'mt-3 space-y-3'}>
              {graphStats.patterns.length ? graphStats.patterns.map((pattern) => (
                <div key={`${pattern.patternType}-${pattern.label}`} className={'rounded-xl border border-black/5 bg-white/70 p-3'}>
                  <div className={'flex items-center justify-between gap-3'}>
                    <div className={'text-sm font-semibold'}>{pattern.patternType}</div>
                    <div className={'text-xs text-black/40'}>{pattern.userCount} users</div>
                  </div>
                  <div className={'mt-1 text-sm text-black/70'}>{pattern.label}</div>
                  <div className={'mt-2 text-xs text-black/50'}>{pattern.description}</div>
                </div>
              )) : <div className={'text-sm text-black/50'}>No repeated patterns yet.</div>}
            </div>
          </div>

          <div className={'rounded-2xl border border-black/5 bg-white/55 p-4'}>
            <div className={'text-sm font-semibold'}>Suspicious Chains</div>
            <div className={'mt-3 space-y-3'}>
              {graphStats.chains.length ? graphStats.chains.map((chain) => (
                <div key={chain.chainId} className={'rounded-xl border border-black/5 bg-white/70 p-3'}>
                  <div className={'flex items-center justify-between gap-3'}>
                    <div className={'text-sm font-semibold'}>{chain.patternType}</div>
                    <div className={'text-xs text-black/40'}>{chain.flaggedTransactionCount} flagged</div>
                  </div>
                  <div className={'mt-1 text-sm text-black/70'}>{chain.path.join(' → ')}</div>
                  <div className={'mt-2 text-xs text-black/50'}>{chain.description}</div>
                </div>
              )) : <div className={'text-sm text-black/50'}>No suspicious chains yet.</div>}
            </div>
          </div>
        </div>
      </Card>
    </div>
  )
}