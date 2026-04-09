interface IndicatorButtonProps {
  onClick: () => void
  disabled?: boolean
  children: React.ReactNode
}

export default function IndicatorButton({
  onClick,
  disabled = false,
  children,
}: IndicatorButtonProps) {
  return (
    <div className="cf-indicator-btn">
      <span
        className="cf-indicator-btn__lamp"
        data-active={!disabled ? 'true' : undefined}
        aria-hidden="true"
      />
      <button
        className="cf-btn-secondary"
        onClick={onClick}
        disabled={disabled}
        aria-disabled={disabled}
      >
        {children}
      </button>
    </div>
  )
}
