interface IndicatorButtonProps {
  onClick: () => void
  disabled?: boolean
  loading?: boolean
  children: React.ReactNode
}

export default function IndicatorButton({
  onClick,
  disabled = false,
  loading = false,
  children,
}: IndicatorButtonProps) {
  const isActive = !disabled && !loading
  return (
    <div className={`cf-indicator-btn${loading ? ' cf-indicator-btn--loading' : ''}`}>
      <span
        className="cf-indicator-btn__lamp"
        data-active={isActive ? 'true' : undefined}
        aria-hidden="true"
      />
      <button
        className="cf-btn-secondary"
        onClick={onClick}
        disabled={disabled}
        aria-disabled={disabled || loading}
        aria-busy={loading || undefined}
      >
        {children}
      </button>
    </div>
  )
}
