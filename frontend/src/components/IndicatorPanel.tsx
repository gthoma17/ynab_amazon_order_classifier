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
  const slotMessage = state === 'idle' ? null : message || null
  const slotColor = state === 'error' ? 'red' : 'green'

  return (
    <div className="cf-indicator-panel">
      <div className="cf-lamp-housing" data-state={state} aria-label={`${label} status lamp`} />
      <div aria-label={readoutAriaLabel}>
        <SplitFlapSlot message={slotMessage} color={slotColor} />
      </div>
    </div>
  )
}
