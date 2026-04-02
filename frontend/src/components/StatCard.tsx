import { Card } from './ui/card'

export function StatCard({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <Card className="flex flex-col gap-2">
      <div className="text-xs uppercase text-black/50">{label}</div>
      <div className={`text-2xl font-semibold ${accent || ''}`}>{value}</div>
    </Card>
  )
}
