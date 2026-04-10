import SplitFlapSlot from './SplitFlapSlot'

export type LampState = 'idle' | 'testing' | 'success' | 'error'

interface IndicatorPanelProps {
  label: string
  state: LampState
  message: string
  readoutAriaLabel?: string
}

export default function IndicatorPanel({
  label,
  state,
  message,
  readoutAriaLabel,
}: IndicatorPanelProps) {
  const slotMessage =
    state === 'idle' ? null : state === 'testing' ? 'TESTING...' : message || null
  const slotColor = state === 'error' ? 'red' : state === 'testing' ? 'yellow' : 'green'

  return (
    <div className="cf-indicator-panel">
      <div className="cf-lamp-housing" data-state={state} aria-label={`${label} status lamp`} />
      <div aria-label={readoutAriaLabel} style={{ flex: 1, minWidth: 0 }}>
        <SplitFlapSlot message={slotMessage} color={slotColor} />
      </div>
    </div>
  )
}
