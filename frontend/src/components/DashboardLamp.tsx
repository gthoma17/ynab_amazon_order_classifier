interface DashboardLampProps {
  label: string
  isLit: boolean
  testId?: string
}

export default function DashboardLamp({ label, isLit, testId }: DashboardLampProps) {
  return (
    <div className="cf-dashboard-lamp" data-testid={testId}>
      <div
        className="cf-dashboard-lamp-bulb"
        data-lit={isLit ? 'true' : undefined}
        aria-label={`${label} indicator`}
      />
      <span className="cf-dashboard-lamp-label">{label}</span>
    </div>
  )
}
