import SplitFlapSlot from './SplitFlapSlot'

interface IndicatorAndMessageButtonProps {
  onClick: () => void
  disabled?: boolean
  loading?: boolean
  inactive?: boolean
  message?: string | null
  children: React.ReactNode
}

export default function IndicatorAndMessageButton({
  onClick,
  disabled = false,
  loading = false,
  inactive = false,
  message = null,
  children,
}: IndicatorAndMessageButtonProps) {
  const lampColor = inactive ? undefined : loading ? 'yellow' : disabled ? 'red' : 'green'
  // Map lamp color to split-flap text color; inactive → default green (dim slot)
  const flapColor: 'green' | 'red' | 'yellow' =
    lampColor === 'red' ? 'red' : lampColor === 'yellow' ? 'yellow' : 'green'

  return (
    <div className="cf-ind-msg-btn">
      <button
        className="cf-ind-msg-btn__btn"
        onClick={onClick}
        disabled={disabled}
        aria-disabled={disabled || loading}
        aria-busy={loading || undefined}
      >
        <span
          className="cf-ind-msg-btn__lamp"
          data-color={lampColor}
          aria-hidden="true"
        />
        {children}
      </button>
      <SplitFlapSlot message={message ?? null} color={flapColor} />
    </div>
  )
}
