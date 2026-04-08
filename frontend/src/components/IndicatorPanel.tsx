import { useState, useEffect, useRef } from 'react'

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
  const displayText = isPlaceholder ? '-- STANDING BY --' : message

  const [visibleText, setVisibleText] = useState(displayText)
  const [incomingText, setIncomingText] = useState<string | null>(null)
  const [animating, setAnimating] = useState(false)
  const prevTextRef = useRef(displayText)

  useEffect(() => {
    if (displayText === prevTextRef.current) return
    prevTextRef.current = displayText

    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIncomingText(displayText)
    setAnimating(true)

    const timer = setTimeout(() => {
      setVisibleText(displayText)
      setIncomingText(null)
      setAnimating(false)
    }, 160)

    return () => clearTimeout(timer)
  }, [displayText])

  return (
    <div className="cf-indicator-panel">
      <div className="cf-lamp-housing" data-state={state} aria-label={`${label} status lamp`} />
      <div className="cf-lamp-body">
        <span
          className="cf-lamp-readout"
          data-placeholder={isPlaceholder ? 'true' : undefined}
          aria-label={readoutAriaLabel}
        >
          <span
            className={animating ? 'cf-rotary-segment cf-rotary-segment--out' : 'cf-rotary-segment'}
          >
            {visibleText}
          </span>
          {animating && incomingText !== null && (
            <span className="cf-rotary-segment cf-rotary-segment--in">{incomingText}</span>
          )}
        </span>
      </div>
    </div>
  )
}
