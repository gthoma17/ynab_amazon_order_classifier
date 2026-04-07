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
  const isPlaceholder = state === 'idle' && !message

  return (
    <div className="cf-indicator-panel">
      <div className="cf-lamp-housing" data-state={state} aria-label={`${label} status lamp`} />
      <div className="cf-lamp-body">
        <span className="cf-lamp-label">{label}</span>
        <span
          className="cf-lamp-readout"
          data-placeholder={isPlaceholder ? 'true' : undefined}
          aria-label={readoutAriaLabel}
        >
          {isPlaceholder ? '-- STANDING BY --' : message}
        </span>
      </div>
    </div>
  )
}
